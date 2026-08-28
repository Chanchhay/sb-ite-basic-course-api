package kh.edu.istad.ite.features.social;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.social.dto.FacebookDeviceAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthResponse;
import kh.edu.istad.ite.features.social.service.FacebookWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public by necessity — these endpoints ARE how a Messenger webview visitor gets a token in the first place, before any bearer token exists yet. */
@RestController
@RequestMapping("/api/v1/facebook-webapp")
@RequiredArgsConstructor
public class FacebookWebAppAuthController {

    private final FacebookWebAppAuthService facebookWebAppAuthService;

    /** Kept for any Page still relying on signed_request, but no longer called by the Mini App itself — see /device-auth. */
    @PostMapping("/auth")
    public FacebookWebAppAuthResponse authenticate(@Valid @RequestBody FacebookWebAppAuthRequest request) {
        return facebookWebAppAuthService.authenticate(request);
    }

    /** What the Mini App actually calls now — the customer's own name/phone plus a client-generated device id, no Facebook identity verification involved. */
    @PostMapping("/device-auth")
    public FacebookWebAppAuthResponse authenticateDevice(@Valid @RequestBody FacebookDeviceAuthRequest request) {
        return facebookWebAppAuthService.authenticateDevice(request);
    }
}
