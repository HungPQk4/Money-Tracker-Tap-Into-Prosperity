package vn.edu.usth.tip.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.debt.CreateDebtRequest;
import vn.edu.usth.tip.backend.dto.debt.DebtResponse;
import vn.edu.usth.tip.backend.services.DebtService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @PostMapping
    public ResponseEntity<DebtResponse> createDebt(@Valid @RequestBody CreateDebtRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debtService.createDebt(req));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<DebtResponse>> getDebtsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(debtService.getDebtsByUser(userId));
    }

    @GetMapping
    public ResponseEntity<List<DebtResponse>> getAllDebts() {
        return ResponseEntity.ok(debtService.getAllDebts());
    }

    @PatchMapping("/{id}/settle")
    public ResponseEntity<DebtResponse> settleDebt(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.settleDebt(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebt(@PathVariable UUID id) {
        debtService.deleteDebt(id);
        return ResponseEntity.noContent().build();
    }
}
