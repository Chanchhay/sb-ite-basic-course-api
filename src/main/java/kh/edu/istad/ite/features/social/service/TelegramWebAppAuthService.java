package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthResponse;

public interface TelegramWebAppAuthService {

    /**
     * Verifies a Telegram Mini App's {@code initData}, then finds-or-creates
     * a real, usable login for that Telegram identity — a Keycloak user,
     * a per-business {@code Customer}, and the channel identity linking the
     * two — so the Mini App can call every other authenticated storefront
     * endpoint (cart, checkout, order history) exactly like a normal
     * logged-in shopper, without ever asking for a password.
     */
    TelegramWebAppAuthResponse authenticate(TelegramWebAppAuthRequest request);
}
