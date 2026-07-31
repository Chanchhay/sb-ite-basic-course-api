package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateMembershipTypeRequest(
        @Size(max = 100, message = "typeName must be at most 100 characters")
        String typeName,

        String remark,

        UUID discountId,

        @Pattern(regexp = "ACTIVE|INACTIVE", message = "status must be one of: ACTIVE, INACTIVE")
        String status
) {
}
