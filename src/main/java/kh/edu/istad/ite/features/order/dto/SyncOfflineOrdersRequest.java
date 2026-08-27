package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SyncOfflineOrdersRequest(
        @NotNull
        @Valid
        List<OfflineOrderDto> orders
) {}
