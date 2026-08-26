package kh.edu.istad.ite.features.social.telegram;

public record InlineKeyboardButton(String label, String callbackData, String url, String webAppUrl) {

    public InlineKeyboardButton(String label, String callbackData) {
        this(label, callbackData, null, null);
    }

    public static InlineKeyboardButton link(String label, String url) {
        return new InlineKeyboardButton(label, null, url, null);
    }

    /**
     * A button that launches a Mini App inside Telegram itself (real
     * {@code initData}, same as the persistent menu button) — unlike
     * {@link #link}, which just opens the URL in a plain browser/webview
     * with no Telegram identity attached. The only way to offer "Open Shop"
     * in a group chat, where the persistent menu button doesn't appear.
     */
    public static InlineKeyboardButton webApp(String label, String url) {
        return new InlineKeyboardButton(label, null, null, url);
    }

    public boolean isLink() {
        return url != null && !url.isBlank();
    }

    public boolean isWebApp() {
        return webAppUrl != null && !webAppUrl.isBlank();
    }
}