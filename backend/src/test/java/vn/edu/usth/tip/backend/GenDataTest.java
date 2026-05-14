package vn.edu.usth.tip.backend;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

public class GenDataTest {

    @Test
    public void generateData() throws Exception {
        String url = "jdbc:postgresql://ep-bold-unit-amd1epkd-pooler.c-5.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require";
        String user = "neondb_owner";
        String password = "npg_m2bhA6IcDMnw";
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to DB!");
            
            UUID userId = UUID.fromString("c0c02ed6-e2a0-446b-8dc1-add1d970d852");
            System.out.println("Using User: " + userId);
            
            // Get any account for this user
            UUID accountId = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM accounts WHERE user_id = ? LIMIT 1")) {
                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    accountId = (UUID) rs.getObject("id");
                }
            }
            if (accountId == null) {
                System.out.println("No account found!");
                return;
            }
            
            // Get Expense Category
            UUID expenseCatId = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM categories WHERE user_id = ? AND type = 'expense' LIMIT 1")) {
                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    expenseCatId = (UUID) rs.getObject("id");
                }
            }
            if (expenseCatId == null) {
                expenseCatId = UUID.randomUUID();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO categories (id, user_id, name, type, icon, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                    stmt.setObject(1, expenseCatId);
                    stmt.setObject(2, userId);
                    stmt.setString(3, "Ăn uống");
                    stmt.setString(4, "expense");
                    stmt.setString(5, "🍔");
                    stmt.setBoolean(6, true);
                    stmt.executeUpdate();
                }
            }
            
            // Get Income Category
            UUID incomeCatId = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM categories WHERE user_id = ? AND type = 'income' LIMIT 1")) {
                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    incomeCatId = (UUID) rs.getObject("id");
                }
            }
            if (incomeCatId == null) {
                incomeCatId = UUID.randomUUID();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO categories (id, user_id, name, type, icon, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                    stmt.setObject(1, incomeCatId);
                    stmt.setObject(2, userId);
                    stmt.setString(3, "Lương");
                    stmt.setString(4, "income");
                    stmt.setString(5, "💵");
                    stmt.setBoolean(6, true);
                    stmt.executeUpdate();
                }
            }

            String insertSql = "INSERT INTO transactions (id, user_id, account_id, category_id, amount, type, note, transaction_date, is_recurring, created_at, updated_at) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, false, NOW(), NOW())";
                               
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                Random rand = new Random();
                
                // For Months: Dec 2025, Jan 2026, Feb 2026
                int[][] months = { {2025, 12}, {2026, 1}, {2026, 2} };
                
                for (int[] ym : months) {
                    int year = ym[0];
                    int month = ym[1];
                    
                    // Generate 2 Income transactions
                    for (int i = 1; i <= 2; i++) {
                        stmt.setObject(1, UUID.randomUUID());
                        stmt.setObject(2, userId);
                        stmt.setObject(3, accountId);
                        stmt.setObject(4, incomeCatId);
                        
                        long amount = 10000000 + rand.nextInt(5000000); // 10m - 15m
                        stmt.setBigDecimal(5, new BigDecimal(amount));
                        stmt.setString(6, "income");
                        stmt.setString(7, i == 1 ? ("Lương tháng " + month) : "Thu nhập ngoài");
                        
                        LocalDate date = LocalDate.of(year, month, i == 1 ? 5 : 20);
                        stmt.setDate(8, java.sql.Date.valueOf(date));
                        stmt.executeUpdate();
                        System.out.println("Inserted INCOME for " + date);
                    }
                    
                    // Generate 8 Expense transactions
                    for (int i = 1; i <= 8; i++) {
                        stmt.setObject(1, UUID.randomUUID());
                        stmt.setObject(2, userId);
                        stmt.setObject(3, accountId);
                        stmt.setObject(4, expenseCatId);
                        
                        long amount = 50000 + rand.nextInt(950000); // 50k - 1m
                        stmt.setBigDecimal(5, new BigDecimal(amount));
                        stmt.setString(6, "expense");
                        stmt.setString(7, "Chi tiêu ngày " + (i * 3));
                        
                        LocalDate date = LocalDate.of(year, month, (i * 3));
                        stmt.setDate(8, java.sql.Date.valueOf(date));
                        stmt.executeUpdate();
                        System.out.println("Inserted EXPENSE for " + date);
                    }
                }
            }
            
            System.out.println("Done.");
        }
    }
}
