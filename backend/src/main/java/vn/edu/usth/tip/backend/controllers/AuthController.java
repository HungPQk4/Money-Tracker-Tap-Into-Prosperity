package vn.edu.usth.tip.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.auth.AuthResponse;
import vn.edu.usth.tip.backend.dto.auth.LoginRequest;
import vn.edu.usth.tip.backend.dto.auth.RegisterRequest;
import vn.edu.usth.tip.backend.services.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Body: { "email", "password", "fullName", "currencyCode", "timezone" }
     * Returns: JWT token + user info
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    /**
     * POST /api/auth/login
     * Body: { "email", "password" }
     * Returns: JWT token + user info hoặc thông báo lỗi
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            // Cố gắng đăng nhập
            AuthResponse response = authService.login(req);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            // Bắt chính xác lỗi sai mật khẩu từ AuthenticationManager
            // Trả về mã 401 Unauthorized thay vì để app văng lỗi 500
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Sai email hoặc mật khẩu!"));

        } catch (Exception e) {
            // Bắt các lỗi hệ thống khác (nếu có) và in ra màn hình CMD để bạn dễ sửa
            System.err.println("==== LỖI HỆ THỐNG TẠI API LOGIN ====");
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", e.getMessage()));
        }
    }
}