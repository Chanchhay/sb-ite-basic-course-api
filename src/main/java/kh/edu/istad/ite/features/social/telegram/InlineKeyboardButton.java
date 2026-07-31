package kh.edu.istad.ite.features.social.telegram;

public record InlineKeyboardButton(String label, String callbackData, String url) {

    // Callback button - the common case, keeps every existing call site working.
    public InlineKeyboardButton(String label, String callbackData) {
        this(label, callbackData, null);
    }

    // Link button, e.g. opening the shop on Google Maps.
    public static InlineKeyboardButton link(String label, String url) {
        return new InlineKeyboardButton(label, null, url);
    }

    public boolean isLink() {
        return url != null && !url.isBlank();
    }
}