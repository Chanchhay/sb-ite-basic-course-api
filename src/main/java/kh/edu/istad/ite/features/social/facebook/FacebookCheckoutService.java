package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.social.service.TelegramStockHelper;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.entity.Sale;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.features.payment.bakong.BakongCheckResult;
import kh.edu.istad.ite.features.payment.bakong.BakongTransactionClient;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.khqr.KhqrGenerator;
import kh.edu.istad.ite.features.payment.khqr.QrImageRenderer;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import kh.edu.istad.ite.features.payment.service.ReceiptService;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.event.FacebookQrGeneratedEvent;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.QrStatus;
import kh.edu.istad.ite.shared.enums.ReceiptType;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FacebookCheckoutService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 2;
    private static final int QR_IMAGE_SIZE = 512;
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final SaleRepository saleRepository;
    private final BusinessPaymentSettingRepository paymentSettingRepository;
    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final KhqrGenerator khqrGenerator;
    private final QrImageRenderer qrImageRenderer;
    private final BakongTransactionClient bakongTransactionClient;
    private final CredentialCipher credentialCipher;
    private final StockEntryService stockEntryService;

    private final ItemChannelStockService itemChannelStockService;
    private final TelegramStockHelper stockHelper;
    private final ReceiptService receiptService;
    private final ApplicationEventPublisher eventPublisher;
    private final FacebookGraphClient graphClient;
    private final StorefrontProps storefrontProps;
    private final kh.edu.istad.ite.features.business.service.TaxCalculator taxCalculator;
    private final kh.edu.istad.ite.features.notification.push.PushNotificationClient pushNotificationClient;

    public record CheckoutDraft(
            java.util.UUID orderId,
            String invoiceNumber,
            java.math.BigDecimal total,
            String currency,
            int itemCount,
            String qrPayload,
            String md5,
            byte[] qrPng,
            java.time.LocalDateTime expiresAt,
            java.util.UUID qrCodeId,
            String bakongDeepLink
    ) {}

    public record VerifyResult(
            boolean paid,
            boolean expired,
            String message,
            String invoiceNumber,
            String receiptText
    ) {}

    /** Everything a Messenger checkout needs regardless of how it's paid: the
     * business, its cart validated against stock, and a saved PENDING order. */
    private record DraftOrder(Business business, Order order, int itemCount) {}

    private DraftOrder buildOrder(UUID businessId, UUID customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("⚠️ រកមិនឃើញព័ត៌មានហាងទេ។"));

        Customer customer = customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new RuntimeException(
                        "⚠️ រកមិនឃើញគណនីអតិថិជននេះទេ។ សូមចុច /start ម្តងទៀត។"));

        Cart cart = cartRepository
                .findActiveCartWithItems(customerId, businessId, CartStatus.ACTIVE)
                .filter(candidate -> !candidate.getItems().isEmpty())
                .orElseThrow(() -> new RuntimeException(
                        "⚠️ កន្ត្រកទំនិញរបស់អ្នកទទេស្អាត។ សូមជ្រើសរើសទំនិញជាមុនសិន។"));

        String currency = resolveCurrency(business);
        int scale = scaleFor(currency);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            // Checked here, where the customer can still change the basket:
            // Messenger settles after the QR is paid, and refusing a line then
            // would be refusing an order that has already been paid for. The
            // channel is part of the question — this shop may have given
            // Messenger only some of what is on the shelf.
            if (!stockHelper.hasEnoughStock(
                    businessId,
                    cartItem.getItem(),
                    cartItem.getVariant(),
                    cartItem.baseQuantity(),
                    OrderChannel.MESSENGER)) {
                throw new RuntimeException(
                        "⚠️ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ \"" + cartItem.getItem().getName() + "\" ទេ។");
            }

            ItemVariant variant = cartItem.getVariant();
            BigDecimal price = cartItem.getPriceSnapshot() != null
                    ? cartItem.getPriceSnapshot()
                    : (variant != null && variant.getPrice() != null ? variant.getPrice()
                            : cartItem.getItem().getPrice());
            if (price == null) {
                throw new RuntimeException("⚠️ ទំនិញខ្លះមិនទាន់មានតម្លៃទេ។");
            }
            int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        Order order = new Order();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setInvoiceNumber(nextInvoiceNumber(businessId));
        order.setCurrency(currency);
        order.setChannel(OrderChannel.MESSENGER);
        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP));
        BigDecimal netAmount = order.getSubtotal().subtract(order.getDiscountAmount());
        kh.edu.istad.ite.features.business.service.TaxCalculator.Result taxResult =
                taxCalculator.apply(business, netAmount, scale);
        order.setTaxInclusionType(taxResult.inclusionType());
        order.setTaxRate(taxResult.taxRate());
        order.setTaxAmount(taxResult.taxAmount());
        order.setTotal(taxResult.total());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(this::toOrderItem)
                .toList();

        orderItems.forEach(order::addItem);
        orderRepository.save(order);

        pushNotificationClient.notifyOwner(
                business.getKeycloakUserId(),
                "New order from Messenger",
                "Order " + order.getInvoiceNumber() + " — " + order.getTotal() + " " + order.getCurrency(),
                "/sales/orders",
                "channel-order");

        return new DraftOrder(business, order, orderItems.size());
    }

    @Transactional
    public CheckoutDraft createCheckout(UUID businessId, UUID customerId) {
        requireKhqrFeature(businessId);

        DraftOrder draft = buildOrder(businessId, customerId);
        Business business = draft.business();
        Order order = draft.order();
        String currency = order.getCurrency();

        BusinessPaymentSetting setting = requireActiveBakongSetting(businessId);

        LocalDateTime expiresAtLocal = LocalDateTime.now().plusMinutes(QR_VALIDITY_MINUTES);
        Instant expiresAtInstant = expiresAtLocal.atZone(ZoneId.systemDefault()).toInstant();

        KhqrGenerator.Result result = khqrGenerator.generate(
                setting,
                order.getTotal(),
                currency,
                order.getInvoiceNumber(),
                "FB Messenger",
                expiresAtInstant);

        PaymentQrCode qrCode = new PaymentQrCode();
        qrCode.setOrder(order);
        qrCode.setBusiness(business);
        qrCode.setCurrency(currency);
        qrCode.setAmount(order.getTotal());
        qrCode.setMd5Hash(result.md5());
        qrCode.setQrPayload(result.qr());
        qrCode.setStatus(QrStatus.PENDING);
        qrCode.setCreatedAt(LocalDateTime.now());

        qrCode.setExpiresAt(expiresAtLocal);
        paymentQrCodeRepository.save(qrCode);

        byte[] png = qrImageRenderer.toPngBytes(result.qr(), QR_IMAGE_SIZE);
        String deepLink = resolveBakongDeepLink(business, setting, result.qr());

        eventPublisher.publishEvent(new FacebookQrGeneratedEvent(qrCode.getId()));

        return new CheckoutDraft(
                order.getId(),
                order.getInvoiceNumber(),
                order.getTotal(),
                currency,
                draft.itemCount(),
                result.qr(),
                result.md5(),
                png,
                expiresAtLocal,
                qrCode.getId(),
                deepLink
        );
    }

    /**
     * A Pay Later checkout never touches Bakong — it just parks the order at
     * PENDING with {@code awaitingPayLaterApproval} set, exactly like the web
     * storefront's Pay Later path, so the business owner approves it from the
     * same dashboard screen regardless of which channel it came from.
     */
    @Transactional
    public CheckoutDraft createPayLaterCheckout(UUID businessId, UUID customerId) {
        DraftOrder draft = buildOrder(businessId, customerId);
        Order order = draft.order();
        order.setAwaitingPayLaterApproval(true);
        orderRepository.save(order);

        return new CheckoutDraft(
                order.getId(),
                order.getInvoiceNumber(),
                order.getTotal(),
                order.getCurrency(),
                draft.itemCount(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Turns the KHQR payload into a real link the Bakong app (and other
     * KHQR-participating bank apps) will open directly, via Bakong's own
     * generate_deeplink_by_qr endpoint — the same call the storefront's
     * public order-status endpoint makes for the Telegram/web checkout page.
     */
    private String resolveBakongDeepLink(Business business, BusinessPaymentSetting setting, String qrPayload) {
        if (!StringUtils.hasText(setting.getApiTokenEncrypted())) {
            return null;
        }

        String accessToken;
        try {
            accessToken = credentialCipher.decrypt(setting.getApiTokenEncrypted());
        } catch (Exception exception) {
            log.warn("Could not decrypt Bakong API token for business {} — no Messenger deep link this time: {}",
                    business.getId(), exception.getMessage());
            return null;
        }

        String appName = StringUtils.hasText(business.getDisplayName()) ? business.getDisplayName() : "iPOS";
        return bakongTransactionClient.generateDeeplinkByQr(accessToken, qrPayload, null, appName).orElse(null);
    }

    private OrderItem toOrderItem(CartItem cartItem) {
        Item item = cartItem.getItem();
        ItemVariant variant = cartItem.getVariant();

        BigDecimal unitPrice = cartItem.getPriceSnapshot() != null
                ? cartItem.getPriceSnapshot()
                : (variant != null && variant.getPrice() != null ? variant.getPrice() : item.getPrice());

        if (unitPrice == null) {
            throw new RuntimeException(
                    "⚠️ ទំនិញ \"" + item.getName() + "\" មិនទាន់មានតម្លៃទេ។ សូមទាក់ទងហាង។");
        }

        int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();

        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setVariant(variant);
        orderItem.setItemName(variant != null ? item.getName() + " (" + variant.getVariantName() + ")" : item.getName());
        orderItem.setQuantity(quantity);
        orderItem.setUnitPrice(unitPrice);
        orderItem.setUnitCost(BigDecimal.ZERO);
        orderItem.setDiscountAmount(BigDecimal.ZERO);
        orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));

        return orderItem;
    }

    private void requireKhqrFeature(UUID businessId) {
        try {
            businessHelper.requireFeature(businessId, BusinessFeature.KHQR_PAYMENT);
        } catch (ResponseStatusException exception) {
            throw new RuntimeException(
                    "⚠️ មុខងារទូទាត់តាម KHQR ត្រូវបានបិទសម្រាប់ហាងនេះ។ សូមទាក់ទងហាងដោយផ្ទាល់។", exception);
        }
    }

    private BusinessPaymentSetting requireActiveBakongSetting(UUID businessId) {
        BusinessPaymentSetting setting = paymentSettingRepository.findByBusiness_Id(businessId)
                .orElseThrow(() -> new RuntimeException(
                        "⚠️ ហាងនេះមិនទាន់កំណត់គណនី Bakong ទេ។ សូមទាក់ទងហាងដើម្បីទូទាត់តាមវិធីផ្សេង។"));

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            throw new RuntimeException(
                    "⚠️ ការទូទាត់តាម Bakong មិនទាន់បើកដំណើរការសម្រាប់ហាងនេះទេ។");
        }

        return setting;
    }

    private String resolveCurrency(Business business) {
        if (StringUtils.hasText(business.getBaseCurrency())) {
            return business.getBaseCurrency().trim().toUpperCase();
        }
        return "USD";
    }

    private int scaleFor(String currency) {
        return CURRENCY_KHR.equalsIgnoreCase(currency) ? 0 : 2;
    }

    private String nextInvoiceNumber(UUID businessId) {
        String datePart = LocalDateTime.now().format(INVOICE_DATE);
        long sequence = orderRepository.countByBusinessId(businessId) + 1;
        return "INV-" + datePart + "-" + String.format("%05d", sequence);
    }

    @Transactional
    public VerifyResult verifyAndSettle(UUID businessId, UUID orderId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("⚠️ រកមិនឃើញព័ត៌មានហាងទេ។"));

        Order order = orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new RuntimeException("⚠️ រកមិនឃើញការបញ្ជាទិញនេះទេ។"));

        if (OrderStatus.PAID.equals(order.getStatus())) {
            return new VerifyResult(true, false, "ការបញ្ជាទិញនេះបានទូទាត់រួចហើយ។", order.getInvoiceNumber(), null);
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            return new VerifyResult(false, false, "ការបញ្ជាទិញនេះត្រូវបានលុបចោល។", order.getInvoiceNumber(), null);
        }

        PaymentQrCode qrCode = paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .filter(qr -> QrStatus.PENDING.equals(qr.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "⚠️ គ្មានកូដ KHQR ដែលកំពុងរង់ចាំទេ។ សូមចុច គិតលុយ ម្តងទៀត។"));

        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            qrCode.setStatus(QrStatus.EXPIRED);
            paymentQrCodeRepository.save(qrCode);
            return new VerifyResult(false, true,
                    "⏰ កូដ KHQR បានផុតកំណត់ហើយ។ សូមចុច គិតលុយ ដើម្បីបង្កើតកូដថ្មី។",
                    order.getInvoiceNumber(), null);
        }

        BusinessPaymentSetting setting = requireActiveBakongSetting(businessId);

        if (!StringUtils.hasText(setting.getApiTokenEncrypted())) {
            throw new RuntimeException(
                    "⚠️ ហាងមិនទាន់រក្សាទុក Bakong API token ទេ ដូច្នេះមិនអាចផ្ទៀងផ្ទាត់ការទូទាត់ដោយស្វ័យប្រវត្តិបានឡើយ។ "
                            + "សូមទាក់ទងហាងដោយផ្ទាល់។");
        }

        BakongCheckResult result = bakongTransactionClient.checkByMd5(
                credentialCipher.decrypt(setting.getApiTokenEncrypted()),
                qrCode.getMd5Hash());

        if (!result.paid()) {
            return new VerifyResult(false, false,
                    "❌ ប្រព័ន្ធ Bakong មិនទាន់ឃើញការទូទាត់ទេ។",
                    order.getInvoiceNumber(), null);
        }

        qrCode.setStatus(QrStatus.PAID);
        qrCode.setPaidAt(LocalDateTime.now());

        settle(business, order);
        closeCart(order);

        String receiptText = renderReceipt(order, business);

        log.info("Messenger order {} ({}) confirmed paid by Bakong, hash {}",
                order.getId(), order.getInvoiceNumber(), result.hash());

        return new VerifyResult(true, false, "ការទូទាត់ត្រូវបានបញ្ជាក់ដោយ Bakong។", order.getInvoiceNumber(), receiptText);
    }

    @Transactional
    public void cancelCheckout(UUID businessId, UUID orderId) {
        orderRepository.findByIdAndBusinessId(orderId, businessId).ifPresent(order -> {
            if (OrderStatus.PAID.equals(order.getStatus())) {
                return;
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                    .forEach(qr -> {
                        if (QrStatus.PENDING.equals(qr.getStatus())) {
                            qr.setStatus(QrStatus.CANCELLED);
                        }
                    });
        });
    }

    @Transactional
    public void updateQrMessageId(UUID qrCodeId, Integer messageId) {
        paymentQrCodeRepository.findById(qrCodeId).ifPresent(qr -> {
            qr.setTelegramMessageId(messageId);
            paymentQrCodeRepository.save(qr);
        });
    }

    private void settle(Business business, Order order) {
        BigDecimal totalCost = BigDecimal.ZERO;
        int itemCount = 0;

        for (OrderItem line : order.getItems()) {
            // Costed from the batches the sale emptied, not from what is left.
            BigDecimal unitCost = stockEntryService.recordSale(
                    business,
                    line.getItem(),
                    line.getVariant(),
                    // A case of twenty-four takes twenty-four off the shelf;
                    // the ledger still reads back as the one case that sold.
                    line.baseQuantity(),
                    BigDecimal.valueOf(line.getQuantity()),
                    line.getUnit(),
                    order.getId(),
                    order.getInvoiceNumber()
            ).getUnitCost();

            if (unitCost == null) {
                unitCost = BigDecimal.ZERO;
            }

            line.setUnitCost(unitCost.setScale(2, RoundingMode.HALF_UP));

            // The sale uses up the channel's share of the shelf as well as the
            // shelf itself. Does nothing for an item whose stock is shared,
            // which is most of them.
            itemChannelStockService.consume(
                    line.getItem(),
                    line.getVariant(),
                    order.getChannel(),
                    line.baseQuantity());

            // Times the base quantity, not the quantity rung up. `unitCost`
            // is what one *base* unit cost — it came back from the movement
            // that took `baseQuantity()` off the shelf — so a case of
            // twenty-four costed at the price of one unit understated this
            // sale by a factor of twenty-four, and flattered the margin by the
            // same.
            totalCost = totalCost.add(unitCost.multiply(line.baseQuantity()));
            itemCount += line.getQuantity();
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        int scale = scaleFor(order.getCurrency());

        Sale sale = new Sale();
        sale.setBusiness(business);
        sale.setOrder(order);
        sale.setCustomer(order.getCustomer());
        sale.setInvoiceNumber(order.getInvoiceNumber());
        sale.setCashierId(null);
        sale.setChannel(order.getChannel());
        sale.setSubtotal(order.getSubtotal());
        sale.setDiscountAmount(order.getDiscountAmount());
        sale.setTotalAmount(order.getTotal());
        sale.setPaidAmount(order.getTotal());
        sale.setChangeAmount(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP));
        sale.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        sale.setCurrency(order.getCurrency());
        sale.setPaymentMethod(PaymentMethodType.DIGITAL);
        sale.setItemCount(itemCount);
        sale.setNote("Paid via Bakong KHQR on Messenger");
        sale.setSoldAt(LocalDateTime.now());

        saleRepository.save(sale);

        receiptService.createForOrder(business, order, ReceiptType.DIGITAL);
    }

    private void closeCart(Order order) {
        if (order.getCustomer() == null) {
            return;
        }
        cartRepository
                .findActiveCartWithItems(
                        order.getCustomer().getId(),
                        order.getBusiness().getId(),
                        CartStatus.ACTIVE)
                .ifPresent(cart -> {
                    cart.setStatus(CartStatus.CHECKED_OUT);
                    cartRepository.save(cart);
                });
    }

    private String renderReceipt(Order order, Business business) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 វិក្កយបត្រ (RECEIPT)\n");
        sb.append("🏪 ហាង៖ ").append(business.getDisplayName()).append("\n");
        sb.append("🔖 លេខវិក្កយបត្រ៖ ").append(order.getInvoiceNumber()).append("\n");
        sb.append("----------------------------\n");
        sb.append("🛒 បញ្ជីទំនិញ៖\n");

        int index = 1;
        for (OrderItem line : order.getItems()) {
            sb.append(index++).append(". ").append(line.getItemName()).append("\n");
            sb.append("   ").append(line.getQuantity()).append(" x ")
                    .append(line.getUnitPrice()).append(" ").append(order.getCurrency())
                    .append(" = ").append(line.getLineTotal()).append(" ").append(order.getCurrency()).append("\n");
        }

        sb.append("----------------------------\n");
        sb.append("សរុបរង៖ ").append(order.getSubtotal()).append(" ").append(order.getCurrency()).append("\n");
        if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) {
            sb.append("បញ្ចុះតម្លៃ៖ ").append(order.getDiscountAmount()).append(" ").append(order.getCurrency()).append("\n");
        }
        if (order.getTaxAmount() != null && order.getTaxAmount().signum() > 0) {
            String taxLabel = business.getTaxLabel() != null && !business.getTaxLabel().isBlank()
                    ? business.getTaxLabel() : "ពន្ធ (Tax)";
            sb.append(taxLabel).append("៖ ").append(order.getTaxAmount()).append(" ").append(order.getCurrency()).append("\n");
        }
        sb.append("សរុបប្រាក់ត្រូវបង់៖ ").append(order.getTotal()).append(" ").append(order.getCurrency()).append("\n");
        sb.append("----------------------------\n");
        sb.append("អរគុណសម្រាប់ការគាំទ្រ!");

        return sb.toString();
    }

    public void handleCheckout(kh.edu.istad.ite.features.social.entity.BusinessFacebookPage page, kh.edu.istad.ite.features.social.entity.BotSession session, String psid) {
        try {
            CheckoutDraft draft = createCheckout(page.getBusiness().getId(), session.getCustomer().getId());

            // The QR goes straight into the chat — same as Telegram — instead of
            // making the customer open a webview page first just to see it.
            graphClient.sendImage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, draft.qrPng());

            String text = "✅ ការបញ្ជាទិញបានបង្កើតដោយជោគជ័យ!\n\n" +
                          "វិក្កយបត្រ៖ " + draft.invoiceNumber() + "\n" +
                          "ចំនួនទំនិញ៖ " + draft.itemCount() + "\n" +
                          "សរុបទឹកប្រាក់៖ $" + draft.total().setScale(2) + "\n\n" +
                          "📲 សូមស្កែន QR ខាងលើជាមួយ App ធនាគារ ឬចុច \"Open Bakong App\" ខាងក្រោម៖";

            List<Map<String, Object>> buttons = new java.util.ArrayList<>();

            if (StringUtils.hasText(draft.bakongDeepLink())) {
                Map<String, Object> deepLinkButton = new java.util.HashMap<>();
                deepLinkButton.put("type", "web_url");
                deepLinkButton.put("url", draft.bakongDeepLink());
                deepLinkButton.put("title", "🏦 Open Bakong App");
                buttons.add(deepLinkButton);
            }

            buttons.add(Map.of(
                    "type", "postback",
                    "title", "❌ បោះបង់ការបញ្ជាទិញ",
                    "payload", "ORDER_CANCEL:" + draft.orderId()
            ));

            graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);

        } catch (RuntimeException e) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, "⚠️ " + e.getMessage());
        }
    }

    /** Asked once at "Checkout", before either payment path runs — mirrors the
     * choice the web storefront gives (KHQR now vs. Pay Later, approved by
     * the business afterward), which the Messenger bot never offered before. */
    public void promptPaymentMethod(kh.edu.istad.ite.features.social.entity.BusinessFacebookPage page, String psid) {
        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "💳 ទូទាត់ឥឡូវ (KHQR)", "payload", "CHECKOUT_KHQR"),
                Map.of("type", "postback", "title", "🕒 បង់ប្រាក់ពេលក្រោយ (Pay Later)", "payload", "CHECKOUT_PAY_LATER")
        );

        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                "តើអ្នកចង់ទូទាត់ប្រាក់តាមរបៀបណា?", buttons);
    }

    public void handlePayLaterCheckout(kh.edu.istad.ite.features.social.entity.BusinessFacebookPage page, kh.edu.istad.ite.features.social.entity.BotSession session, String psid) {
        try {
            CheckoutDraft draft = createPayLaterCheckout(page.getBusiness().getId(), session.getCustomer().getId());

            String text = "✅ ការបញ្ជាទិញត្រូវបានកត់ត្រាដោយជោគជ័យ!\n\n" +
                          "វិក្កយបត្រ៖ " + draft.invoiceNumber() + "\n" +
                          "ចំនួនទំនិញ៖ " + draft.itemCount() + "\n" +
                          "សរុបទឹកប្រាក់៖ $" + draft.total().setScale(2) + "\n\n" +
                          "🕒 ការបញ្ជាទិញនេះកំពុងរង់ចាំការអនុម័តពីហាង។ អ្នកនឹងបង់ប្រាក់នៅពេលទទួលទំនិញ។";

            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text);
        } catch (RuntimeException e) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, "⚠️ " + e.getMessage());
        }
    }
}



