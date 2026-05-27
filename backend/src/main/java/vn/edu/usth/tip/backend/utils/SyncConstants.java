package vn.edu.usth.tip.backend.utils;

public final class SyncConstants {

    private SyncConstants() {}

    /** Default cursor for a full-sync request when the client has no prior checkpoint. */
    public static final String EPOCH_CURSOR = "1970-01-01T00:00:00Z";

    public static final int MAX_SYNC_ITEMS = 500;
}
