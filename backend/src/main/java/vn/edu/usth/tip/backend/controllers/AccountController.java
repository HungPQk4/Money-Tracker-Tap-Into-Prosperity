package vn.edu.usth.tip.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.account.AccountRequest;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.services.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody AccountRequest request) {
        try {
            AccountResponse newAccount = accountService.createAccount(request);
            return new ResponseEntity<>(newAccount, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Tạo tài khoản thất bại: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable java.util.UUID id, @RequestBody AccountRequest request) {
        try {
            AccountResponse updatedAccount = accountService.updateAccount(id, request);
            return ResponseEntity.ok(updatedAccount);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Cập nhật tài khoản thất bại: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable java.util.UUID id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.ok("Xóa ví thành công");
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Xóa ví thất bại: " + e.getMessage());
        }
    }
}
