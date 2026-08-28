package kh.edu.istad.ite.features.social.dto;

import java.util.UUID;

public record FacebookWebAppAuthResponse(
        String token,
        String refreshToken,
        UUID businessId,
        String businessName,
        String businessSlug,
        String logoUrl,
        UUID customerId,
        UUID globalCustomerId,
        /** The psid for a real signed_request login, or the local device id for the device-registration flow. */
        String externalId,
        String fullName,
        String phoneNumber,
        String email,
        String gender,
        String address,
        /** False until email, gender, phoneNumber and address are all set — gates the Mini App's "complete your profile" screen. */
        boolean profileComplete
) {
}
