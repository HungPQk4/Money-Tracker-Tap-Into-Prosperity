package vn.edu.usth.tip.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.transaction.CreateTransactionRequest;
import vn.edu.usth.tip.backend.dto.transaction.SyncRequest;
import vn.edu.usth.tip.backend.dto.transaction.SyncResponse;
import vn.edu.usth.tip.backend.dto.transaction.TransactionResponse;
import vn.edu.usth.tip.backend.services.TransactionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // ─── Tạo giao dịch đơn lẻ (nhập tay real-time) ───────────────────────────
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(req));
    }

    // ─── Đồng bộ batch offline (tối đa 30 ngày, giữ createdAt gốc) ──────────
    /**
     * POST /api/transactions/sync
     * Body: { "userId": "...", "transactions": [...] }
     * Response: { "savedCount": N, "skippedCount": M, "savedTransactions": [...] }
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncTransactions(
            @Valid @RequestBody SyncRequest req) {
        SyncResponse response = transactionService.syncTransactions(req);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
    }

    // ─── Lấy giao dịch 30 ngày gần nhất (của user hiện tại qua JWT) ──────────
    /**
     * GET /api/transactions/recent?days=30
     * Header: Authorization: Bearer <token>
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TransactionResponse>> getRecentTransactions(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(transactionService.getMyRecentTransactions(days));
    }

    // ─── Lấy giao dịch gần đây theo userId cụ thể ────────────────────────────
    @GetMapping("/recent/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getRecentByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(transactionService.getRecentTransactions(userId, days));
    }

    // ─── CRUD cơ bản ──────────────────────────────────────────────────────────
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(transactionService.getTransactionsByUser(userId));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTransactionRequest req) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
