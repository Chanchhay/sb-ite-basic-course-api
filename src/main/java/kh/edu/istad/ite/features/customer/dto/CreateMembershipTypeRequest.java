package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.RecordStatus;

import java.util.UUID;

public record CreateMembershipTypeRequest(
        @NotBlank(message = "typeName cannot be empty")
        @Size(max = 100, message = "typeName must be at most 100 characters")
        String typeName,
        @Size(max = 150, message = "remark must be at most 150 characters")
        String remark,
        UUID discountId,
        RecordStatus status
) {
}
