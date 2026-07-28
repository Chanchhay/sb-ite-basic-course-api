package kh.edu.istad.ite.shared.enums;

public enum BusinessFeature {

//    Public shop page and its listing. Checked in StorefrontServiceImpl
    STOREFRONT("Storefront", "The public shop page and directory listing"),

    // Incoming Telegram updates. Checked in TelegramWebhookServiceImpl.
    TELEGRAM_BOT("Telegram bot", "Taking orders through a Telegram bot"),

    // KHQR generation. Checked in OrderServiceImpl and BakongSettingServiceImpl.
    KHQR_PAYMENT("KHQR payment", "Generating Bakong QR codes for payment");

    private final String label;
    private final String description;

    BusinessFeature(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
