package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramFrom(
        Long id,
        @JsonProperty("is_bot") Boolean isBot,
        @JsonProperty("first_name") String firstName,
        String username
) {
}
