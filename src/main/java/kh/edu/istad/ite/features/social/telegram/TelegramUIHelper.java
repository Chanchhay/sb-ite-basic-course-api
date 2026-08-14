package kh.edu.istad.ite.features.social.telegram;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.business.repository.BusinessCurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TelegramUIHelper {

    private static final String DIVIDER = "━━━━━━━━━━━━━━━━━━━━";

    private final BusinessCurrencyRepository businessCurrencyRepository;

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
        if (business == null) {
            return "`$" + price.setScale(2, RoundingMode.HALF_UP) + "`";
        }

        String baseCode = StringUtils.hasText(business.getBaseCurrency()) ? business.getBaseCurrency().trim().toUpperCase() : "USD";
        String displayCode = StringUtils.hasText(business.getDisplayCurrency()) ? business.getDisplayCurrency().trim().toUpperCase() : baseCode;

        BigDecimal amountToFormat = price;

        if (businessCurrencyRepository != null && !displayCode.equalsIgnoreCase(baseCode)) {
            Optional<BusinessCurrency> baseCurrOpt = businessCurrencyRepository.findByBusinessIdAndCodeIgnoreCase(business.getId(), baseCode);
            Optional<BusinessCurrency> dispCurrOpt = businessCurrencyRepository.findByBusinessIdAndCodeIgnoreCase(business.getId(), displayCode);

            if (baseCurrOpt.isPresent() && dispCurrOpt.isPresent()) {
                BigDecimal baseRate = baseCurrOpt.get().getExchangeRate();
                BigDecimal dispRate = dispCurrOpt.get().getExchangeRate();
                if (baseRate != null && dispRate != null && baseRate.compareTo(BigDecimal.ZERO) > 0 && dispRate.compareTo(BigDecimal.ZERO) > 0) {
                    amountToFormat = price.multiply(dispRate).divide(baseRate, 4, RoundingMode.HALF_UP);
                }
            }
        }

        int decimals = 2;
        String symbol = displayCode;

        if ("KHR".equalsIgnoreCase(displayCode) || "RIEL".equalsIgnoreCase(displayCode) || "៛".equals(displayCode)) {
            decimals = 0;
            symbol = "៛";
        } else if ("USD".equalsIgnoreCase(displayCode) || "$".equals(displayCode)) {
            decimals = 2;
            symbol = "$";
        }

        if (businessCurrencyRepository != null) {
            Optional<BusinessCurrency> currOpt = businessCurrencyRepository.findByBusinessIdAndCodeIgnoreCase(business.getId(), displayCode);
            if (currOpt.isPresent()) {
                BusinessCurrency curr = currOpt.get();
                if (curr.getDecimalPlaces() != null) {
                    decimals = curr.getDecimalPlaces();
                }
                if (StringUtils.hasText(curr.getSymbol())) {
                    symbol = curr.getSymbol();
                }
            }
        }

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        nf.setGroupingUsed(true);

        String formattedStr = nf.format(amountToFormat.setScale(decimals, RoundingMode.HALF_UP));

        if ("$".equals(symbol)) {
            return "`$" + formattedStr + "`";
        } else if ("៛".equals(symbol)) {
            return "`" + formattedStr + " ៛`";
        } else {
            return "`" + formattedStr + " " + symbol + "`";
        }
    }


    public String renderProductDetail(Item item, BusinessTelegramBot setting, Optional<BigDecimal> availableQuantity, kh.edu.istad.ite.features.discount.dto.DiscountResponse discount) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🏷️", item.getName()));
        
        if (discount != null) {
            BigDecimal discountAmount = BigDecimal.ZERO;
            if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.PERCENTAGE && discount.value() != null) {
                discountAmount = item.getPrice().multiply(discount.value()).divide(new BigDecimal("100"));
            } else if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.FIXED_AMOUNT && discount.value() != null) {
                discountAmount = discount.value();
            }
            BigDecimal newPrice = item.getPrice().subtract(discountAmount);
            if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                newPrice = BigDecimal.ZERO;
            }
            sb.append("💵 តម្លៃទំនិញ ៖  ").append(formatPrice(newPrice, setting))
              .append(" (បញ្ចុះពី ~").append(formatPrice(item.getPrice(), setting)).append("~)\n");
        } else {
            sb.append("💵 តម្លៃទំនិញ ៖  ").append(formatPrice(item.getPrice(), setting)).append("\n");
        }

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
        List<ItemAttribute> attributes = item.getAttributes();

        if (attributes == null || attributes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (ItemAttribute attr : attributes) {
            String value = "";
            if (attr.getValues() != null && !attr.getValues().isEmpty()) {
                value = attr.getValues().getFirst().getValue();
            }

            if (!StringUtils.hasText(attr.getName()) || !StringUtils.hasText(value)) {
                continue;
            }

            sb.append("• ").append(escape(attr.getName())).append(" ៖ `").append(value).append("`\n");
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
            if (line.getSelections() != null && !line.getSelections().isEmpty()) {
                for (var sel : line.getSelections()) {
                    sb.append(" ├ ⚙️ ").append(sel.getAttributeName()).append(": `").append(sel.display()).append("`\n");
                }
            }
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
        return renderCartReceipt(cart, setting, customerName, null);
    }

    public String renderCartReceipt(Cart cart, BusinessTelegramBot setting, String customerName, kh.edu.istad.ite.features.discount.service.DiscountService discountService) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("🛒", "កន្ត្រកទំនិញរបស់អ្នក (YOUR CART)"));

        sb.append("👤 អតិថិជន ៖ *").append(customerName != null ? customerName : "អតិថិជនទូទៅ").append("*\n");
        sb.append("🏪 ហាង     ៖ *").append(setting.getBusiness().getDisplayName()).append("*\n\n");
        sb.append("📦 *បញ្ជីមុខទំនិញ៖*\n");

        int index = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (CartItem ci : cart.getItems()) {
            Item item = ci.getItem();
            String label = ci.getVariant() != null
                    ? item.getName() + " (" + ci.getVariant().getVariantName() + ")"
                    : item.getName();
            sb.append("*").append(index++).append(".* ").append(label).append("\n");

            if (ci.getSelections() != null && !ci.getSelections().isEmpty()) {
                for (var sel : ci.getSelections()) {
                    sb.append(" ├ ⚙️ ").append(sel.getAttributeName()).append(": `").append(sel.display()).append("`\n");
                }
            }

            BigDecimal basePrice = ci.getPriceSnapshot() != null ? ci.getPriceSnapshot()
                    : (ci.getVariant() != null && ci.getVariant().getPrice() != null ? ci.getVariant().getPrice() : item.getPrice());
            int quantity = ci.getQuantity() == null ? 1 : ci.getQuantity();
            BigDecimal rawLineSubtotal = basePrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(rawLineSubtotal);

            BigDecimal lineDiscount = BigDecimal.ZERO;
            if (discountService != null && item != null) {
                UUID itemGroupId = item.getItemGroup() != null ? item.getItemGroup().getId() : null;
                List<kh.edu.istad.ite.features.discount.dto.DiscountResponse> applicable = discountService.findApplicableDiscounts(
                        setting.getBusiness().getId(),
                        kh.edu.istad.ite.shared.enums.OrderChannel.TELEGRAM,
                        item.getId(),
                        itemGroupId
                );
                if (!applicable.isEmpty()) {
                    kh.edu.istad.ite.features.discount.dto.DiscountResponse discount = applicable.get(0);
                    if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.PERCENTAGE && discount.value() != null) {
                        BigDecimal unitDiscount = basePrice.multiply(discount.value()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        lineDiscount = unitDiscount.multiply(BigDecimal.valueOf(quantity));
                    } else if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.FIXED_AMOUNT && discount.value() != null) {
                        lineDiscount = discount.value().multiply(BigDecimal.valueOf(quantity));
                    }
                    if (discount.maxDiscountAmount() != null && lineDiscount.compareTo(discount.maxDiscountAmount()) > 0) {
                        lineDiscount = discount.maxDiscountAmount();
                    }
                }
            }
            totalDiscount = totalDiscount.add(lineDiscount);

            sb.append(" └ ").append(quantity).append(" x ")
                    .append(formatPrice(basePrice, setting))
                    .append(" = ").append(formatPrice(rawLineSubtotal, setting)).append("\n");
        }

        BigDecimal grandTotal = subtotal.subtract(totalDiscount);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) grandTotal = BigDecimal.ZERO;

        sb.append(divider());
        sb.append("📊 ចំនួនទំនិញសរុប ៖  `").append(cart.getTotalItemsCount()).append(" មុខ`\n");
        sb.append("💵 សរុបរង (Subtotal) ៖  ").append(formatPrice(subtotal, setting)).append("\n");
        if (totalDiscount.signum() > 0) {
            sb.append("🏷️ បញ្ចុះតម្លៃ (Discount) ៖  -").append(formatPrice(totalDiscount, setting)).append("\n");
        }
        sb.append("💳 *ទឹកប្រាក់សរុប (Total) ៖*  ").append(formatPrice(grandTotal, setting)).append("\n");
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