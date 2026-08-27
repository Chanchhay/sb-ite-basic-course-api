package kh.edu.istad.ite.features.social;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthResponse;
import kh.edu.istad.ite.features.social.service.TelegramWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public by necessity — this endpoint IS how a Telegram Mini App visitor gets a token in the first place, verified via initData rather than a bearer token nobody has yet. */
@RestController
@RequestMapping("/api/v1/telegram-webapp")
@RequiredArgsConstructor
public class TelegramWebAppAuthController {

    private final TelegramWebAppAuthService telegramWebAppAuthService;

    @PostMapping("/auth")
    public TelegramWebAppAuthResponse authenticate(@Valid @RequestBody TelegramWebAppAuthRequest request) {
        return telegramWebAppAuthService.authenticate(request);
    }
}
