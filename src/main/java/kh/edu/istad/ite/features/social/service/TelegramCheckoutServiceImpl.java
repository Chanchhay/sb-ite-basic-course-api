package kh.edu.istad.ite.features.social.service;

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
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
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
import kh.edu.istad.ite.features.social.telegram.TelegramUIHelper;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.features.social.event.TelegramQrGeneratedEvent;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramCheckoutServiceImpl implements TelegramCheckoutService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 5;
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
    private final ReceiptService receiptService;
    private final TelegramStockHelper stockHelper;
    private final TelegramUIHelper uiHelper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CheckoutDraft createCheckout(UUID businessId, UUID customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new TelegramCheckoutException("⚠️ រកមិនឃើញព័ត៌មានហាងទេ។"));

        requireKhqrFeature(businessId);

        Customer customer = customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new TelegramCheckoutException(
                        "⚠️ គណនីរបស់អ្នកមិនត្រូវបានភ្ជាប់ជាមួយហាងនេះទេ។ សូមចូលគណនីម្តងទៀត។"));

        Cart cart = cartRepository
                .findActiveCartWithItems(customerId, businessId, CartStatus.ACTIVE)
                .filter(candidate -> !candidate.getItems().isEmpty())
                .orElseThrow(() -> new TelegramCheckoutException(
                        "⚠️ កន្ត្រកទំនិញរបស់អ្នកទទេ។ សូមជ្រើសរើសទំនិញជាមុនសិន។"));

        BusinessPaymentSetting setting = requireActiveBakongSetting(businessId);

        // Never let a customer pay for something we can't actually fulfil: check stock
        // BEFORE a KHQR is generated, not after Bakong confirms the money moved.
        for (CartItem cartItem : cart.getItems()) {
            int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();
            if (!stockHelper.hasEnoughStock(businessId, cartItem.getItem(), quantity)) {
                throw new TelegramCheckoutException(
                        "⚠️ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ \"" + cartItem.getItem().getName()
                                + "\" ទេ។ សូមកែសម្រួលកន្ត្រកទំនិញរបស់អ្នកមុននឹងទូទាត់ប្រាក់។");
            }
        }

        // 1. Build a real PENDING order from the cart.
        Order order = new Order();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setChannel(OrderChannel.TELEGRAM);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(resolveCurrency(business));
        order.setInvoiceNumber(nextInvoiceNumber(businessId));
        // No cashier: the customer rang this up themselves through the bot.
        order.setCashierId(null);
        order.setNote("Telegram bot order");

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = toOrderItem(cartItem);
            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getLineTotal());
        }

        int scale = scaleFor(order.getCurrency());

        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP));
        order.setTotal(subtotal.setScale(scale, RoundingMode.HALF_UP));

        if (order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TelegramCheckoutException("⚠️ ទឹកប្រាក់សរុបត្រូវតែធំជាងសូន្យ។");
        }

        Order savedOrder = orderRepository.save(order);

        // 2. Issue the KHQR through the official NBC generator (not a hand-rolled EMVCo string).
        Instant expiresAt = Instant.now().plusSeconds(QR_VALIDITY_MINUTES * 60L);

        KhqrGenerator.Result result;
        try {
            result = khqrGenerator.generate(
                    setting,
                    savedOrder.getTotal(),
                    savedOrder.getCurrency(),
                    savedOrder.getInvoiceNumber(),
                    "TELEGRAM",
                    expiresAt
            );
        } catch (ResponseStatusException exception) {
            log.error("KHQR generation failed for Telegram order {}: {}",
                    savedOrder.getId(), exception.getReason());
            throw new TelegramCheckoutException(
                    "❌ បង្កើតកូដ KHQR មិនបានសម្រេច។ សូមទាក់ទងហាងដើម្បីពិនិត្យការកំណត់ Bakong។", exception);
        }

        LocalDateTime expiresAtLocal = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());

        // 3. Void any stale QR then persist the new one so payment can be verified later.
        paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(savedOrder.getId())
                .forEach(existing -> {
                    if (QrStatus.PENDING.equals(existing.getStatus())) {
                        existing.setStatus(QrStatus.CANCELLED);
                    }
                });

        PaymentQrCode qrCode = new PaymentQrCode();
        qrCode.setBusiness(business);
        qrCode.setOrder(savedOrder);
        qrCode.setProvider("BAKONG");
        qrCode.setQrPayload(result.qr());
        qrCode.setMd5Hash(result.md5());
        qrCode.setAmount(savedOrder.getTotal());
        qrCode.setCurrency(savedOrder.getCurrency());
        qrCode.setStatus(QrStatus.PENDING);
        qrCode.setExpiresAt(expiresAtLocal);
        qrCode.setCreatedAt(LocalDateTime.now());

        paymentQrCodeRepository.save(qrCode);
        eventPublisher.publishEvent(new TelegramQrGeneratedEvent(qrCode.getId()));

        byte[] png = qrImageRenderer.toPngBytes(result.qr(), QR_IMAGE_SIZE);

        log.info("Telegram checkout created order {} ({}) for business {} totalling {} {}",
                savedOrder.getId(), savedOrder.getInvoiceNumber(), businessId,
                savedOrder.getTotal(), savedOrder.getCurrency());

        return new CheckoutDraft(
                savedOrder.getId(),
                savedOrder.getInvoiceNumber(),
                savedOrder.getTotal(),
                savedOrder.getCurrency(),
                cart.getTotalItemsCount(),
                result.qr(),
                result.md5(),
                png,
                expiresAtLocal,
                qrCode.getId()
        );
    }

    @Override
    @Transactional
    public VerifyResult verifyAndSettle(UUID businessId, UUID orderId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new TelegramCheckoutException("⚠️ រកមិនឃើញព័ត៌មានហាងទេ។"));

        Order order = orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new TelegramCheckoutException("⚠️ រកមិនឃើញការបញ្ជាទិញនេះទេ។"));

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
                .orElseThrow(() -> new TelegramCheckoutException(
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
            // Without a token we cannot verify. Never auto-approve in this case.
            throw new TelegramCheckoutException(
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

        // Rendered while the order/items are still attached to this transaction's
        // persistence context, so the poller can just send the text as-is without
        // risking a LazyInitializationException outside this method.
        String receiptText = uiHelper.renderReceipt(order, business);

        log.info("Telegram order {} ({}) confirmed paid by Bakong, hash {}",
                order.getId(), order.getInvoiceNumber(), result.hash());

        return new VerifyResult(true, false, "ការទូទាត់ត្រូវបានបញ្ជាក់ដោយ Bakong។", order.getInvoiceNumber(), receiptText);
    }

    @Override
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

    @Override
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
            stockEntryService.recordSale(
                    business,
                    line.getItem(),
                    BigDecimal.valueOf(line.getQuantity()),
                    order.getId(),
                    order.getInvoiceNumber()
            );

            BigDecimal unitCost = stockEntryService.findLatestUnitCost(
                    business.getId(), line.getItem().getId());

            if (unitCost == null) {
                unitCost = BigDecimal.ZERO;
            }

            line.setUnitCost(unitCost.setScale(2, RoundingMode.HALF_UP));

            totalCost = totalCost.add(unitCost.multiply(BigDecimal.valueOf(line.getQuantity())));
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
        sale.setNote("Paid via Bakong KHQR on Telegram");
        sale.setSoldAt(LocalDateTime.now());

        saleRepository.save(sale);

        receiptService.createForOrder(business, order, ReceiptType.DIGITAL);
    }

    /** The cart is only closed once the money has actually landed. */
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

    private OrderItem toOrderItem(CartItem cartItem) {
        Item item = cartItem.getItem();
        ItemVariant variant = cartItem.getVariant();

        BigDecimal unitPrice = cartItem.getPriceSnapshot() != null
                ? cartItem.getPriceSnapshot()
                : (variant != null && variant.getPrice() != null ? variant.getPrice() : item.getPrice());

        if (unitPrice == null) {
            throw new TelegramCheckoutException(
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
            throw new TelegramCheckoutException(
                    "⚠️ មុខងារទូទាត់តាម KHQR ត្រូវបានបិទសម្រាប់ហាងនេះ។ សូមទាក់ទងហាងដោយផ្ទាល់។", exception);
        }
    }

    private BusinessPaymentSetting requireActiveBakongSetting(UUID businessId) {
        BusinessPaymentSetting setting = paymentSettingRepository.findByBusiness_Id(businessId)
                .orElseThrow(() -> new TelegramCheckoutException(
                        "⚠️ ហាងនេះមិនទាន់កំណត់គណនី Bakong ទេ។ សូមទាក់ទងហាងដើម្បីទូទាត់តាមវិធីផ្សេង។"));

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            throw new TelegramCheckoutException(
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
}