package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ReplyKeyboardMarkup(
        @JsonProperty("keyboard") List<List<ReplyKeyboardButton>> keyboard,
        @JsonProperty("resize_keyboard") boolean resizeKeyboard,
        @JsonProperty("is_persistent") boolean isPersistent
) {
    public ReplyKeyboardMarkup(List<List<ReplyKeyboardButton>> keyboard) {
        this(keyboard, true, true);
    }
}
