package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.auth.AuthResponse;
import vn.edu.usth.tip.backend.dto.auth.LoginRequest;
import vn.edu.usth.tip.backend.dto.auth.RegisterRequest;
import vn.edu.usth.tip.backend.dto.auth.SessionResponse;
import vn.edu.usth.tip.backend.exception.InvalidTokenException;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.UserRepository;
import vn.edu.usth.tip.backend.security.CustomUserDetailsService;
import vn.edu.usth.tip.backend.security.JwtUtil;
import vn.edu.usth.tip.backend.utils.SecurityUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final SessionService sessionService;
    private final SecurityUtils securityUtils;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use: " + req.getEmail());
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setCurrencyCode(req.getCurrencyCode());
        user.setTimezone(req.getTimezone());
        User saved = userRepository.save(user);

        return issueTokens(saved, req.getDeviceName());
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", req.getEmail()));

        return issueTokens(user, req.getDeviceName());
    }

    /** Cấp access token + tạo phiên mới (refresh token) cho một thiết bị. */
    private AuthResponse issueTokens(User user, String deviceName) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        SessionService.SessionCreation sc = sessionService.createSession(user.getId(), deviceName, null);
        String token = jwtUtil.generateToken(userDetails, sc.session().getId());
        return new AuthResponse(token, sc.rawRefreshToken(), user.getId(), user.getEmail(), user.getFullName());
    }

    /** Đổi refresh token lấy access token mới (đồng thời xoay refresh token). */
    public AuthResponse refresh(String rawRefresh) {
        SessionService.SessionCreation sc = sessionService.rotate(rawRefresh);
        User user = userRepository.findById(sc.session().getUserId())
                .orElseThrow(() -> new InvalidTokenException("Người dùng không tồn tại"));
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(userDetails, sc.session().getId());
        return new AuthResponse(token, sc.rawRefreshToken(), user.getId(), user.getEmail(), user.getFullName());
    }

    /** Đăng xuất phiên hiện tại (thu hồi). */
    public void logout(UUID currentSessionId) {
        sessionService.revoke(currentSessionId, securityUtils.getCurrentUser().getId());
    }

    /** Danh sách thiết bị/phiên đang đăng nhập của user hiện tại. */
    public List<SessionResponse> listSessions(UUID currentSessionId) {
        UUID userId = securityUtils.getCurrentUser().getId();
        return sessionService.listActive(userId).stream()
                .map(s -> new SessionResponse(
                        s.getId(), s.getDeviceName(), s.getDeviceInfo(),
                        s.getCreatedAt(), s.getLastSeenAt(),
                        s.getId().equals(currentSessionId)))
                .toList();
    }

    /** Thu hồi một phiên cụ thể (đăng xuất từ xa một thiết bị). */
    public void revokeSession(UUID sessionId) {
        sessionService.revoke(sessionId, securityUtils.getCurrentUser().getId());
    }

    /** Thu hồi mọi phiên khác, giữ lại phiên hiện tại. */
    public void logoutOtherSessions(UUID currentSessionId) {
        sessionService.revokeAllExcept(securityUtils.getCurrentUser().getId(), currentSessionId);
    }
}
