package vn.edu.usth.tip.utils;

import androidx.annotation.Nullable;

/**
 * Wrapper để dùng LiveData như một event stream thay vì state holder.
 * Observer gọi getContentIfNotHandled() — chỉ nhận được content lần đầu tiên,
 * tránh trường hợp Fragment quay lại từ background xử lý lại event cũ.
 */
public class Event<T> {
    private final T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getContentIfNotHandled() {
        if (hasBeenHandled) return null;
        hasBeenHandled = true;
        return content;
    }

    public T peekContent() {
        return content;
    }
}
