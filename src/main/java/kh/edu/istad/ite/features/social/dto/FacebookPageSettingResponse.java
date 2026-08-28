package kh.edu.istad.ite.features.social.dto;

import java.util.UUID;

public record FacebookPageSettingResponse(
        UUID id,
        UUID businessId,
        String pageId,
        String pageName,
        boolean connected,
        boolean active,
        String welcomeMessage,
        boolean miniAppEnabled,
        /** Only set when miniAppEnabled — the same webview URL the persistent menu button opens. */
        String miniAppUrl
) {}
