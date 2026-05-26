package vn.edu.usth.tip.network.responses;

import java.util.List;

/**
 * Response chung cho tất cả /delta endpoints.
 * syncTimestamp = thời điểm server chụp snapshot, dùng làm cursor cho phiên sync tiếp theo.
 */
public class DeltaResponse<T> {
    private List<T> items;
    private boolean hasMore;
    private String syncTimestamp;

    public List<T> getItems() { return items; }
    public boolean isHasMore() { return hasMore; }
    public String getSyncTimestamp() { return syncTimestamp; }
}
