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
 * Mục 3 (transfer): chuyển khoản phải TRỪ ví nguồn và CỘNG ví đích — số dư = Σ giao dịch,
 * không còn "bốc hơi" tiền. Boot toàn bộ app trên H2 in-memory (profile "test").
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.api.key=test"})
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class TransferBalanceFlowTest {

    @Autowired private Environment env;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + env.getProperty("local.server.port") + "/api";
    }

    @Test
    void transfer_debitsSource_andCreditsDestination() throws Exception {
        String email = "tf-" + UUID.randomUUID() + "@test.com";

        // 1. Đăng ký → access token + userId
        Resp reg = call("POST", "/auth/register", null,
                Map.of("email", email, "password", "123456", "fullName", "TF User", "deviceName", "D"));
        assertThat(reg.status()).isEqualTo(201);
        Map regBody = asMap(reg.body());
        String token  = (String) regBody.get("token");
        String userId = (String) regBody.get("userId");

        // 2. Hai ví A (số dư khởi tạo 0) và B (0)
        String accA = (String) asMap(call("POST", "/accounts", token,
                Map.of("name", "Vi A", "type", "cash", "balance", 0)).body()).get("id");
        String accB = (String) asMap(call("POST", "/accounts", token,
                Map.of("name", "Vi B", "type", "cash", "balance", 0)).body()).get("id");

        // 3. Một category (transaction yêu cầu categoryId)
        String catId = (String) asMap(call("POST", "/categories", token,
                Map.of("name", "Chuyen khoan", "type", "expense", "userId", userId)).body()).get("id");

        // 4. Nạp 100k vào ví A (income) → A = 100k
        Resp income = call("POST", "/transactions", token, Map.of(
                "userId", userId, "accountId", accA, "categoryId", catId,
                "amount", 100000, "type", "income", "transactionDate", "2026-06-10"));
        assertThat(income.status()).as("income lỗi: " + income.body()).isEqualTo(201);
        assertThat(balance(token, accA)).as("A sau income").isEqualTo(100000.0);
        assertThat(balance(token, accB)).as("B chưa đổi").isEqualTo(0.0);

        // 5. Chuyển 30k A → B (type=transfer, accountId=A, toAccountId=B)
        Resp tf = call("POST", "/transactions", token, Map.of(
                "userId", userId, "accountId", accA, "toAccountId", accB, "categoryId", catId,
                "amount", 30000, "type", "transfer", "transactionDate", "2026-06-11"));
        assertThat(tf.status()).as("transfer lỗi: " + tf.body()).isEqualTo(201);

        // 6. KIỂM CHỨNG: tổng không đổi — A giảm 30k, B tăng 30k.
        assertThat(balance(token, accA)).as("A sau transfer -30k").isEqualTo(70000.0);
        assertThat(balance(token, accB)).as("B sau transfer +30k").isEqualTo(30000.0);

        // 7. response của transfer mang toAccountId/toAccountName
        Map tfBody = asMap(tf.body());
        assertThat(tfBody.get("toAccountId")).as("response có toAccountId").isEqualTo(accB);
        assertThat(tfBody.get("toAccountName")).isEqualTo("Vi B");

        // 8. Xoá transfer → tiền quay lại ví nguồn, ví đích về 0.
        String tfId = (String) tfBody.get("id");
        assertThat(call("DELETE", "/transactions/" + tfId, token, null).status()).isEqualTo(204);
        assertThat(balance(token, accA)).as("A sau xoá transfer").isEqualTo(100000.0);
        assertThat(balance(token, accB)).as("B sau xoá transfer").isEqualTo(0.0);
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
