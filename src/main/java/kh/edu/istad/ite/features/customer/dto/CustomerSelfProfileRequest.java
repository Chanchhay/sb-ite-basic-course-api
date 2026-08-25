package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** What a logged-in customer can set about themselves for one business — not the fuller shape a business owner edits via {@code CustomerController}. */
public record CustomerSelfProfileRequest(
        @NotNull UUID businessId,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotBlank @Size(max = 100) String address
) {
}
