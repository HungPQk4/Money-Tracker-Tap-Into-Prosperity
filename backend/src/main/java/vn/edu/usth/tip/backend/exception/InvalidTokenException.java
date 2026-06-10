package vn.edu.usth.tip.backend.exception;

/** Refresh token sai / đã thu hồi / hết hạn. Controller ánh xạ sang HTTP 401. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
