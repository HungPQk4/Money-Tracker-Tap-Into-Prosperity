package vn.edu.usth.tip.backend.dto.common;

import java.util.List;

public record DeltaResponse<T>(
        List<T> items,
        boolean hasMore,
        String syncTimestamp   // ISO-8601, server time at start of query — client stores as next cursor
) {}
