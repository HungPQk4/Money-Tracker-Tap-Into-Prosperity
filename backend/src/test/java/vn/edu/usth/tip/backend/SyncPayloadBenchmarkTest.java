package vn.edu.usth.tip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quantitative evaluation harness for the thesis Results section.
 *
 *  Workstream A — Delta-sync payload efficiency  (full pull vs incremental k∈{1,5,50}).
 *  Workstream B — Backend latency per endpoint   (p50 / p95 / mean over M reps).
 *
 * Runs the whole backend on H2 in-memory (profile "test"), so the numbers measure
 * *server compute* over loopback — a clean, reproducible baseline. To obtain the
 * "Neon (end-to-end)" column, point BASE_OVERRIDE at a running Neon-backed server
 * (set env BENCH_BASE_URL=https://host/api) and run the same assertions; WAN latency
 * is environment-specific and must be measured from a representative client machine.
 *
 * Results are printed between the BENCH_BEGIN / BENCH_END markers on stdout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.api.key=test"})
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class SyncPayloadBenchmarkTest {

    // ── Tunable parameters (documented in the thesis "Evaluation setup") ──────────
    private static final int N_SEED   = 1000;          // total transactions seeded
    private static final int BATCH    = 500;           // server sync batch cap (@Size max=500)
    private static final int[] K_VALS = {1, 5, 50};    // incremental change sizes
    private static final int M_REPS   = 100;           // latency reps per endpoint
    private static final int WARMUP   = 10;            // discarded warm-up reps
    private static final int PULL_LIMIT = 5000;        // single-page full pull (> N_SEED)

    @Autowired private Environment env;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        String override = System.getenv("BENCH_BASE_URL");
        if (override != null && !override.isBlank()) return override;
        return "http://localhost:" + env.getProperty("local.server.port") + "/api";
    }

    @Test
    void runQuantitativeBenchmarks() throws Exception {
        // ── Setup: account + category + seed N transactions ──────────────────────
        String email = "bench-" + UUID.randomUUID() + "@test.com";
        Resp reg = call("POST", "/auth/register", null,
                Map.of("email", email, "password", "123456", "fullName", "Bench", "deviceName", "Bench-Dev"));
        assertThat(reg.status()).isEqualTo(201);
        Map regBody = asMap(reg.body());
        String token  = (String) regBody.get("token");
        String userId = (String) regBody.get("userId");

        Resp acc = call("POST", "/accounts", token,
                Map.of("name", "Bench wallet", "type", "cash", "balance", 0));
        assertThat(acc.status()).isEqualTo(201);
        String accountId = (String) asMap(acc.body()).get("id");

        Resp cat = call("POST", "/categories", token,
                Map.of("name", "Bench", "type", "expense", "userId", userId));
        assertThat(cat.status()).isEqualTo(201);
        String catId = (String) asMap(cat.body()).get("id");

        List<UUID> ids = new ArrayList<>(N_SEED);
        for (int i = 0; i < N_SEED; i++) ids.add(UUID.randomUUID());
        for (int start = 0; start < N_SEED; start += BATCH) {
            int end = Math.min(start + BATCH, N_SEED);
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = start; i < end; i++) {
                items.add(txItem(ids.get(i), accountId, catId, 10000 + i, OffsetDateTime.now()));
            }
            Resp sync = call("POST", "/transactions/sync", token,
                    Map.of("userId", userId, "transactions", items));
            assertThat(sync.status()).isEqualTo(207);
            assertThat((Integer) asMap(sync.body()).get("savedCount")).isEqualTo(end - start);
        }

        StringBuilder out = new StringBuilder("\n==== BENCH_BEGIN ====\n");
        out.append("env.base=").append(base()).append("  N=").append(N_SEED)
           .append("  M=").append(M_REPS).append("  warmup=").append(WARMUP).append('\n');

        // ── Workstream A: payload efficiency ─────────────────────────────────────
        Resp full = call("GET", "/transactions/delta?updatedSince=" + epoch()
                + "&limit=" + PULL_LIMIT, token, null);
        assertThat(full.status()).isEqualTo(200);
        Map fullBody = asMap(full.body());
        int  fullRows  = ((List) fullBody.get("items")).size();
        long fullBytes = full.bytes();
        assertThat((Boolean) fullBody.get("hasMore")).as("full pull fits one page").isFalse();
        assertThat(fullRows).isEqualTo(N_SEED);

        out.append("\n-- A. Delta-sync payload efficiency --\n");
        out.append(row("Scenario", "Rows", "Payload(KB)", "%full"));
        out.append(row("Full pull (N=" + N_SEED + ")", str(fullRows), kb(fullBytes), "100.0"));

        for (int k : K_VALS) {
            // fresh cursor = server "now" just before the edits
            Resp probe = call("GET", "/transactions/delta?updatedSince=" + nowIso()
                    + "&limit=1", token, null);
            String cursor = (String) asMap(probe.body()).get("syncTimestamp");

            // edit k distinct rows → new updatedAt (PATH A, client wins)
            List<Map<String, Object>> edits = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                edits.add(txItem(ids.get(i), accountId, catId, 99000 + i, OffsetDateTime.now()));
            }
            Resp es = call("POST", "/transactions/sync", token,
                    Map.of("userId", userId, "transactions", edits));
            assertThat(es.status()).isEqualTo(207);

            Resp delta = call("GET", "/transactions/delta?updatedSince=" + enc(cursor)
                    + "&limit=" + PULL_LIMIT, token, null);
            assertThat(delta.status()).isEqualTo(200);
            int  dRows  = ((List) asMap(delta.body()).get("items")).size();
            long dBytes = delta.bytes();
            assertThat(dRows).as("incremental returns exactly k changed rows").isEqualTo(k);
            out.append(row("Incremental k=" + k, str(dRows), kb(dBytes),
                    String.format("%.2f", 100.0 * dBytes / fullBytes)));
        }

        // ── Workstream B: latency per endpoint ───────────────────────────────────
        out.append("\n-- B. Backend response latency (ms) --\n");
        out.append(row4("Endpoint", "n", "p50", "p95", "mean"));

        Map<String, Object> loginBody = Map.of("email", email, "password", "123456", "deviceName", "Bench-Dev");
        String recentCursor = (String) asMap(
                call("GET", "/transactions/delta?updatedSince=" + nowIso() + "&limit=1", token, null).body()
        ).get("syncTimestamp");
        Map<String, Object> oneEdit = Map.of("userId", userId, "transactions",
                List.of(txItem(ids.get(0), accountId, catId, 12345, OffsetDateTime.now())));

        out.append(latency("POST /auth/login",           () -> call("POST", "/auth/login", null, loginBody)));
        out.append(latency("GET  /transactions/delta(full)", () ->
                call("GET", "/transactions/delta?updatedSince=" + epoch() + "&limit=" + PULL_LIMIT, token, null)));
        out.append(latency("GET  /transactions/delta(incr)", () ->
                call("GET", "/transactions/delta?updatedSince=" + enc(recentCursor) + "&limit=" + PULL_LIMIT, token, null)));
        out.append(latency("POST /transactions/sync(1)",  () -> call("POST", "/transactions/sync", token, oneEdit)));
        out.append(latency("GET  /accounts",              () -> call("GET", "/accounts", token, null)));
        out.append(latency("GET  /dashboard/summary",     () -> call("GET", "/dashboard/summary", token, null)));

        out.append("==== BENCH_END ====\n");
        System.out.println(out);
    }

    // ── Latency measurement ───────────────────────────────────────────────────────
    private interface CallFn { Resp run() throws Exception; }

    private String latency(String label, CallFn fn) throws Exception {
        long[] ns = new long[M_REPS];
        for (int i = 0; i < M_REPS; i++) {
            long t0 = System.nanoTime();
            Resp r = fn.run();
            ns[i] = System.nanoTime() - t0;
            assertThat(r.status()).as(label + " status").isBetween(200, 299);
        }
        long[] kept = Arrays.copyOfRange(ns, WARMUP, M_REPS);
        Arrays.sort(kept);
        double p50  = ms(kept[(int) (kept.length * 0.50)]);
        double p95  = ms(kept[(int) (kept.length * 0.95)]);
        double mean = ms((long) Arrays.stream(kept).average().orElse(0));
        return row4(label, str(kept.length),
                String.format("%.1f", p50), String.format("%.1f", p95), String.format("%.1f", mean));
    }

    // ── HTTP + JSON helpers (mirrors MultiDeviceSessionFlowTest) ───────────────────
    private record Resp(int status, String body, long bytes) {}

    private Resp call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(base() + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) {
            b.header("Content-Type", "application/json");
            b.method(method, BodyPublishers.ofString(om.writeValueAsString(body)));
        } else {
            b.method(method, BodyPublishers.noBody());
        }
        HttpResponse<String> r = http.send(b.build(), BodyHandlers.ofString());
        long bytes = r.body() == null ? 0 : r.body().getBytes(StandardCharsets.UTF_8).length;
        return new Resp(r.statusCode(), r.body(), bytes);
    }

    private Map<String, Object> txItem(UUID id, String accountId, String catId, long amount, OffsetDateTime ts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transactionId", id.toString());
        m.put("accountId", accountId);
        m.put("categoryId", catId);
        m.put("amount", amount);
        m.put("type", "expense");
        m.put("transactionDate", LocalDate.now().toString());
        m.put("clientUpdatedAt", ts.toString());
        return m;
    }

    private Map asMap(String json) throws Exception {
        return (json == null || json.isEmpty()) ? Map.of() : om.readValue(json, Map.class);
    }

    private static String epoch()  { return enc("1970-01-01T00:00:00Z"); }
    private static String nowIso() { return enc(OffsetDateTime.now().toString()); }
    private static String enc(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static double ms(long nanos) { return nanos / 1_000_000.0; }
    private static String kb(long bytes) { return String.format("%.1f", bytes / 1024.0); }
    private static String str(int v) { return Integer.toString(v); }
    private static String row(String a, String b, String c, String d) {
        return String.format("%-26s %8s %12s %8s%n", a, b, c, d);
    }
    private static String row4(String a, String b, String c, String d, String e) {
        return String.format("%-30s %5s %8s %8s %8s%n", a, b, c, d, e);
    }
}
