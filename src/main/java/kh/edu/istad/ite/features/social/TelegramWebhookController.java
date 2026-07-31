package kh.edu.istad.ite.features.social;

import kh.edu.istad.ite.features.social.service.TelegramWebhookService;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;

    @PostMapping("/{webhookSecret}")
    public ResponseEntity<Void> receiveUpdate(
            @PathVariable String webhookSecret,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretTokenHeader,
            @RequestBody TelegramUpdate update
    ) {
        telegramWebhookService.handleUpdate(webhookSecret, secretTokenHeader, update);
        return ResponseEntity.ok().build();
    }
}
