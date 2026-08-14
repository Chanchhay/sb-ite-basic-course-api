package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplyKeyboardButton(
        @JsonProperty("text") String text,
        @JsonProperty("request_contact") Boolean requestContact
) {
    public ReplyKeyboardButton(String text) {
        this(text, null);
    }
}
