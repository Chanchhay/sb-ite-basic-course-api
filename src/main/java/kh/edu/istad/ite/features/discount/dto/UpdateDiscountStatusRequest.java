package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.RecordStatus;

public record UpdateDiscountStatusRequest(
        @NotNull(message = "status is required")
        RecordStatus status
) {
}
