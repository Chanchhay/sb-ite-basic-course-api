package kh.edu.istad.ite.features.social.telegram;

import kh.edu.istad.ite.features.catalog.entity.Item;
import java.util.ArrayList;
import java.util.List;

public class TelegramKeyboards {

    public static List<List<InlineKeyboardButton>> mainMenu(boolean registered) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (!registered) {
            keyboard.add(List.of(
                    new InlineKeyboardButton("📝 ចុះឈ្មោះ / ចូលគណនី (Register / Login)", "auth:register:start")
            ));
        }

        keyboard.add(List.of(
                new InlineKeyboardButton("🛍️ Product Catalog", "menu:catalog"),
                new InlineKeyboardButton("🔍 Search Product", "menu:search")
        ));

        keyboard.add(List.of(
                new InlineKeyboardButton("🛒 Cart", "menu:cart"),
                new InlineKeyboardButton("✅ Checkout", "menu:checkout")
        ));

        keyboard.add(List.of(
                new InlineKeyboardButton("📦 Order History", "menu:orders"),
                new InlineKeyboardButton("🕒 User History", "menu:history")
        ));

        if (registered) {
            keyboard.add(List.of(
                    new InlineKeyboardButton("👤 My Profile", "menu:profile"),
                    new InlineKeyboardButton("📍 Location", "menu:location")
            ));
            keyboard.add(List.of(
                    new InlineKeyboardButton("🚪 ចាកចេញពីគណនី (Logout)", "auth:logout")
            ));
        } else {
            keyboard.add(List.of(
                    new InlineKeyboardButton("👤 My Profile", "menu:profile"),
                    new InlineKeyboardButton("📍 Location", "menu:location")
            ));
        }

        return keyboard;
    }

    public static List<List<InlineKeyboardButton>> backToMenu() {
        return List.of(
                List.of(new InlineKeyboardButton("⬅️ ត្រលប់ទៅម៉ឺនុយដើម (Main Menu)", "menu:main"))
        );
    }

    public static List<List<InlineKeyboardButton>> getCancelSearchKeyboard() {
        return List.of(
                List.of(new InlineKeyboardButton("❌ បោះបង់ការស្វែងរក (Cancel)", "search:cancel"))
        );
    }

    public static List<List<InlineKeyboardButton>> getSearchAgainOrMenuKeyboard() {
        return List.of(
                List.of(new InlineKeyboardButton("🔍 ស្វែងរកម្ដងទៀត (Search Again)", "menu:search")),
                List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម (Main Menu)", "menu:main"))
        );
    }

    public static List<List<InlineKeyboardButton>> buildProductListKeyboard(List<Item> items) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : items) {
            String label = "▫️ " + item.getName();
            keyboard.add(List.of(new InlineKeyboardButton(label, "item:" + item.getId())));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🔍 ស្វែងរកម្ដងទៀត (Search Again)", "menu:search")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម (Main Menu)", "menu:main")));
        return keyboard;
    }
}