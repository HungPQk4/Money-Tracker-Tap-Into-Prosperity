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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test cho Mục 1 (quản lý phiên/thiết bị) + reconciliation số dư.
 * Boot toàn bộ app trên H2 in-memory (profile "test") — KHÔNG đụng Neon production.
 * Dùng JDK HttpClient (Spring Boot 4 đã bỏ/đổi gói TestRestTemplate & MockMvc autoconfigure).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.api.key=test"})
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class MultiDeviceSessionFlowTest {

    @Autowired private Environment env;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + env.getProperty("local.server.port") + "/api";
    }

    @Test
    void multiDeviceSessionAndReconcileFlow() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@test.com";

        // 1. Đăng ký "Thiết bị A" (tự tạo phiên A) → 201, có access + refresh token
        Resp reg = call("POST", "/auth/register", null,
                Map.of("email", email, "password", "123456", "fullName", "IT User", "deviceName", "Device-A"));
        assertThat(reg.status()).isEqualTo(201);
        Map regBody = asMap(reg.body());
        String tokenA = (String) regBody.get("token");
        String refreshA = (String) regBody.get("refreshToken");
        assertThat(tokenA).as("access token A").isNotNull();
        assertThat(refreshA).as("refresh token A").isNotNull();

        // 2. Đăng nhập "Thiết bị B" (cùng tài khoản, song song) → 200
        Resp login = call("POST", "/auth/login", null,
                Map.of("email", email, "password", "123456", "deviceName", "Device-B"));
        assertThat(login.status()).isEqualTo(200);
        String tokenB = (String) asMap(login.body()).get("token");
        assertThat(tokenB).isNotNull();

        // 3. A liệt kê phiên → đúng 2, đúng 1 phiên "current"
        Resp sess = call("GET", "/auth/sessions", tokenA, null);
        assertThat(sess.status()).isEqualTo(200);
        List<Map> list = asList(sess.body());
        assertThat(list).as("2 thiết bị đăng nhập song song").hasSize(2);
        long currentCount = list.stream().filter(s -> Boolean.TRUE.equals(s.get("current"))).count();
        assertThat(currentCount).as("đúng 1 phiên current").isEqualTo(1);
        String sessionIdB = (String) list.stream()
                .filter(s -> !Boolean.TRUE.equals(s.get("current")))
                .findFirst().orElseThrow().get("id");

        // 4. Xoay access token A bằng refresh token → 200, token mới
        Resp refreshed = call("POST", "/auth/refresh", null, Map.of("refreshToken", refreshA));
        assertThat(refreshed.status()).isEqualTo(200);
        String tokenA2 = (String) asMap(refreshed.body()).get("token");
        assertThat(tokenA2).isNotNull();

        // 5. Dùng LẠI refresh token A CŨ → 401 (đã xoay, không tái sử dụng)
        Resp reused = call("POST", "/auth/refresh", null, Map.of("refreshToken", refreshA));
        assertThat(reused.status()).as("refresh token cũ phải bị từ chối").isEqualTo(401);

        // 6. A thu hồi phiên của B → 204
        Resp revoke = call("DELETE", "/auth/sessions/" + sessionIdB, tokenA2, null);
        assertThat(revoke.status()).isEqualTo(204);

        // 7. Token B dùng NGAY sau khi bị thu hồi → 401 (thu hồi TỨC THÌ)
        Resp bAfter = call("GET", "/auth/sessions", tokenB, null);
        assertThat(bAfter.status()).as("token B phải chết ngay sau khi thu hồi").isEqualTo(401);

        // 8. Reconcile: tạo ví số dư "lệch" 500k, không có giao dịch → reconcile phải về 0
        Resp acc = call("POST", "/accounts", tokenA2,
                Map.of("name", "Vi test", "type", "cash", "balance", 500000));
        assertThat(acc.status()).isEqualTo(201);
        String accountId = (String) asMap(acc.body()).get("id");

        Resp reconciled = call("POST", "/accounts/" + accountId + "/reconcile", tokenA2, null);
        assertThat(reconciled.status()).isEqualTo(200);
        Number balance = (Number) asMap(reconciled.body()).get("balance");
        assertThat(balance.doubleValue()).as("số dư lệch phải được tính lại = 0 (không có giao dịch)").isEqualTo(0.0);

        // ── Mục 3 phần sâu: số dư = Σ giao dịch (Part B) + optimistic concurrency (Part A) ──
        String userId = (String) regBody.get("userId");

        Resp cat = call("POST", "/categories", tokenA2,
                Map.of("name", "Food", "type", "expense", "userId", userId));
        assertThat(cat.status()).as("tạo category lỗi: " + cat.body()).isEqualTo(201);
        String catId = (String) asMap(cat.body()).get("id");

        // Thu 100k → số dư ví phải = 100k (tính lại từ giao dịch, KHÔNG cộng dồn LWW)
        Resp tIncome = call("POST", "/transactions", tokenA2, Map.of(
                "userId", userId, "accountId", accountId, "categoryId", catId,
                "amount", 100000, "type", "income", "transactionDate", "2026-06-10"));
        assertThat(tIncome.status()).as("tạo income lỗi: " + tIncome.body()).isEqualTo(201);
        String incomeId = (String) asMap(tIncome.body()).get("id");
        assertThat(accountBalance(tokenA2, accountId)).as("income 100k → số dư 100k").isEqualTo(100000.0);

        // Chi 30k → số dư 70k
        Resp tExpense = call("POST", "/transactions", tokenA2, Map.of(
                "userId", userId, "accountId", accountId, "categoryId", catId,
                "amount", 30000, "type", "expense", "transactionDate", "2026-06-10"));
        assertThat(tExpense.status()).isEqualTo(201);
        String expenseId = (String) asMap(tExpense.body()).get("id");
        assertThat(accountBalance(tokenA2, accountId)).as("expense 30k → số dư 70k").isEqualTo(70000.0);

        // Xóa giao dịch chi → số dư về 100k (giao dịch đã xóa bị loại khỏi tổng)
        Resp delTx = call("DELETE", "/transactions/" + expenseId, tokenA2, null);
        assertThat(delTx.status()).isEqualTo(204);
        assertThat(accountBalance(tokenA2, accountId)).as("xóa expense → số dư 100k").isEqualTo(100000.0);

        // Part A: sửa income với expectedUpdatedAt CŨ → 409 (chống lost-update đa thiết bị)
        Resp conflict = call("PUT", "/transactions/" + incomeId, tokenA2, Map.of(
                "userId", userId, "accountId", accountId, "categoryId", catId,
                "amount", 120000, "type", "income", "transactionDate", "2026-06-10",
                "expectedUpdatedAt", "2000-01-01T00:00:00Z"));
        assertThat(conflict.status()).as("sửa với expectedUpdatedAt cũ → 409").isEqualTo(409);
        assertThat(accountBalance(tokenA2, accountId)).as("409 không làm đổi số dư").isEqualTo(100000.0);

        // Sửa KHÔNG kèm expectedUpdatedAt → 200, số dư cập nhật theo income mới (120k)
        Resp okUpdate = call("PUT", "/transactions/" + incomeId, tokenA2, Map.of(
                "userId", userId, "accountId", accountId, "categoryId", catId,
                "amount", 120000, "type", "income", "transactionDate", "2026-06-10"));
        assertThat(okUpdate.status()).as("sửa không kèm expectedUpdatedAt → 200").isEqualTo(200);
        assertThat(accountBalance(tokenA2, accountId)).as("income sửa 120k → số dư 120k").isEqualTo(120000.0);

        // 9. Đăng xuất A → 204, rồi token A chết
        Resp logout = call("POST", "/auth/logout", tokenA2, null);
        assertThat(logout.status()).isEqualTo(204);
        Resp aAfter = call("GET", "/auth/sessions", tokenA2, null);
        assertThat(aAfter.status()).as("token A phải chết sau logout").isEqualTo(401);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private record Resp(int status, String body) {}

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
        return new Resp(r.statusCode(), r.body());
    }

    private Map asMap(String json) throws Exception {
        return (json == null || json.isEmpty()) ? Map.of() : om.readValue(json, Map.class);
    }

    private List<Map> asList(String json) throws Exception {
        return om.readValue(json, List.class);
    }

    private double accountBalance(String token, String accountId) throws Exception {
        Resp r = call("GET", "/accounts", token, null);
        Map acc = asList(r.body()).stream()
                .filter(a -> accountId.equals(a.get("id")))
                .findFirst().orElseThrow();
        return ((Number) acc.get("balance")).doubleValue();
    }
}
