package kh.edu.istad.ite.features.social.telegram;

import java.util.List;

public final class TelegramKeyboards {

    private TelegramKeyboards() {
    }

    public static List<List<InlineKeyboardButton>> mainMenu(boolean isRegistered) {
        InlineKeyboardButton authButton = isRegistered
                ? new InlineKeyboardButton("👤 My Profile", "menu:profile")
                : new InlineKeyboardButton("📝 Register / Login", "auth:register:start");

        return List.of(
                List.of(new InlineKeyboardButton("🛍️ Product Catalog", "menu:catalog"),
                        new InlineKeyboardButton("🔎 Search Product", "menu:search")),
                List.of(new InlineKeyboardButton("🛒 Cart", "menu:cart"),
                        new InlineKeyboardButton("✅ Checkout", "menu:checkout")),
                List.of(new InlineKeyboardButton("📦 Order History", "menu:orders"),
                        new InlineKeyboardButton("🕘 User History", "menu:history")),
                List.of(authButton,
                        new InlineKeyboardButton("📍 Location", "menu:location"))
        );
    }

    public static List<List<InlineKeyboardButton>> backToMenu() {
        return List.of(List.of(new InlineKeyboardButton("⬅️ Main Menu", "menu:main")));
    }
}
