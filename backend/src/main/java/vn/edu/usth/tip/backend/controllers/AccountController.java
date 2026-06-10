package vn.edu.usth.tip.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.account.AccountRequest;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.services.AccountService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/delta")
    public ResponseEntity<DeltaResponse<AccountResponse>> getDelta(
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String untilTimestamp,
            @RequestParam(required = false) String lastUpdatedAt,
            @RequestParam(required = false) UUID lastId,
            @RequestParam(defaultValue = "500") int limit) {
        return ResponseEntity.ok(accountService.getDelta(updatedSince, untilTimestamp, lastUpdatedAt, lastId, limit));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable UUID id,
                                                          @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Reconciliation số dư (tính lại từ giao dịch) ───────────────────────────
    @PostMapping("/{id}/reconcile")
    public ResponseEntity<AccountResponse> reconcile(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.reconcileBalance(id));
    }

    @PostMapping("/reconcile-all")
    public ResponseEntity<List<AccountResponse>> reconcileAll() {
        return ResponseEntity.ok(accountService.reconcileAllBalances());
    }
}
