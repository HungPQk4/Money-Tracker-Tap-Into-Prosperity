package vn.edu.usth.tip.backend.exception;

/**
 * Xung đột phiên bản (optimistic concurrency): bản ghi đã bị sửa ở nơi khác kể từ lần client đọc.
 * Controller/GlobalExceptionHandler ánh xạ sang HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
