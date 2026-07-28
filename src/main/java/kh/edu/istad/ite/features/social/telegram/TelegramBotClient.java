package kh.edu.istad.ite.features.social.telegram;

import kh.edu.istad.ite.config.props.TelegramProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TelegramBotClient {

    private final RestClient restClient;

    public TelegramBotClient(TelegramProps props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .build();
    }

    public TelegramBotIdentity getMe(String botToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/bot{token}/getMe", botToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telegram rejected this bot token");
            }

            if (!(body.get("result") instanceof Map<?, ?> result)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected response from Telegram");
            }

            Number id = (Number) result.get("id");
            Object username = result.get("username");

            return new TelegramBotIdentity(id == null ? null : id.longValue(),
                    username == null ? null : username.toString());
        } catch (RestClientException exception) {
            log.warn("Telegram getMe failed: {}", exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid Telegram bot token or Telegram is unreachable");
        }
    }

    public void setWebhook(String botToken, String webhookUrl, String secretToken) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "url", webhookUrl,
                    "secret_token", secretToken,
                    "allowed_updates", List.of("message", "callback_query")
            );

            Map<String, Object> body = restClient.post()
                    .uri("/bot{token}/setWebhook", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to register the Telegram webhook");
            }
        } catch (RestClientException exception) {
            log.warn("Telegram setWebhook failed: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to register the Telegram webhook");
        }
    }

    public void deleteWebhook(String botToken) {
        try {
            restClient.get()
                    .uri("/bot{token}/deleteWebhook", botToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram deleteWebhook failed: {}", exception.getMessage());
        }
    }

    public void sendMessage(String botToken, Long chatId, String text) {
        sendMessage(botToken, chatId, text, null);
    }

    // keyboard: rows of buttons, e.g. List.of(List.of(catalogBtn, cartBtn), List.of(profileBtn))
    // Pass null/empty for a plain text message with no buttons.
    public void sendMessage(String botToken, Long chatId, String text, List<List<InlineKeyboardButton>> keyboard) {
        try {
            Map<String, Object> requestBody = buildBody(chatId, text, keyboard);

            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram sendMessage failed for chat {}: {}", chatId, exception.getMessage());
        }
    }

    // Stops the spinner Telegram shows on the tapped button; optional toast text.
    public void answerCallbackQuery(String botToken, String callbackQueryId, String toastText) {
        try {
            Map<String, Object> requestBody = toastText == null
                    ? Map.of("callback_query_id", callbackQueryId)
                    : Map.of("callback_query_id", callbackQueryId, "text", toastText);

            restClient.post()
                    .uri("/bot{token}/answerCallbackQuery", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram answerCallbackQuery failed: {}", exception.getMessage());
        }
    }

    // Sends a photo by URL with a caption and optional inline keyboard - used for product detail cards.
    // Telegram captions are capped at 1024 chars; callers should keep detail text under that.
    public void sendPhoto(String botToken, Long chatId, String photoUrl, String caption, List<List<InlineKeyboardButton>> keyboard) {
        try {
            Map<String, Object> requestBody = keyboard == null || keyboard.isEmpty()
                    ? Map.of("chat_id", chatId, "photo", photoUrl, "caption", caption)
                    : Map.of("chat_id", chatId, "photo", photoUrl, "caption", caption,
                    "reply_markup", Map.of("inline_keyboard", toButtonMaps(keyboard)));

            restClient.post()
                    .uri("/bot{token}/sendPhoto", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram sendPhoto failed for chat {}: {}", chatId, exception.getMessage());
        }
    }

    private Map<String, Object> buildBody(Long chatId, String text, List<List<InlineKeyboardButton>> keyboard) {
        if (keyboard == null || keyboard.isEmpty()) {
            return Map.of("chat_id", chatId, "text", text);
        }

        return Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard", toButtonMaps(keyboard))
        );
    }

    private List<List<Map<String, String>>> toButtonMaps(List<List<InlineKeyboardButton>> keyboard) {
        return keyboard.stream()
                .map(row -> row.stream()
                        .map(button -> Map.of("text", button.label(), "callback_data", button.callbackData()))
                        .toList())
                .toList();
    }
}
