package vn.edu.usth.tip.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        runIfMissing("categories", "updated_at",
                "ALTER TABLE categories ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        runIfMissing("categories", "deleted_at",
                "ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMPTZ");
        runIfMissing("transactions", "deleted_at",
                "ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMPTZ");
    }

    private void runIfMissing(String table, String column, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            if (count == null || count == 0) {
                log.info("Adding missing column {}.{}", table, column);
                jdbcTemplate.execute(ddl);
                log.info("Column {}.{} added successfully", table, column);
            }
        } catch (Exception e) {
            log.error("Failed to add column {}.{}: {}", table, column, e.getMessage());
        }
    }
}
