package kh.edu.istad.ite.features.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Messenger's own {@code getContext()}/{@code signed_request} identity turned
 * out to be unreliable in practice (Facebook-side {@code -32603}/{@code 2071011}
 * errors that never resolved) and gives no real name most of the time anyway
 * — so the Messenger Mini App now asks the customer to enter their own name
 * and phone once per device instead, keyed by a random id generated and kept
 * in that browser's own localStorage rather than anything Facebook hands us.
 */
public record FacebookDeviceAuthRequest(
        @NotBlank String businessId,
        @NotBlank @Size(max = 100) String deviceId,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 30) String phoneNumber
) {
}
