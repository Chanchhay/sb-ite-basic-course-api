package kh.edu.istad.ite.features.social.telegram;

public record InlineKeyboardButton(String label, String callbackData, String url) {

    public InlineKeyboardButton(String label, String callbackData) {
        this(label, callbackData, null);
    }

    public static InlineKeyboardButton link(String label, String url) {
        return new InlineKeyboardButton(label, null, url);
    }

    public boolean isLink() {
        return url != null && !url.isBlank();
    }
}