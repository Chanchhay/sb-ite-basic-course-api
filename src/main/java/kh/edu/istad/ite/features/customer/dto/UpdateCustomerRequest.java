package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateCustomerRequest(
        @Size(max = 200, message = "fullName must be at most 200 characters")
        String fullName,
        @Size(max = 30, message = "phoneNumber must be at most 30 characters")
        String phoneNumber,
        UUID membershipTypeId,
        UUID salesChannelId,
        @DecimalMin(value = "0.00", message = "totalSpend cannot be negative")
        BigDecimal totalSpend,
        LocalDateTime becameMembershipAt,
        Boolean active
) {
}
