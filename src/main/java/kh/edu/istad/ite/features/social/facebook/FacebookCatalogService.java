package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.specification.ItemSpecifications;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.service.TelegramStockHelper;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Renders items (image, title, price, detail) for the Messenger bot,
 * mirroring what TelegramWebhookServiceImpl/TelegramUIHelper does for Telegram,
 * but using Messenger's Generic Template card instead of Markdown captions.
 */
@Service
@RequiredArgsConstructor
public class FacebookCatalogService {

    // Messenger Generic Template hard limits.
    private static final int TITLE_MAX = 80;
    private static final int SUBTITLE_MAX = 80;
    private static final int CATALOG_PAGE_SIZE = 8; // template allows up to 10 elements per message

    private final ItemRepository itemRepository;
    private final MinioService minioService;
    private final DiscountService discountService;
    private final TelegramStockHelper stockHelper;
    private final FacebookGraphClient graphClient;

    /**
     * Sends the welcome/main-menu message — this is what fires when the customer taps
     * "Get Started" or opens the chat for the first time. Equivalent of Telegram's
     * sendMainMenu() on /start, but as a Button Template instead of an inline keyboard.
     */
    public void sendWelcomeMenu(BusinessFacebookPage page, String psid) {
        String storeName = page.getBusiness().getDisplayName();
        String text = "👋 សូមស្វាគមន៍មកកាន់ " + storeName + "!\n\nសូមចុចប៊ូតុងខាងក្រោម ដើម្បីមើលផលិតផលរបស់យើង។";

        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🗂️ មើលផលិតផល", "payload", "CATALOG")
        );

        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);
    }

    /** Sends a carousel of items (image + title + price) the customer can tap to view detail. */
    public void showCatalog(BusinessFacebookPage page, String psid) {
        UUID businessId = page.getBusiness().getId();
        Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(ItemSpecifications.isEnabledInChannelCodes(List.of("MESSENGER")));
//
//                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE));
        Page<Item> itemsPage = itemRepository.findAll(spec, PageRequest.of(0, CATALOG_PAGE_SIZE));

        if (itemsPage.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនទាន់មានផលិតផលនៅឡើយទេ។");
            return;
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            elements.add(buildElement(item, page.getBusiness(), true));
        }
        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, elements);
    }

    /** Sends one item as a card (image + title + price), then a follow-up text with the full detail. */
    public void showItemDetail(BusinessFacebookPage page, String psid, UUID itemId) {
        Optional<Item> found = itemRepository.findByIdAndBusinessId(itemId, page.getBusiness().getId());
        if (found.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 ផលិតផលនេះមិនមានទៀតទេ។");
            return;
        }
        Item item = found.get();

        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                List.of(buildElement(item, page.getBusiness(), false)));

        graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                buildDetailText(item, page.getBusiness()));
    }

    /** image_url + title + subtitle(price · stock) + button, for a catalog card or a single-item card. */
    private Map<String, Object> buildElement(Item item, Business business, boolean forCatalogList) {
        String subtitle = truncate(
                formatPrice(effectivePrice(item, business), business) + " · " + stockLabel(item, business),
                SUBTITLE_MAX);

        Optional<String> imageUrl = item.getImages().stream()
                .findFirst()
                .map(image -> minioService.getPublicUrl(image.getImageKey()))
                .or(() -> Optional.ofNullable(item.getImageUrl()));

        Map<String, Object> button = forCatalogList
                ? Map.of("type", "postback", "title", "🔍 មើលលម្អិត", "payload", "ITEM:" + item.getId())
                : Map.of("type", "postback", "title", "🛒 ថែមចូលកន្ត្រក", "payload", "CART_ADD:" + item.getId());

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("title", truncate(item.getName(), TITLE_MAX));
        element.put("subtitle", subtitle);
        imageUrl.ifPresent(url -> element.put("image_url", url));
        element.put("buttons", List.of(button));
        return element;
    }

    /** Full detail text (description + attributes) - Messenger plain text, no Markdown. */
    private String buildDetailText(Item item, Business business) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏷️ ").append(item.getName()).append("\n");
        sb.append("💵 តម្លៃ ៖ ").append(formatPrice(effectivePrice(item, business), business)).append("\n");
        sb.append("📦 ស្ថានភាព ៖ ").append(stockLabel(item, business)).append("\n");

        if (item.getItemGroup() != null) {
            sb.append("🗂️ ប្រភេទ ៖ ").append(item.getItemGroup().getName()).append("\n");
        }

        if (StringUtils.hasText(item.getDescription())) {
            sb.append("\n📝 ការពិពណ៌នា៖\n").append(item.getDescription().trim()).append("\n");
        }

        List<ItemAttribute> attributes = item.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            StringBuilder attrLines = new StringBuilder();
            for (ItemAttribute attr : attributes) {
                String value = (attr.getValues() != null && !attr.getValues().isEmpty())
                        ? attr.getValues().getFirst().getValue()
                        : null;
                if (StringUtils.hasText(attr.getName()) && StringUtils.hasText(value)) {
                    attrLines.append("• ").append(attr.getName()).append(" ៖ ").append(value).append("\n");
                }
            }
            if (!attrLines.isEmpty()) {
                sb.append("\n🏷️ លក្ខណៈពិសេស៖\n").append(attrLines);
            }
        }

        return sb.toString().trim();
    }

    private BigDecimal effectivePrice(Item item, Business business) {
        List<DiscountResponse> applicable = discountService.findApplicableDiscounts(
                business.getId(),
                OrderChannel.MESSENGER,
                item.getId(),
                item.getItemGroup() != null ? item.getItemGroup().getId() : null);

        if (applicable.isEmpty()) {
            return item.getPrice();
        }

        DiscountResponse discount = applicable.get(0);
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discount.type() == DiscountType.PERCENTAGE && discount.value() != null) {
            discountAmount = item.getPrice().multiply(discount.value()).divide(BigDecimal.valueOf(100));
        } else if (discount.type() == DiscountType.FIXED_AMOUNT && discount.value() != null) {
            discountAmount = discount.value();
        }
        BigDecimal discounted = item.getPrice().subtract(discountAmount);
        return discounted.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : discounted;
    }

    private String stockLabel(Item item, Business business) {
        Optional<BigDecimal> availableQuantity = stockHelper.trackedAvailableQuantity(business.getId(), item);
        if (availableQuantity.isEmpty()) {
            return "🟢 មានទំនិញ";
        }
        BigDecimal qty = availableQuantity.get();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return "🔴 អស់ស្តុក";
        }
        if (qty.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return "🟡 នៅសល់តិច (" + qty.stripTrailingZeros().toPlainString() + ")";
        }
        return "🟢 មានទំនិញ";
    }

    private String formatPrice(BigDecimal price, Business business) {
        if (price == null) {
            return "—";
        }
        String currency = business.getDisplayCurrency() != null ? business.getDisplayCurrency() : business.getBaseCurrency();
        String formattedNumber = price.setScale(2, RoundingMode.HALF_UP).toString();
        return ("USD".equalsIgnoreCase(currency) || "$".equals(currency))
                ? "$" + formattedNumber
                : formattedNumber + " " + currency;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
