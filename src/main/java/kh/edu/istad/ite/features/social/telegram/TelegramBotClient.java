package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.config.props.TelegramProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class TelegramBotClient {

    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_CAPTION_LENGTH = 1024;

    private static final String PARSE_MODE = "Markdown";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();


    private final ExecutorService fireAndForget =
            Executors.newVirtualThreadPerTaskExecutor();

    public TelegramBotClient(TelegramProps props) {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .requestFactory(requestFactory)
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


    public void sendMessage(String botToken, Long chatId, String text, List<List<InlineKeyboardButton>> keyboard) {
        String safeText = truncate(nullSafe(text, "..."), MAX_MESSAGE_LENGTH);

        if (postMessage(botToken, chatId, safeText, keyboard, true)) {
            return;
        }

        log.warn("Retrying chat {} without parse_mode after Telegram rejected the Markdown", chatId);
        postMessage(botToken, chatId, safeText, keyboard, false);
    }

    private boolean postMessage(
            String botToken,
            Long chatId,
            String text,
            List<List<InlineKeyboardButton>> keyboard,
            boolean withParseMode
    ) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", text);

            if (withParseMode) {
                requestBody.put("parse_mode", PARSE_MODE);
            }

            Map<String, Object> replyMarkup = buildReplyMarkup(keyboard);
            if (replyMarkup != null) {
                requestBody.put("reply_markup", replyMarkup);
            }

            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (HttpStatusCodeException exception) {
            log.warn("Telegram sendMessage failed for chat {}: {} -> {}",
                    chatId, exception.getStatusCode(), exception.getResponseBodyAsString());
            return false;
        } catch (RestClientException exception) {
            log.warn("Telegram sendMessage failed for chat {}: {}", chatId, exception.getMessage());
            return false;
        }
    }

    public void answerCallbackQuery(String botToken, String callbackQueryId, String toastText) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("callback_query_id", callbackQueryId);
            if (toastText != null) {
                requestBody.put("text", toastText);
            }

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


    public void sendPhoto(String botToken, Long chatId, String photoUrl, String caption,
                          List<List<InlineKeyboardButton>> keyboard) {
        String safeCaption = truncate(nullSafe(caption, ""), MAX_CAPTION_LENGTH);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("photo", photoUrl);
            requestBody.put("caption", safeCaption);
            requestBody.put("parse_mode", PARSE_MODE);

            Map<String, Object> replyMarkup = buildReplyMarkup(keyboard);
            if (replyMarkup != null) {
                requestBody.put("reply_markup", replyMarkup);
            }

            restClient.post()
                    .uri("/bot{token}/sendPhoto", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            log.warn("Telegram sendPhoto failed for chat {} (url {}): {} -> {}. Falling back to text.",
                    chatId, photoUrl, exception.getStatusCode(), exception.getResponseBodyAsString());
            sendMessage(botToken, chatId, caption, keyboard);
        } catch (RestClientException exception) {
            log.warn("Telegram sendPhoto failed for chat {}: {}. Falling back to text.",
                    chatId, exception.getMessage());
            sendMessage(botToken, chatId, caption, keyboard);
        }
    }


    public Integer sendPhotoBytes(String botToken, Long chatId, byte[] photo, String filename,
                               String caption, List<List<InlineKeyboardButton>> keyboard) {
        if (photo == null || photo.length == 0) {
            sendMessage(botToken, chatId, caption, keyboard);
            return null;
        }

        String safeCaption = truncate(nullSafe(caption, ""), MAX_CAPTION_LENGTH);

        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", String.valueOf(chatId));
            form.add("caption", safeCaption);
            form.add("parse_mode", PARSE_MODE);
            form.add("photo", new ByteArrayResource(photo) {
                @Override
                public String getFilename() {
                    return filename == null ? "image.png" : filename;
                }
            });

            Map<String, Object> replyMarkup = buildReplyMarkup(keyboard);
            if (replyMarkup != null) {
                form.add("reply_markup", objectMapper.writeValueAsString(replyMarkup));
            }

            Map<String, Object> response = restClient.post()
                    .uri("/bot{token}/sendPhoto", botToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                if (response.get("result") instanceof Map<?, ?> result) {
                    Number messageId = (Number) result.get("message_id");
                    return messageId != null ? messageId.intValue() : null;
                }
            }
            return null;
        } catch (HttpStatusCodeException exception) {
            log.warn("Telegram sendPhotoBytes failed for chat {}: {} -> {}. Falling back to text.",
                    chatId, exception.getStatusCode(), exception.getResponseBodyAsString());
            sendMessage(botToken, chatId, caption, keyboard);
            return null;
        } catch (Exception exception) {
            log.warn("Telegram sendPhotoBytes failed for chat {}: {}. Falling back to text.",
                    chatId, exception.getMessage());
            sendMessage(botToken, chatId, caption, keyboard);
            return null;
        }
    }

    public void deleteMessage(String botToken, Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "chat_id", chatId,
                    "message_id", messageId
            );

            restClient.post()
                    .uri("/bot{token}/deleteMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram deleteMessage failed for chat {} message {}: {}", chatId, messageId, exception.getMessage());
        }
    }


    public void answerCallbackQueryAsync(String botToken, String callbackQueryId, String toastText) {
        fireAndForget.execute(() -> answerCallbackQuery(botToken, callbackQueryId, toastText));
    }


    public void deleteMessageAsync(String botToken, Long chatId, Integer messageId) {
        fireAndForget.execute(() -> deleteMessage(botToken, chatId, messageId));
    }


    private Map<String, Object> buildReplyMarkup(List<List<InlineKeyboardButton>> keyboard) {
        if (keyboard == null || keyboard.isEmpty()) {
            return null;
        }

        List<List<Map<String, String>>> rows = keyboard.stream()
                .filter(row -> row != null && !row.isEmpty())
                .map(row -> row.stream()
                        .filter(button -> button != null
                                && button.label() != null
                                && (button.isLink() || button.callbackData() != null))
                        .map(this::toButtonMap)
                        .toList())
                .filter(row -> !row.isEmpty())
                .toList();

        if (rows.isEmpty()) {
            return null;
        }

        return Map.of("inline_keyboard", rows);
    }

    private Map<String, String> toButtonMap(InlineKeyboardButton button) {
        return button.isLink()
                ? Map.of("text", button.label(), "url", button.url())
                : Map.of("text", button.label(), "callback_data", truncateCallbackData(button.callbackData()));
    }


    private String truncateCallbackData(String data) {
        if (data.length() <= 64) {
            return data;
        }

        log.error("callback_data is {} chars, over Telegram's 64 limit, and will be truncated: {}",
                data.length(), data);

        return data.substring(0, 64);
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}