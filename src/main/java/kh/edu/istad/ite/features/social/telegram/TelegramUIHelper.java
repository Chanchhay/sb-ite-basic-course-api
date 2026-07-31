package kh.edu.istad.ite.features.social.telegram;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

@Component
public class TelegramUIHelper {

    private static final String DIVIDER = "━━━━━━━━━━━━━━━━━━━━";

    public String header(String emoji, String title) {
        return emoji + " *" + title.toUpperCase() + "*\n" + DIVIDER + "\n";
    }


    public String divider() {
        return "\n" + DIVIDER + "\n";
    }


    public String formatPrice(BigDecimal price, BusinessTelegramBot setting) {
        return formatPrice(price, setting.getBusiness());
    }

    public String formatPrice(BigDecimal price, Business business) {
        if (price == null) {
            return "`—`";
        }
        String currency = business.getDisplayCurrency() != null
                ? business.getDisplayCurrency()
                : business.getBaseCurrency();

        String formattedNumber = price.setScale(2, RoundingMode.HALF_UP).toString();
        if ("USD".equalsIgnoreCase(currency) || "$".equals(currency)) {
            return "`$" + formattedNumber + "`";
        } else {
            return "`" + formattedNumber + " " + currency + "`";
        }
    }


    public String renderProductDetail(Item item, BusinessTelegramBot setting, Optional<BigDecimal> availableQuantity) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🏷️", item.getName()));
        sb.append("💵 តម្លៃទំនិញ ៖  ").append(formatPrice(item.getPrice(), setting)).append("\n");

        if (item.getItemGroup() != null) {
            sb.append("🗂️ ប្រភេទ    ៖  `").append(item.getItemGroup().getName()).append("`\n");
        }

        sb.append("📦 ស្ថានភាព   ៖  ").append(stockBadge(availableQuantity)).append("\n");

        if (StringUtils.hasText(item.getDescription())) {
            sb.append("\n📝 *ការពិពណ៌នា៖*\n");
            sb.append("_").append(item.getDescription().trim()).append("_\n");
        }

        sb.append(renderAttributes(item));

        sb.append(divider());
        sb.append("👇 *សូមជ្រើសរើសជម្រើសខាងក្រោម៖*");
        return sb.toString();
    }


    private String renderAttributes(Item item) {
        Map<String, Object> attributes = item.getAttributes();

        if (attributes == null || attributes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String value = renderAttributeValue(entry.getValue());

            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(value)) {
                continue;
            }

            sb.append("• ").append(escape(entry.getKey())).append(" ៖ `").append(value).append("`\n");
        }

        return sb.isEmpty() ? "" : "\n\uD83C\uDFF7\uFE0F *លក្ខណៈពិសេស៖*\n" + sb;
    }

    private String renderAttributeValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Iterable<?> many) {
            StringBuilder joined = new StringBuilder();

            for (Object element : many) {
                if (element == null || element instanceof Map || element instanceof Iterable) {
                    continue;
                }
                if (!joined.isEmpty()) {
                    joined.append(", ");
                }
                joined.append(String.valueOf(element).trim());
            }

            return escape(joined.toString());
        }

        if (value instanceof Map) {
            return "";
        }

        return escape(String.valueOf(value).trim());
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("`", "'").replace("*", "").replace("_", " ");
    }

    private String stockBadge(Optional<BigDecimal> availableQuantity) {
        if (availableQuantity.isEmpty()) {
            return "🟢 `មានទំនិញ`";
        }

        BigDecimal quantity = availableQuantity.get();
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return "🔴 `អស់ស្តុក (Out of Stock)`";
        }
        if (quantity.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return "🟡 `នៅសល់តិច — " + quantity.stripTrailingZeros().toPlainString() + " ទំនិញ`";
        }
        return "🟢 `មានទំនិញ`";
    }


    public String renderReceipt(Order order, Business business) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🧾", "វិក្កយបត្រ (RECEIPT)"));
        sb.append("🏪 ហាង        ៖ *").append(business.getDisplayName()).append("*\n");
        sb.append("🔢 លេខវិក្កយបត្រ ៖ `").append(order.getInvoiceNumber()).append("`\n");
        sb.append(divider());
        sb.append("📦 *បញ្ជីមុខទំនិញ៖*\n");

        int index = 1;
        for (OrderItem line : order.getItems()) {
            sb.append("*").append(index++).append(".* ").append(line.getItemName()).append("\n");
            sb.append(" └ ").append(line.getQuantity()).append(" x ")
                    .append(formatPrice(line.getUnitPrice(), business))
                    .append(" = ").append(formatPrice(line.getLineTotal(), business)).append("\n");
        }

        sb.append(divider());
        sb.append("💵 សរុបរង ៖ ").append(formatPrice(order.getSubtotal(), business)).append("\n");
        if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) {
            sb.append("🏷️ បញ្ចុះតម្លៃ ៖ ").append(formatPrice(order.getDiscountAmount(), business)).append("\n");
        }
        sb.append("💳 *សរុបទាំងអស់ ៖* ").append(formatPrice(order.getTotal(), business)).append("\n");
        sb.append(DIVIDER).append("\n");
        sb.append("✅ _អរគុណសម្រាប់ការទិញទំនិញ!_");

        return sb.toString();
    }


    public String renderCartReceipt(Cart cart, BusinessTelegramBot setting, String customerName) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🛒", "កន្ត្រកទំនិញរបស់អ្នក (YOUR CART)"));

        sb.append("👤 អតិថិជន ៖ *").append(customerName != null ? customerName : "អតិថិជនទូទៅ").append("*\n");
        sb.append("🏪 ហាង     ៖ *").append(setting.getBusiness().getDisplayName()).append("*\n\n");
        sb.append("📦 *បញ្ជីមុខទំនិញ៖*\n");

        int index = 1;
        for (CartItem ci : cart.getItems()) {
            Item item = ci.getItem();
            String label = ci.getVariant() != null
                    ? item.getName() + " (" + ci.getVariant().getVariantName() + ")"
                    : item.getName();
            sb.append("*").append(index++).append(".* ").append(label).append("\n");
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