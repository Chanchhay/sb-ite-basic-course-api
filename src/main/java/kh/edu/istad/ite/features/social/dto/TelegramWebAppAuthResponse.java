package kh.edu.istad.ite.features.social.dto;

import java.util.UUID;

public record TelegramWebAppAuthResponse(
        String token,
        String refreshToken,
        UUID businessId,
        String businessName,
        String businessSlug,
        String logoUrl,
        UUID customerId,
        UUID globalCustomerId,
        Long telegramUserId,
        String telegramUsername,
        String fullName,
        String photoUrl,
        String phoneNumber,
        String email,
        String address,
        /** False until both phoneNumber and address are set — gates the Mini App's "complete your profile" screen. */
        boolean profileComplete
) {
}
