package kh.edu.istad.ite.features.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;


public record BusinessChannelResponse(
        UUID businessId,
        String businessName,
        String slug,

        boolean storefrontPublished,
        String storefrontUrl,
        String website,

        boolean telegramConnected,
        String telegramBotUsername,
        Long telegramBotId,
        boolean telegramActive,

        boolean bakongConfigured,
        boolean bakongActive,

        LocalDateTime registeredAt
) {
}
