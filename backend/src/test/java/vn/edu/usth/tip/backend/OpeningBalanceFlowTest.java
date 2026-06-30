package vn.edu.usth.tip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Số dư ĐẦU KỲ của ví phải được giữ: balance = openingBalance + Σ giao dịch.
 * Tái hiện đúng lỗi cũ (ví mở 5tr + chi 100k từng bị tính ra −100k) và kiểm tra
 * ngữ nghĩa sửa số dư (đặt lại tổng → số dư đầu kỳ tự suy ngược, giữ nguyên giao dịch).
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.api.key=test"})
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class OpeningBalanceFlowTest {

    @Autowired private Environment env;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + env.getProperty("local.server.port") + "/api";
    }

    @Test
    void openingBalance_isPreserved_acrossTransactionsAndEdits() throws Exception {
        String email = "ob-" + UUID.randomUUID() + "@test.com";
        Map reg = asMap(call("POST", "/auth/register", null,
                Map.of("email", email, "password", "123456", "fullName", "OB", "deviceName", "D")).body());
        String token  = (String) reg.get("token");
        String userId = (String) reg.get("userId");

        // 1. Lập ví "Tiền mặt" với số dư đầu kỳ 5.000.000
        Map acc = asMap(call("POST", "/accounts", token,
                Map.of("name", "Tien mat", "type", "cash", "balance", 5_000_000)).body());
        String accId = (String) acc.get("id");
        assertThat(((Number) acc.get("balance")).doubleValue()).as("số dư = đầu kỳ 5tr").isEqualTo(5_000_000.0);
        assertThat(((Number) acc.get("openingBalance")).doubleValue()).isEqualTo(5_000_000.0);

        String catId = (String) asMap(call("POST", "/categories", token,
                Map.of("name", "An uong", "type", "expense", "userId", userId)).body()).get("id");

        // 2. Chi 100k → số dư phải là 4.900.000 (TRƯỚC khi sửa lỗi: bị tính ra −100.000)
        assertThat(call("POST", "/transactions", token, Map.of(
                "userId", userId, "accountId", accId, "categoryId", catId,
                "amount", 100_000, "type", "expense", "transactionDate", "2026-06-10")).status()).isEqualTo(201);
        assertThat(balance(token, accId)).as("đầu kỳ 5tr − chi 100k = 4.9tr").isEqualTo(4_900_000.0);

        // 3. Sửa ví: đặt lại số dư = 10.000.000 (đang có Σtx = −100.000)
        //    → openingBalance = 10.000.000 − (−100.000) = 10.100.000; balance hiển thị = 10.000.000.
        Map edited = asMap(call("PUT", "/accounts/" + accId, token,
                Map.of("name", "Tien mat", "type", "cash", "balance", 10_000_000)).body());
        assertThat(((Number) edited.get("balance")).doubleValue()).as("đặt lại số dư = 10tr").isEqualTo(10_000_000.0);
        assertThat(((Number) edited.get("openingBalance")).doubleValue()).isEqualTo(10_100_000.0);

        // 4. Chi thêm 100k → 10.100.000 + (−200.000) = 9.900.000
        assertThat(call("POST", "/transactions", token, Map.of(
                "userId", userId, "accountId", accId, "categoryId", catId,
                "amount", 100_000, "type", "expense", "transactionDate", "2026-06-11")).status()).isEqualTo(201);
        assertThat(balance(token, accId)).as("đầu kỳ 10.1tr − chi 200k = 9.9tr").isEqualTo(9_900_000.0);
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

    private double balance(String token, String accountId) throws Exception {
        java.util.List<Map> accs = om.readValue(call("GET", "/accounts", token, null).body(), java.util.List.class);
        Map acc = accs.stream().filter(a -> accountId.equals(a.get("id"))).findFirst().orElseThrow();
        return ((Number) acc.get("balance")).doubleValue();
    }
}
