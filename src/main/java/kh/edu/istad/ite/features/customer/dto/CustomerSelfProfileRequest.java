package kh.edu.istad.ite.features.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * What a logged-in customer can set about themselves for one business — not
 * the fuller shape a business owner edits via {@code CustomerController}.
 * Only {@code businessId} and {@code phoneNumber} are required: the
 * Telegram/Messenger Mini Apps only ever prompt for a phone number at
 * checkout, and the older full-profile screen that also collects the other
 * fields sends them alongside it — either shape hits this same endpoint.
 */
public record CustomerSelfProfileRequest(
        @NotNull UUID businessId,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 20) String gender,
        @NotBlank @Size(max = 30) String phoneNumber,
        @Size(max = 100) String address
) {
}
