package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.telegram.InlineKeyboardButton;
import kh.edu.istad.ite.features.social.telegram.TelegramUIHelper;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramCustomerScreenService {

    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(OrderStatus.PENDING);
    private static final Set<OrderStatus> PAST_STATUSES =
            Set.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.FAILED);

    private final OrderRepository orderRepository;
    private final TelegramUIHelper uiHelper;

    /** A rendered message plus the keyboard that belongs with it. */
    public record Screen(String text, List<List<InlineKeyboardButton>> keyboard) {
    }


    @Transactional(readOnly = true)
    public Screen activeOrders(BusinessTelegramBot setting, UUID customerId, int page) {
        Page<Order> orders = orderRepository.findForCustomerByStatuses(
                setting.getBusiness().getId(), customerId, ACTIVE_STATUSES,
                PageRequest.of(Math.max(0, page), PAGE_SIZE));

        if (orders.isEmpty()) {
            return new Screen(
                    uiHelper.header("📋", "ការបញ្ជាទិញកំពុងដំណើរការ")
                            + "😌 អ្នកគ្មានការបញ្ជាទិញណាកំពុងរង់ចាំទេ។",
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")),
                            List.of(new InlineKeyboardButton("🧾 ប្រវត្តិការទិញ", "menu:history")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
        }

        return new Screen(
                uiHelper.header("📋", "ការបញ្ជាទិញកំពុងដំណើរការ")
                        + "អ្នកមាន *" + orders.getTotalElements() + "* ការបញ្ជាទិញកំពុងរង់ចាំទូទាត់៖\n"
                        + "📑 ទំព័រទី " + (orders.getNumber() + 1) + "/" + orders.getTotalPages(),
                buildOrderKeyboard(orders, setting, "orders"));
    }

    @Transactional(readOnly = true)
    public Screen orderHistory(BusinessTelegramBot setting, UUID customerId, int page) {
        Page<Order> orders = orderRepository.findForCustomerByStatuses(
                setting.getBusiness().getId(), customerId, PAST_STATUSES,
                PageRequest.of(Math.max(0, page), PAGE_SIZE));

        if (orders.isEmpty()) {
            return new Screen(
                    uiHelper.header("🧾", "ប្រវត្តិការទិញ")
                            + "😌 អ្នកមិនទាន់មានប្រវត្តិការទិញនៅឡើយទេ។\n"
                            + "សូមចាប់ផ្តើមទិញទំនិញដំបូងរបស់អ្នក!",
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
        }

        long paidCount = orderRepository.countByBusinessIdAndCustomerIdAndStatus(
                setting.getBusiness().getId(), customerId, OrderStatus.PAID);

        return new Screen(
                uiHelper.header("🧾", "ប្រវត្តិការទិញ")
                        + "✅ ការទិញជោគជ័យ ៖ `" + paidCount + " ដង`\n"
                        + "📑 ទំព័រទី " + (orders.getNumber() + 1) + "/" + orders.getTotalPages()
                        + uiHelper.divider()
                        + "👇 ចុចលើវិក្កយបត្រណាមួយដើម្បីមើលលម្អិត៖",
                buildOrderKeyboard(orders, setting, "history"));
    }


    @Transactional(readOnly = true)
    public Screen orderDetail(BusinessTelegramBot setting, UUID customerId, UUID orderId, String backTo) {
        Order order = orderRepository
                .findDetailForCustomer(orderId, setting.getBusiness().getId(), customerId)
                .orElse(null);

        List<List<InlineKeyboardButton>> back = List.of(
                List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "menu:" + backTo)),
                List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        if (order == null) {
            return new Screen("⚠️ រកមិនឃើញការបញ្ជាទិញនេះទេ។", back);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(uiHelper.header("🧾", "វិក្កយបត្រលម្អិត"));
        sb.append("🔢 លេខវិក្កយបត្រ ៖ `").append(order.getInvoiceNumber()).append("`\n");
        sb.append("📅 កាលបរិច្ឆេទ ៖ `")
                .append(order.getCreatedDate() == null ? "—" : order.getCreatedDate().format(STAMP))
                .append("`\n");
        sb.append("📊 ស្ថានភាព ៖ ").append(statusLabel(order.getStatus())).append("\n");
        sb.append(uiHelper.divider());
        sb.append("📦 *បញ្ជីមុខទំនិញ៖*\n");

        int index = 1;
        for (OrderItem line : order.getItems()) {
            sb.append("*").append(index++).append(".* ").append(line.getItemName()).append("\n");
            sb.append(" └ ").append(line.getQuantity()).append(" x ")
                    .append(uiHelper.formatPrice(line.getUnitPrice(), setting))
                    .append(" = ").append(uiHelper.formatPrice(line.getLineTotal(), setting)).append("\n");
        }

        sb.append(uiHelper.divider());
        sb.append("💵 សរុបរង ៖ ").append(uiHelper.formatPrice(order.getSubtotal(), setting)).append("\n");

        if (order.getDiscountAmount() != null
                && order.getDiscountAmount().signum() > 0) {
            sb.append("🏷️ បញ្ចុះតម្លៃ ៖ ")
                    .append(uiHelper.formatPrice(order.getDiscountAmount(), setting)).append("\n");
        }

        sb.append("💳 *សរុបទាំងអស់ ៖* ").append(uiHelper.formatPrice(order.getTotal(), setting));

        // A still-pending order deserves a way back to paying for it.
        if (OrderStatus.PENDING.equals(order.getStatus())) {
            List<List<InlineKeyboardButton>> pendingKeyboard = new ArrayList<>();
            pendingKeyboard.add(List.of(new InlineKeyboardButton("💳 បង់ប្រាក់ឥឡូវ", "menu:checkout")));
            pendingKeyboard.add(List.of(
                    new InlineKeyboardButton("❌ បោះបង់", "order:cancel:" + order.getId())));
            pendingKeyboard.addAll(back);
            return new Screen(sb.toString(), pendingKeyboard);
        }

        return new Screen(sb.toString(), back);
    }

    public Screen storeLocation(BusinessTelegramBot setting) {
        Business business = setting.getBusiness();

        StringBuilder sb = new StringBuilder();
        sb.append(uiHelper.header("📍", "ទីតាំងហាង"));
        sb.append("🏪 ហាង ៖ *").append(business.getDisplayName()).append("*\n");

        if (StringUtils.hasText(business.getAddress())) {
            sb.append("🏠 អាសយដ្ឋាន ៖ ").append(business.getAddress()).append("\n");
        }
        if (StringUtils.hasText(business.getCityOrProvince())) {
            sb.append("🌆 ខេត្ត/ក្រុង ៖ ").append(business.getCityOrProvince()).append("\n");
        }
        if (StringUtils.hasText(business.getPhoneNumber())) {
            sb.append("📞 ទូរស័ព្ទ ៖ `").append(business.getPhoneNumber()).append("`\n");
        }

        boolean hasAnyDetail = StringUtils.hasText(business.getAddress())
                || StringUtils.hasText(business.getCityOrProvince())
                || StringUtils.hasText(business.getPhoneNumber());

        if (!hasAnyDetail) {
            sb.append("\n😔 ហាងមិនទាន់បញ្ចូលព័ត៌មានទីតាំងនៅឡើយទេ។");
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        String mapUrl = resolveMapUrl(business);
        if (mapUrl != null) {
            keyboard.add(List.of(InlineKeyboardButton.link("🗺️ បើកក្នុង Google Maps", mapUrl)));
        }
        if (StringUtils.hasText(business.getWebsite())) {
            keyboard.add(List.of(InlineKeyboardButton.link("🌐 គេហទំព័រ", business.getWebsite())));
        }

        keyboard.add(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        return new Screen(sb.toString(), keyboard);
    }

    private List<List<InlineKeyboardButton>> buildOrderKeyboard(
            Page<Order> orders, BusinessTelegramBot setting, String scope) {

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Order order : orders.getContent()) {
            String price = uiHelper.formatPrice(order.getTotal(), setting).replace("`", "");
            String label = statusIcon(order.getStatus()) + " " + order.getInvoiceNumber() + " — " + price;
            keyboard.add(List.of(new InlineKeyboardButton(label, "order:view:" + scope + ":" + order.getId())));
        }

        List<InlineKeyboardButton> paging = new ArrayList<>();
        if (orders.hasPrevious()) {
            paging.add(new InlineKeyboardButton("⬅️ ទំព័រមុន", scope + ":page:" + (orders.getNumber() - 1)));
        }
        if (orders.hasNext()) {
            paging.add(new InlineKeyboardButton("ទំព័របន្ទាប់ ➡️", scope + ":page:" + (orders.getNumber() + 1)));
        }
        if (!paging.isEmpty()) {
            keyboard.add(List.copyOf(paging));
        }

        keyboard.add(List.of("orders".equals(scope)
                ? new InlineKeyboardButton("🧾 ប្រវត្តិការទិញ", "menu:history")
                : new InlineKeyboardButton("📋 ការបញ្ជាទិញកំពុងដំណើរការ", "menu:orders")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        return keyboard;
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING -> "🟡 `រង់ចាំការទូទាត់`";
            case CONFIRMED -> "🟠 `បានបញ្ជាក់`";
            case PAID -> "🟢 `បានទូទាត់រួច`";
            case CANCELLED -> "⚪ `បានលុបចោល`";
            case FAILED -> "🔴 `បរាជ័យ`";
        };
    }

    private String statusIcon(OrderStatus status) {
        return switch (status) {
            case PENDING -> "🟡";
            case CONFIRMED -> "🟠";
            case PAID -> "🟢";
            case CANCELLED -> "⚪";
            case FAILED -> "🔴";
        };
    }

    private String resolveMapUrl(Business business) {
        if (StringUtils.hasText(business.getGoogleMap())) {
            return business.getGoogleMap().trim();
        }

        String address = StringUtils.hasText(business.getAddress()) ? business.getAddress() : "";
        String city = StringUtils.hasText(business.getCityOrProvince()) ? business.getCityOrProvince() : "";
        String query = (business.getDisplayName() + " " + address + " " + city).trim();

        if (!StringUtils.hasText(address) && !StringUtils.hasText(city)) {
            return null;
        }

        return "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}