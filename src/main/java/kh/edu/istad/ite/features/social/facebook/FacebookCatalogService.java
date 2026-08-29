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


import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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


    public void sendWelcomeMenu(BusinessFacebookPage page, String psid) {
        sendWelcomeMenu(page, psid, null);
    }

    /**
     * @param miniAppUrl when non-null (the business also has the Mini App
     *                   enabled), a web_url "Open Shop" button rides along in
     *                   the same message — otherwise the only place a
     *                   customer could ever find that button again after the
     *                   first "Get Started" is the persistent menu icon next
     *                   to the composer, which is easy to miss entirely.
     */
    public void sendWelcomeMenu(BusinessFacebookPage page, String psid, String miniAppUrl) {
        String storeName = page.getBusiness().getDisplayName();
        String text = "👋 សូមស្វាគមន៍មកកាន់ " + storeName + "!\n\nសូមចុចប៊ូតុងខាងក្រោម ដើម្បីមើល ឬស្វែងរកផលិតផលរបស់យើង។";

        List<Map<String, Object>> buttons = new ArrayList<>();
        if (StringUtils.hasText(miniAppUrl)) {
            Map<String, Object> shopButton = new java.util.HashMap<>();
            shopButton.put("type", "web_url");
            shopButton.put("title", "🛍 បើកហាង");
            shopButton.put("url", miniAppUrl);
            shopButton.put("webview_height_ratio", "tall");
            shopButton.put("messenger_extensions", true);
            buttons.add(shopButton);
        }
        buttons.add(Map.of("type", "postback", "title", "🗂️ មើលផលិតផល", "payload", "CATALOG"));
        // Messenger button templates cap out at 3 buttons.
        if (buttons.size() < 3) {
            buttons.add(Map.of("type", "postback", "title", "📂 ប្រភេទទំនិញ", "payload", "CATALOG_CATEGORIES"));
        }

        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);
    }

    public void showCatalog(BusinessFacebookPage page, String psid) {
        UUID businessId = page.getBusiness().getId();
        Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(ItemSpecifications.isEnabledInChannelCodes(List.of("MESSENGER")));

        Page<Item> itemsPage = itemRepository.findAll(spec, PageRequest.of(0, CATALOG_PAGE_SIZE));

        if (itemsPage.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនទាន់មានផលិតផលនៅឡើយទេ។");
            return;
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            elements.add(buildElement(item, page.getBusiness()));
        }
        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, elements);
    }

    public void showItemDetail(BusinessFacebookPage page, String psid, UUID itemId) {
        Optional<Item> found = itemRepository.findByIdAndBusinessId(itemId, page.getBusiness().getId());
        if (found.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 ផលិតផលនេះមិនមានទៀតទេ។");
            return;
        }
        Item item = found.get();
 //show item again with detail
        graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                buildDetailText(item, page.getBusiness()));
        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🛒 ថែមចូលកន្ត្រក", "payload", "CART_ADD:" + item.getId()),
                Map.of("type", "postback", "title", "🗂️ ត្រឡប់ទៅផលិតផល", "payload", "CATALOG")
        );
        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                "ចង់ធ្វើអ្វីបន្ត?", buttons);
    }

    private Map<String, Object> buildElement(Item item, Business business) {
        String subtitle = truncate(
                formatPrice(effectivePrice(item, business), business) + " · " + stockLabel(item, business),
                SUBTITLE_MAX);

        Optional<String> imageUrl = item.getImages().stream()
                .findFirst()
                .map(image -> minioService.getPublicUrl(image.getImageKey()))
                .or(() -> Optional.ofNullable(item.getImageUrl()))
                .filter(url -> url != null && url.startsWith("https://"));

        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🔍 លម្អិត", "payload", "ITEM:" + item.getId()),
                Map.of("type", "postback", "title", "🛒 ថែមចូលកន្ត្រក", "payload", "CART_ADD:" + item.getId())
        );

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("title", truncate(item.getName(), TITLE_MAX));
        element.put("subtitle", subtitle);
        imageUrl.ifPresent(url -> element.put("image_url", url));
        element.put("buttons", buttons);
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
        Optional<BigDecimal> availableQuantity = stockHelper.trackedAvailableQuantity(
                business.getId(), item, OrderChannel.MESSENGER);
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

    private final kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository itemGroupRepository;

    public void showCategories(BusinessFacebookPage page, String psid) {
        UUID businessId = page.getBusiness().getId();
        List<kh.edu.istad.ite.features.catalog.entity.ItemGroup> categories =
                itemGroupRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId);

        if (categories.isEmpty()) {
            categories = itemGroupRepository.findByBusinessIdAndParentIsNullOrderByNameAsc(businessId);
        }

        if (categories.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនទាន់មានប្រភេទទំនិញនៅឡើយទេ។");
            return;
        }

        List<Map<String, Object>> buttons = new ArrayList<>();
        for (kh.edu.istad.ite.features.catalog.entity.ItemGroup cat : categories) {
            if (buttons.size() >= 3) break; // Messenger button template max 3 buttons
            buttons.add(Map.of("type", "postback", "title", "📂 " + truncate(cat.getName(), 18),
                    "payload", "CATALOG_CAT:" + cat.getId()));
        }

        String text = "📂 សូមជ្រើសរើសប្រភេទទំនិញ ៖";
        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);
    }

    public void showCatalogByCategory(BusinessFacebookPage page, String psid, UUID categoryId) {
        UUID businessId = page.getBusiness().getId();
        Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(ItemSpecifications.hasItemGroupId(categoryId));

        Page<Item> itemsPage = itemRepository.findAll(spec, PageRequest.of(0, CATALOG_PAGE_SIZE));

        if (itemsPage.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនទាន់មានផលិតផលក្នុងប្រភេទទំនិញនេះនៅឡើយទេ។");
            return;
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            elements.add(buildElement(item, page.getBusiness()));
        }
        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, elements);
    }

    public void searchItems(BusinessFacebookPage page, String psid, String query) {
        UUID businessId = page.getBusiness().getId();
        Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(ItemSpecifications.nameContainsIgnoreCase(query));

        Page<Item> itemsPage = itemRepository.findAll(spec, PageRequest.of(0, CATALOG_PAGE_SIZE));

        if (itemsPage.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនមានផលិតផលត្រូវនឹងពាក្យស្វែងរក \"" + query + "\" ឡើយ។");
            return;
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            elements.add(buildElement(item, page.getBusiness()));
        }
        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, elements);
    }

    private String formatPrice(BigDecimal price, Business business) {
        if (price == null) {
            return "—";
        }
        BigDecimal usdPrice = price.setScale(2, RoundingMode.HALF_UP);
        BigDecimal khrPrice = price.multiply(BigDecimal.valueOf(4100)).setScale(0, RoundingMode.HALF_UP);

        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.US);
        String formattedKhr = nf.format(khrPrice);

        return "$" + usdPrice + " (" + formattedKhr + " ៛)";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
