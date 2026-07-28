package kh.edu.istad.ite.features.social.telegram;

import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TelegramUIHelper {

    private static final String DIVIDER = "━━━━━━━━━━━━━━━━━━━━";

    /**
     * បង្កើត Header ស្អាតមានបន្ទាត់បែងចែកខាងក្រោម
     */
    public String header(String emoji, String title) {
        return emoji + " *" + title.toUpperCase() + "*\n" + DIVIDER + "\n";
    }

    /**
     * បង្កើតបន្ទាត់បែងចែកធម្មតា
     */
    public String divider() {
        return "\n" + DIVIDER + "\n";
    }

    /**
     * Format តម្លៃលុយឲ្យលោតក្នុងប្រអប់ Monospace Code ឧទាហរណ៍៖ `$15.00` ឬ `15,000 KHR`
     */
    public String formatPrice(BigDecimal price, BusinessTelegramBot setting) {
        if (price == null) {
            return "`—`";
        }
        String currency = setting.getBusiness().getDisplayCurrency() != null
                ? setting.getBusiness().getDisplayCurrency()
                : setting.getBusiness().getBaseCurrency();

        String formattedNumber = price.setScale(2, RoundingMode.HALF_UP).toString();
        if ("USD".equalsIgnoreCase(currency) || "$".equals(currency)) {
            return "`$" + formattedNumber + "`";
        } else {
            return "`" + formattedNumber + " " + currency + "`";
        }
    }

    /**
     * Format កាតបង្ហាញព័ត៌មានផលិតផល (Product Detail UX)
     */
    public String renderProductDetail(Item item, BusinessTelegramBot setting) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🏷️", item.getName()));
        sb.append("💵 តម្លៃទំនិញ ៖  ").append(formatPrice(item.getPrice(), setting)).append("\n");

        if (item.getItemGroup() != null) {
            sb.append("🗂️ ប្រភេទ    ៖  `").append(item.getItemGroup().getName()).append("`\n");
        }

        sb.append("📦 ស្ថានភាព   ៖  🟢 `In Stock`\n");

        if (StringUtils.hasText(item.getDescription())) {
            sb.append("\n📝 *ការពិពណ៌នា៖*\n");
            sb.append("_").append(item.getDescription().trim()).append("_\n");
        }

        sb.append(divider());
        sb.append("👇 *សូមជ្រើសរើសជម្រើសខាងក្រោម៖*");
        return sb.toString();
    }

    /**
     * Format កន្ត្រកទំនិញបែប Digital Receipt UX
     */
    public String renderCartReceipt(Cart cart, BusinessTelegramBot setting, String customerName) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🛒", "កន្ត្រកទំនិញរបស់អ្នក (YOUR CART)"));

        sb.append("👤 អតិថិជន ៖ *").append(customerName != null ? customerName : "អតិថិជនទូទៅ").append("*\n");
        sb.append("🏪 ហាង     ៖ *").append(setting.getBusiness().getDisplayName()).append("*\n\n");
        sb.append("📦 *បញ្ជីមុខទំនិញ៖*\n");

        int index = 1;
        for (CartItem ci : cart.getItems()) {
            Item item = ci.getItem();
            sb.append("*").append(index++).append(".* ").append(item.getName()).append("\n");
            sb.append(" └ ").append(ci.getQuantity()).append(" x ")
                    .append(formatPrice(ci.getPriceSnapshot(), setting))
                    .append(" = ").append(formatPrice(ci.getSubtotal(), setting)).append("\n");
        }

        sb.append(divider());
        sb.append("📊 ចំនួនទំនិញសរុប ៖  `").append(cart.getTotalItemsCount()).append(" មុខ`\n");
        sb.append("💳 *ទឹកប្រាក់សរុប   ៖*  ").append(formatPrice(cart.getTotalAmount(), setting)).append("\n");
        sb.append(DIVIDER).append("\n");
        sb.append("⚠️ _សូមពិនិត្យបញ្ជីទំនិញមុនពេលបន្តទៅការទូទាត់ប្រាក់_");

        return sb.toString();
    }

    /**
     * Format សារស្វាគមន៍ Main Menu
     */
    public String renderWelcomeMessage(BusinessTelegramBot setting, String customerName) {
        StringBuilder sb = new StringBuilder();
        String storeName = setting.getBusiness().getDisplayName();

        sb.append("⚡️ *WELCOME TO ").append(storeName.toUpperCase()).append("* ⚡️\n");
        sb.append(DIVIDER).append("\n");

        if (customerName != null) {
            sb.append("👋 សួស្តី *").append(customerName).append("*! សូមស្វាគមន៍មកកាន់ហាងរបស់យើង។\n\n");
        } else {
            sb.append("👋 សួស្តី! សូមស្វាគមន៍មកកាន់ហាងរបស់យើង។\n\n");
        }

        if (StringUtils.hasText(setting.getWelcomeMessage())) {
            sb.append("_").append(setting.getWelcomeMessage()).append("_\n\n");
        } else {
            sb.append("🛍️ *បទពិសោធន៍ទិញទំនិញបែបថ្មី៖*\n");
            sb.append("▫️ ស្វែងរកផលិតផលលឿនរហ័ស\n");
            sb.append("▫️ បញ្ជាទិញងាយស្រួល 24/7\n");
            sb.append("▫️ ទូទាត់ប្រាក់សុវត្ថិភាពតាម KHQR\n\n");
        }

        sb.append("👇 *សូមជ្រើសរើសជម្រើសក្នុងម៉ឺនុយខាងក្រោម៖*");
        return sb.toString();
    }
}