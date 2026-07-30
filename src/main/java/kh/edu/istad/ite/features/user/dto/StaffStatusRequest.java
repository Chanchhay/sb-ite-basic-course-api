package kh.edu.istad.ite.features.user.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.RecordStatus;

public record StaffStatusRequest(
        @NotNull(message = "Status is required")
        RecordStatus status
) {}
