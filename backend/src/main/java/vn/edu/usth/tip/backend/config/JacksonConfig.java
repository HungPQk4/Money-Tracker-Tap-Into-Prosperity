package vn.edu.usth.tip.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cấu hình Jackson 3.x (tools.jackson) cho Spring Boot 4.x.
 *
 * Jackson 3.x thay đổi so với 2.x:
 * - WRITE_DATES_AS_TIMESTAMPS mặc định = FALSE (ISO-8601 string tự động)
 * - Dùng JsonMapper.builder() thay vì new ObjectMapper()
 * - java.time (OffsetDateTime, LocalDate) được hỗ trợ sẵn, không cần module riêng
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public JsonMapper objectMapper() {
        return JsonMapper.builder()
                // Chấp nhận enum theo tên không phân biệt hoa/thường
                // Ví dụ: "monthly", "MONTHLY", "Monthly" đều parse thành BudgetPeriod.monthly
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
    }
}
