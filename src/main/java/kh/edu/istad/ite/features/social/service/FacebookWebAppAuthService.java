package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.dto.FacebookDeviceAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthResponse;

public interface FacebookWebAppAuthService {

    /**
     * Verifies a Messenger webview's {@code signed_request}, then
     * finds-or-creates a real, usable login for that PSID — a Keycloak
     * user, a per-business {@code Customer}, and the channel identity
     * linking the two — so the webview can call every other authenticated
     * storefront endpoint (cart, checkout, order history) exactly like a
     * normal logged-in shopper, without ever asking for a password. Mirrors
     * {@link TelegramWebAppAuthService}.
     */
    FacebookWebAppAuthResponse authenticate(FacebookWebAppAuthRequest request);

    /**
     * Registers (or re-recognizes) a Messenger Mini App visitor from a
     * customer-entered name and phone plus a device id generated client-side
     * — the fallback identity path now that {@code signed_request} auth is
     * no longer used at all for this Mini App.
     */
    FacebookWebAppAuthResponse authenticateDevice(FacebookDeviceAuthRequest request);
}
