package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.RecordStatus;

import java.util.UUID;

public record CreateMembershipTypeRequest(
        @NotBlank(message = "typeName cannot be empty")
        @Size(max = 100, message = "typeName must be at most 100 characters")
        String typeName,
        @NotBlank(message = "remark cannot be empty")
        @Size(max = 150, message = "remark must be at most 150 characters")
        String remark,
        @NotBlank(message = "discountID cannot be empty")
        UUID discountId,
        @NotBlank(message = "status cannot be empty")
        RecordStatus status
) {
}
