package kh.edu.istad.ite.features.cart.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.cart.dto.ActiveCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutRequest;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontOrderResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontPaymentStatusResponse;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.entity.OrderItemAddOn;
import kh.edu.istad.ite.features.order.entity.OrderItemSelection;
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
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.QrStatus;
import kh.edu.istad.ite.shared.enums.ReceiptType;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.CurrencyDisplayHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import kh.edu.istad.ite.features.social.service.TelegramAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorefrontCheckoutServiceImpl implements StorefrontCheckoutService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 5;
    private static final int QR_IMAGE_SIZE = 512;
    private static final String TERMINAL_LABEL = "WEB";
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;
    private final CurrencyDisplayHelper currencyDisplayHelper;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityService customerIdentityService;
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
    private final kh.edu.istad.ite.features.channel.service.ChannelPriceResolver channelPriceResolver;
    private final ReceiptService receiptService;
    private final TelegramAlertService telegramAlertService;


    @Override
    @Transactional
    public StorefrontCheckoutResponse createCheckout(StorefrontCheckoutRequest request) {
        GlobalCustomer shopper = currentShopper();

        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop has not been found"));

        requireShoppable(business);
        businessHelper.requireFeature(business.getId(), BusinessFeature.KHQR_PAYMENT);


        Order openOrder = findOpenOrder(shopper).orElse(null);

        if (openOrder != null && !openOrder.getBusiness().getId().equals(business.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a payment open at " + openOrder.getBusiness().getDisplayName()
                            + ". Finish or cancel it before paying another shop.");
        }

        // Same store, still pending: reissue a QR rather than stacking orders.
        if (openOrder != null) {
            return issueQrFor(business, openOrder);
        }

        Customer customer = customerIdentityService.customerFor(business, shopper);

        Cart cart = cartRepository
                .findActiveCartWithItems(customer.getId(), business.getId(), CartStatus.ACTIVE)
                .filter(candidate -> !candidate.getItems().isEmpty())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Your cart for this shop is empty"));

        // Defence in depth: a cart is per business already, but never let a line
        // from another shop slip into an order that credits this shop's Bakong.
        for (CartItem line : cart.getItems()) {
            UUID lineBusinessId = line.getItem().getBusiness().getId();
            if (!lineBusinessId.equals(business.getId())) {
                log.error("Cart {} mixes businesses: line {} belongs to {} but cart is for {}",
                        cart.getId(), line.getId(), lineBusinessId, business.getId());
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This cart mixes items from more than one shop");
            }
        }

        // Check stock BEFORE money moves, never after Bakong confirms.
        for (CartItem line : cart.getItems()) {
            // What the shelf must hold: a case of twenty-four needs
            // twenty-four, not one.
            if (!hasEnoughStock(
                    business.getId(), line.getItem(), line.getVariant(), line.baseQuantity())) {
                // Named by the option, or the customer is told the item is out
                // while the shop is full of the size they did not ask for.
                String name = line.getVariant() == null
                        ? line.getItem().getName()
                        : line.getItem().getName() + " (" + line.getVariant().getVariantName() + ")";

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + name + "\" does not have enough stock left");
            }
        }

        Order order = new Order();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setChannel(OrderChannel.WEB);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(resolveCurrency(business));
        currencyDisplayHelper.snapshot(business, order.getCurrency()).ifPresent(snapshot -> {
            order.setDisplayCurrency(snapshot.currency());
            order.setDisplayExchangeRate(snapshot.rate());
        });
        order.setInvoiceNumber(nextInvoiceNumber(business.getId()));
        // The shopper rang this up themselves, so there is no cashier.
        order.setCashierId(null);
        order.setNote(StringUtils.hasText(request.note()) ? request.note() : "Storefront web order");

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem line : cart.getItems()) {
            OrderItem orderItem = toOrderItem(line);
            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getLineTotal());
        }

        int scale = scaleFor(order.getCurrency());

        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP));
        order.setTotal(subtotal.setScale(scale, RoundingMode.HALF_UP));

        if (order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total must be greater than zero");
        }

        Order saved = orderRepository.save(order);

        log.info("Storefront checkout opened: order {} ({}) at business {} for {} {}",
                saved.getId(), saved.getInvoiceNumber(), business.getId(),
                saved.getTotal(), saved.getCurrency());

        return issueQrFor(business, saved);
    }


    @Override
    @Transactional(readOnly = true)
    public ActiveCheckoutResponse findActiveCheckout() {
        GlobalCustomer shopper = currentShopper();

        Order openOrder = findOpenOrder(shopper).orElse(null);

        if (openOrder == null) {
            return new ActiveCheckoutResponse(false, null);
        }

        PaymentQrCode qrCode = pendingQrFor(openOrder.getId()).orElse(null);

        // An order whose QR already lapsed is still "open" — the shopper must
        // deal with it before another shop can be paid — but there is no live
        // QR to hand back, so the payload carries the order without one.
        return new ActiveCheckoutResponse(true, new StorefrontCheckoutResponse(
                openOrder.getId(),
                openOrder.getInvoiceNumber(),
                openOrder.getBusiness().getId(),
                openOrder.getBusiness().getDisplayName(),
                openOrder.getBusiness().getSlug(),
                openOrder.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                openOrder.getTotal(),
                openOrder.getCurrency(),
                qrCode == null ? null : qrCode.getQrPayload(),
                qrCode == null ? null : qrCode.getMd5Hash(),
                qrCode == null ? null : qrImageRenderer.toPngDataUri(qrCode.getQrPayload(), QR_IMAGE_SIZE),
                qrCode == null ? null : qrCode.getExpiresAt()));
    }


    @Override
    @Transactional
    public StorefrontPaymentStatusResponse checkPaymentStatus(UUID orderId) {
        GlobalCustomer shopper = currentShopper();
        Order order = requireOwnOrder(shopper, orderId);
        Business business = order.getBusiness();

        if (OrderStatus.PAID.equals(order.getStatus())) {
            return status(order, QrStatus.PAID, true, "This order is already paid", null, null);
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            return status(order, QrStatus.CANCELLED, false, "This order was cancelled", null, null);
        }

        PaymentQrCode qrCode = pendingQrFor(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No KHQR is waiting for this order"));

        // A lapsed QR is still worth checking. Banks can accept a scan a little
        // past our 5-minute window, and if the money did arrive we must honour
        // it rather than stranding a real payment behind an expiry check.
        boolean lapsed = qrCode.getExpiresAt().isBefore(LocalDateTime.now());

        BusinessPaymentSetting setting = requireActiveBakongSetting(business.getId());

        if (!StringUtils.hasText(setting.getApiTokenEncrypted())) {
            // Without a token we cannot verify. Never auto-approve.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This shop has not saved a Bakong API token, so payment cannot be confirmed automatically");
        }

        BakongCheckResult result = bakongTransactionClient.checkByMd5(
                credentialCipher.decrypt(setting.getApiTokenEncrypted()),
                qrCode.getMd5Hash());

        // "Could not verify" is not "not paid" — surface it instead of leaving
        // the shopper staring at a QR that will never resolve.
        if (result.verificationFailed()) {
            log.error("Cannot verify order {} ({}) with Bakong: {}",
                    order.getId(), order.getInvoiceNumber(), result.message());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, result.message());
        }

        if (!result.paid()) {
            if (lapsed) {
                qrCode.setStatus(QrStatus.EXPIRED);
                paymentQrCodeRepository.save(qrCode);
                return status(order, QrStatus.EXPIRED, false,
                        "The KHQR expired before it was paid. Generate a new one.",
                        qrCode.getExpiresAt(), null);
            }

            return status(order, QrStatus.PENDING, false,
                    "Bakong has not seen this payment yet", qrCode.getExpiresAt(), null);
        }

        if (lapsed) {
            log.info("Order {} ({}) was paid after its QR window closed — honouring it",
                    order.getId(), order.getInvoiceNumber());
        }

        // The md5 is derived from the QR payload, which already encodes amount
        // and currency, so a match binds the transaction to this exact order.
        // A discrepancy here means something upstream is wrong: record it.
        if (result.amount() != null && result.amount().compareTo(qrCode.getAmount()) != 0) {
            log.warn("Bakong amount {} {} differs from QR amount {} {} on order {} (md5 {})",
                    result.amount(), result.currency(),
                    qrCode.getAmount(), qrCode.getCurrency(),
                    order.getId(), qrCode.getMd5Hash());
        }

        LocalDateTime paidAt = LocalDateTime.now();
        qrCode.setStatus(QrStatus.PAID);
        qrCode.setPaidAt(paidAt);
        paymentQrCodeRepository.save(qrCode);

        settle(business, order);
        closeCart(order);

        telegramAlertService.sendQrPaymentAlert(order);

        log.info("Storefront order {} ({}) confirmed paid by Bakong, hash {}",
                order.getId(), order.getInvoiceNumber(), result.hash());

        return status(order, QrStatus.PAID, true,
                "Payment confirmed by Bakong", qrCode.getExpiresAt(), paidAt);
    }


    @Override
    @Transactional
    public StorefrontPaymentStatusResponse cancelCheckout(UUID orderId) {
        GlobalCustomer shopper = currentShopper();
        Order order = requireOwnOrder(shopper, orderId);

        if (OrderStatus.PAID.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A paid order cannot be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .forEach(qr -> {
                    if (QrStatus.PENDING.equals(qr.getStatus())) {
                        qr.setStatus(QrStatus.CANCELLED);
                    }
                });

        // The cart is untouched on cancel, so the shopper keeps their items and
        // is now free to pay a different shop.
        log.info("Storefront order {} ({}) cancelled by shopper", order.getId(), order.getInvoiceNumber());

        return status(order, QrStatus.CANCELLED, false, "Order cancelled", null, null);
    }


    /** Voids any stale QR for the order and mints a fresh 5-minute KHQR. */
    private StorefrontCheckoutResponse issueQrFor(Business business, Order order) {
        BusinessPaymentSetting setting = requireActiveBakongSetting(business.getId());

        paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())
                .forEach(existing -> {
                    if (QrStatus.PENDING.equals(existing.getStatus())) {
                        existing.setStatus(QrStatus.CANCELLED);
                    }
                });

        Instant expiresAt = Instant.now().plusSeconds(QR_VALIDITY_MINUTES * 60L);

        KhqrGenerator.Result result = khqrGenerator.generate(
                setting,
                order.getTotal(),
                order.getCurrency(),
                order.getInvoiceNumber(),
                TERMINAL_LABEL,
                expiresAt);

        LocalDateTime expiresAtLocal = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());

        PaymentQrCode qrCode = new PaymentQrCode();
        qrCode.setBusiness(business);
        qrCode.setOrder(order);
        qrCode.setProvider("BAKONG");
        qrCode.setQrPayload(result.qr());
        qrCode.setMd5Hash(result.md5());
        qrCode.setAmount(order.getTotal());
        qrCode.setCurrency(order.getCurrency());
        qrCode.setStatus(QrStatus.PENDING);
        qrCode.setExpiresAt(expiresAtLocal);
        qrCode.setCreatedAt(LocalDateTime.now());

        paymentQrCodeRepository.save(qrCode);

        return new StorefrontCheckoutResponse(
                order.getId(),
                order.getInvoiceNumber(),
                business.getId(),
                business.getDisplayName(),
                business.getSlug(),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getTotal(),
                order.getCurrency(),
                result.qr(),
                result.md5(),
                qrImageRenderer.toPngDataUri(result.qr(), QR_IMAGE_SIZE),
                expiresAtLocal);
    }

    private void settle(Business business, Order order) {
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            // Two polls can land at once; only the first one settles.
            return;
        }

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
                    order.getInvoiceNumber()).getUnitCost();

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

            // The extras go out with it: a tub of pearls empties whether it
            // was scooped into one drink or ten, and whether the drink was
            // rung up at the till or bought on the web.
            for (OrderItemAddOn chosen : line.getAddOns()) {
                if (chosen.getAddOn() == null) {
                    continue;
                }

                stockEntryService.recordAddOnSale(
                        business,
                        chosen.getAddOn(),
                        chosen.getUsePerOrder()
                                .multiply(BigDecimal.valueOf(line.getQuantity())),
                        order.getId(),
                        order.getInvoiceNumber());
            }

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
        sale.setDisplayCurrency(order.getDisplayCurrency());
        sale.setDisplayExchangeRate(order.getDisplayExchangeRate());
        sale.setPaymentMethod(PaymentMethodType.DIGITAL);
        sale.setItemCount(itemCount);
        sale.setNote("Paid via Bakong KHQR on the web storefront");
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

    private Optional<Order> findOpenOrder(GlobalCustomer shopper) {
        return orderRepository
                .findOpenOrdersForShopper(customerIdsOf(shopper), OrderChannel.WEB, OrderStatus.PENDING)
                .stream()
                .findFirst();
    }

    private Order requireOwnOrder(GlobalCustomer shopper, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has not been found"));

        UUID owner = order.getCustomer() == null || order.getCustomer().getGlobalCustomer() == null
                ? null
                : order.getCustomer().getGlobalCustomer().getId();

        if (!shopper.getId().equals(owner)) {
            // Same 404 as a missing order, so ids cannot be probed.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has not been found");
        }

        return order;
    }

    private Optional<PaymentQrCode> pendingQrFor(UUID orderId) {
        return paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .filter(qr -> QrStatus.PENDING.equals(qr.getStatus()))
                .findFirst();
    }

    private BusinessPaymentSetting requireActiveBakongSetting(UUID businessId) {
        BusinessPaymentSetting setting = paymentSettingRepository.findByBusiness_Id(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This shop has not set up Bakong payment yet"));

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bakong payment is switched off for this shop");
        }

        return setting;
    }

    private void requireShoppable(Business business) {
        if (!Boolean.TRUE.equals(business.getIsEnabled()) || Boolean.TRUE.equals(business.getIsClosed())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, business.getDisplayName() + " is closed right now");
        }

        businessHelper.requireFeature(business.getId(), BusinessFeature.STOREFRONT);
        // The shop's own online hours, enforced before money moves.
        channelPriceResolver.requireOpen(business.getId(), TERMINAL_LABEL);
    }

    /**
     * Whether the option in this basket line can still be sold.
     *
     * The option is counted on its own, so the item's total cannot answer it —
     * ten Smalls in stock says nothing about the Large being bought.
     *
     * And what is on the shelf is not always what this channel may sell: a
     * shop that has given the web four of its ten has said the other six are
     * for the counter, so the basket must stop at four.
     */
    private boolean hasEnoughStock(
            UUID businessId, Item item, ItemVariant variant, BigDecimal requestedBaseQuantity) {
        if (!item.isStockTracked()) {
            return true;
        }

        StockSummaryResponse summary = stockEntryService.findAvailableStock(
                businessId, item.getId(), variant == null ? null : variant.getId());

        // No stock entries at all means the shop is not tracking this item.
        if (summary.lastEntryId() == null) {
            return true;
        }

        BigDecimal onHand = summary.quantityOnHand() == null ? BigDecimal.ZERO : summary.quantityOnHand();
        BigDecimal available = itemChannelStockService.availableFor(
                item, variant, OrderChannel.WEB, onHand);

        return available.compareTo(requestedBaseQuantity) >= 0;
    }

    private OrderItem toOrderItem(CartItem cartItem) {
        Item item = cartItem.getItem();
        ItemVariant variant = cartItem.getVariant();

        BigDecimal unitPrice = cartItem.getPriceSnapshot() != null
                ? cartItem.getPriceSnapshot()
                : (variant != null && variant.getPrice() != null ? variant.getPrice() : item.getPrice());

        if (unitPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "\"" + item.getName() + "\" has no price yet");
        }

        int quantity = cartItem.getQuantity() == null ? 1 : cartItem.getQuantity();

        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setVariant(variant);
        orderItem.setItemName(variant != null
                ? item.getName() + " (" + variant.getVariantName() + ")"
                : item.getName());
        orderItem.setQuantity(quantity);
        // The basket already agreed which unit it was buying, and at what
        // factor; the order keeps both rather than working them out again.
        orderItem.setUnit(cartItem.getUnit());
        orderItem.setUnitFactor(
                cartItem.getUnitFactor() == null ? BigDecimal.ONE : cartItem.getUnitFactor());
        orderItem.setUnitPrice(unitPrice);
        orderItem.setUnitCost(BigDecimal.ZERO);
        orderItem.setDiscountAmount(BigDecimal.ZERO);

        // The extras were agreed and priced when they were ticked, so the
        // order takes its own copy of each — the basket is emptied once this
        // settles, and a receipt has to keep saying what was in the cup.
        if (cartItem.getAddOns() != null) {
            cartItem.getAddOns().forEach(chosen -> {
                OrderItemAddOn addOn = new OrderItemAddOn();
                addOn.setAddOn(chosen.getAddOn());
                addOn.setAddOnName(chosen.getAddOnName());
                addOn.setUnitPrice(
                        chosen.getUnitPrice() == null ? BigDecimal.ZERO : chosen.getUnitPrice());
                addOn.setUsePerOrder(
                        chosen.getUsePerOrder() == null ? BigDecimal.ONE : chosen.getUsePerOrder());
                orderItem.addAddOn(addOn);
            });
        }

        // Billed with its extras on top, the way the basket quoted it.
        orderItem.setLineTotal(
                orderItem.priceWithAddOns().multiply(BigDecimal.valueOf(quantity)));

        // The basket is emptied once this settles, so the order takes its own
        // copy of what was chosen — otherwise the only record of "50% sugar"
        // disappears at the moment it starts to matter.
        if (cartItem.getSelections() != null) {
            cartItem.getSelections().forEach(chosen -> {
                OrderItemSelection selection = new OrderItemSelection();
                selection.setAttributeName(chosen.getAttributeName());
                selection.setValue(chosen.getValue());
                selection.setLabel(chosen.getLabel());
                orderItem.addSelection(selection);
            });
        }

        return orderItem;
    }

    private StorefrontPaymentStatusResponse status(
            Order order,
            QrStatus qrStatus,
            boolean paid,
            String message,
            LocalDateTime expiresAt,
            LocalDateTime paidAt
    ) {
        return new StorefrontPaymentStatusResponse(
                order.getId(),
                order.getInvoiceNumber(),
                order.getStatus(),
                qrStatus,
                paid,
                message,
                expiresAt,
                paidAt);
    }

    private List<UUID> customerIdsOf(GlobalCustomer shopper) {
        List<UUID> ids = customerRepository.findAllByGlobalCustomer_Id(shopper.getId()).stream()
                .map(Customer::getId)
                .toList();

        return ids.isEmpty() ? List.of(new UUID(0L, 0L)) : ids;
    }

    private GlobalCustomer currentShopper() {
        return customerIdentityService.resolve(
                AuthHelper.currentUserId(),
                claim("email"),
                claim("phone_number"),
                claim("name"));
    }

    private String claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwt) {
            Object value = jwt.getToken().getClaims().get(name);
            return value == null ? null : String.valueOf(value);
        }

        return null;
    }

    private String resolveCurrency(Business business) {
        return StringUtils.hasText(business.getBaseCurrency()) ? business.getBaseCurrency() : "USD";
    }

    private int scaleFor(String currency) {
        return CURRENCY_KHR.equalsIgnoreCase(currency) ? 0 : 2;
    }

    private String nextInvoiceNumber(UUID businessId) {
        String datePart = LocalDateTime.now().format(INVOICE_DATE);
        long sequence = orderRepository.countByBusinessId(businessId) + 1;
        return "INV-" + datePart + "-" + String.format("%05d", sequence);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorefrontOrderResponse> getMyOrders() {
        GlobalCustomer shopper = currentShopper();
        List<UUID> customerIds = customerIdsOf(shopper);
        List<Order> orders = orderRepository.findAllOrdersForShopper(customerIds);
        return orders.stream().map(this::toStorefrontOrderResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StorefrontOrderResponse getMyOrderReceipt(UUID orderId) {
        GlobalCustomer shopper = currentShopper();
        Order order = requireOwnOrder(shopper, orderId);
        return toStorefrontOrderResponse(order);
    }

    private StorefrontOrderResponse toStorefrontOrderResponse(Order order) {
        Business b = order.getBusiness();
        Customer c = order.getCustomer();
        GlobalCustomer gc = c != null ? c.getGlobalCustomer() : null;

        List<StorefrontOrderResponse.StorefrontOrderItemResponse> items = order.getItems().stream()
                .map(item -> new StorefrontOrderResponse.StorefrontOrderItemResponse(
                        item.getItem() != null ? item.getItem().getId() : null,
                        item.getItemName(),
                        item.getQuantity() != null ? item.getQuantity() : 1,
                        item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO,
                        item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO,
                        // The extras read alongside the options: without them
                        // the line total is higher than the item's price with
                        // nothing on the receipt to say why.
                        Stream.concat(
                                        item.getSelections() == null
                                                ? Stream.<String>empty()
                                                : item.getSelections().stream()
                                                        .map(selection -> selection.getAttributeName()
                                                                + ": " + selection.display()),
                                        item.getAddOns() == null
                                                ? Stream.<String>empty()
                                                : item.getAddOns().stream()
                                                        .map(addOn -> "+ " + addOn.getAddOnName()))
                                .toList()
                ))
                .toList();

        return new StorefrontOrderResponse(
                order.getId(),
                order.getInvoiceNumber(),
                b.getId(),
                b.getDisplayName(),
                b.getSlug(),
                b.getLogo(),
                b.getAddress(),
                b.getPhoneNumber(),
                gc != null ? gc.getFullName() : null,
                gc != null ? gc.getEmail() : null,
                gc != null ? gc.getPhoneNumber() : null,
                order.getStatus(),
                order.getChannel() != null ? order.getChannel().name() : "WEB",
                "Bakong KHQR",
                order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO,
                order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO,
                order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO,
                order.getCurrency() != null ? order.getCurrency() : "USD",
                order.getDisplayCurrency(),
                order.getDisplayExchangeRate(),
                order.getItems().stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1).sum(),
                order.getCreatedDate(),
                OrderStatus.PAID.equals(order.getStatus()) ? order.getLastModifiedDate() : null,
                items
        );
    }
}