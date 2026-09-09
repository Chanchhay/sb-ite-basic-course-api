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
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
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
import kh.edu.istad.ite.features.cart.entity.CartItemSelection;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.features.order.entity.OrderItemSelection;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
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
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramCheckoutServiceImpl implements TelegramCheckoutService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 2;
    private static final int QR_IMAGE_SIZE = 512;

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
    private final ReceiptService receiptService;
    private final TelegramStockHelper stockHelper;
    private final TelegramUIHelper uiHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final DiscountService discountService;
    private final kh.edu.istad.ite.features.business.service.TaxCalculator taxCalculator;
    private final kh.edu.istad.ite.features.notification.push.PushNotificationClient pushNotificationClient;
    private final kh.edu.istad.ite.features.order.service.InvoiceNumberGenerator invoiceNumberGenerator;

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

        for (CartItem cartItem : cart.getItems()) {
            int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();
            if (!stockHelper.hasEnoughStock(
                    businessId, cartItem.getItem(), cartItem.getVariant(), cartItem.baseQuantity(),
                    OrderChannel.TELEGRAM)) {
                throw new TelegramCheckoutException(
                        "⚠️ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ \"" + cartItem.getItem().getName()
                                + "\" ទេ។ សូមកែសម្រួលកន្ត្រកទំនិញរបស់អ្នកមុននឹងទូទាត់ប្រាក់។");
            }
        }

        Order order = new Order();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setChannel(OrderChannel.TELEGRAM);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(resolveCurrency(business));
        order.setInvoiceNumber(nextInvoiceNumber(businessId));
        order.setCashierId(null);
        order.setNote("Telegram bot order");

        int scale = scaleFor(order.getCurrency());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = toOrderItem(cartItem);

            Item item = cartItem.getItem();
            UUID itemGroupId = item.getItemGroup() != null ? item.getItemGroup().getId() : null;
            int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();

            BigDecimal baseUnitPrice = orderItem.getUnitPrice();
            BigDecimal rawLineSubtotal = baseUnitPrice.multiply(BigDecimal.valueOf(quantity));

            List<DiscountResponse> applicableDiscounts = discountService.findApplicableDiscounts(
                    businessId,
                    OrderChannel.TELEGRAM,
                    item.getId(),
                    itemGroupId
            );

            BigDecimal lineDiscount = BigDecimal.ZERO;
            if (!applicableDiscounts.isEmpty()) {
                DiscountResponse discount = applicableDiscounts.get(0);
                if (discount.type() == DiscountType.PERCENTAGE && discount.value() != null) {
                    BigDecimal unitDiscount = baseUnitPrice.multiply(discount.value()).divide(BigDecimal.valueOf(100), scale, RoundingMode.HALF_UP);
                    lineDiscount = unitDiscount.multiply(BigDecimal.valueOf(quantity));
                } else if (discount.type() == DiscountType.FIXED_AMOUNT && discount.value() != null) {
                    lineDiscount = discount.value().multiply(BigDecimal.valueOf(quantity));
                }

                if (discount.maxDiscountAmount() != null && lineDiscount.compareTo(discount.maxDiscountAmount()) > 0) {
                    lineDiscount = discount.maxDiscountAmount();
                }

                if (lineDiscount.compareTo(rawLineSubtotal) > 0) {
                    lineDiscount = rawLineSubtotal;
                }
            }

            orderItem.setDiscountAmount(lineDiscount.setScale(scale, RoundingMode.HALF_UP));
            BigDecimal lineTotal = rawLineSubtotal.subtract(lineDiscount);
            if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                lineTotal = BigDecimal.ZERO;
            }
            orderItem.setLineTotal(lineTotal.setScale(scale, RoundingMode.HALF_UP));

            order.addItem(orderItem);
            subtotal = subtotal.add(rawLineSubtotal);
            totalDiscount = totalDiscount.add(lineDiscount);
        }

        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(totalDiscount.setScale(scale, RoundingMode.HALF_UP));
        BigDecimal netAmount = subtotal.subtract(totalDiscount);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            netAmount = BigDecimal.ZERO;
        }
        kh.edu.istad.ite.features.business.service.TaxCalculator.Result taxResult =
                taxCalculator.apply(business, netAmount, scale);
        order.setTaxInclusionType(taxResult.inclusionType());
        order.setTaxRate(taxResult.taxRate());
        order.setTaxAmount(taxResult.taxAmount());
        order.setTotal(taxResult.total());

        Order savedOrder = orderRepository.save(order);

        pushNotificationClient.notifyOwner(
                business.getKeycloakUserId(),
                "New order from Telegram",
                "Order " + savedOrder.getInvoiceNumber() + " — " + savedOrder.getTotal() + " " + savedOrder.getCurrency(),
                "/sales/orders",
                "channel-order");

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
        sale.setTaxRate(order.getTaxRate());
        sale.setTaxAmount(order.getTaxAmount());
        sale.setTaxInclusionType(order.getTaxInclusionType());
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
        orderItem.setBusiness(cartItem.getCart().getBusiness());
        orderItem.setItem(item);
        orderItem.setVariant(variant);
        orderItem.setUnit(cartItem.getUnit());
        orderItem.setUnitFactor(cartItem.getUnitFactor());
        orderItem.setItemName(variant != null ? item.getName() + " (" + variant.getVariantName() + ")" : item.getName());
        orderItem.setQuantity(quantity);
        orderItem.setUnitPrice(unitPrice);
        orderItem.setUnitCost(BigDecimal.ZERO);

        if (cartItem.getSelections() != null && !cartItem.getSelections().isEmpty()) {
            for (CartItemSelection sel : cartItem.getSelections()) {
                OrderItemSelection orderSel = new OrderItemSelection();
                orderSel.setAttributeName(sel.getAttributeName());
                orderSel.setValue(sel.getValue());
                orderSel.setLabel(sel.getLabel());
                orderItem.addSelection(orderSel);
            }
        }

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
        return invoiceNumberGenerator.next(businessId);
    }
}