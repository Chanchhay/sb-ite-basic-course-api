package kh.edu.istad.ite.features.order.dto;

import java.util.List;

public record SyncOfflineOrdersResponse(
        boolean success,
        List<String> syncedUuids
) {}
