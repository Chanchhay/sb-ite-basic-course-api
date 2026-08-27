package kh.edu.istad.ite.features.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.filter.SearchRequestDto;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.config.specification.FilterSpecification;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemAddOn;
import kh.edu.istad.ite.features.order.entity.OrderItemAddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.entity.DiscountTarget;
import kh.edu.istad.ite.features.discount.repository.CouponRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountTargetRepository;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.OfflineOrderDto;
import kh.edu.istad.ite.features.order.dto.OfflineOrderItemDto;
import kh.edu.istad.ite.features.order.dto.SyncOfflineOrdersRequest;
import kh.edu.istad.ite.features.order.dto.SyncOfflineOrdersResponse;
import kh.edu.istad.ite.features.order.dto.AddOrderItemRequest;
import kh.edu.istad.ite.features.order.dto.CreateOrderItemRequest;
import kh.edu.istad.ite.features.order.dto.CreateOrderRequest;
import kh.edu.istad.ite.features.order.dto.OrderResponse;
import kh.edu.istad.ite.features.order.dto.PayOrderRequest;
import kh.edu.istad.ite.features.order.dto.PaymentStatusResponse;
import kh.edu.istad.ite.features.order.dto.SaleResponse;
import kh.edu.istad.ite.features.order.dto.UpdateOrderItemRequest;
import kh.edu.istad.ite.features.order.dto.UpdateOrderDiscountRequest;
import kh.edu.istad.ite.features.order.dto.UpdateOrderNoteRequest;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.entity.Sale;
import kh.edu.istad.ite.features.order.mapper.OrderMapper;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.features.payment.bakong.BakongCheckResult;
import kh.edu.istad.ite.features.payment.bakong.BakongTransactionClient;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.khqr.KhqrGenerator;
import kh.edu.istad.ite.features.payment.khqr.QrImageRenderer;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import kh.edu.istad.ite.features.payment.service.ReceiptService;
import kh.edu.istad.ite.features.register.entity.RegisterSession;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.QrStatus;
import kh.edu.istad.ite.shared.enums.ReceiptType;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import kh.edu.istad.ite.features.business.service.TaxCalculator;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.CurrencyDisplayHelper;
import kh.edu.istad.ite.features.social.service.TelegramAlertService;
import kh.edu.istad.ite.shared.enums.CouponStatus;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 2;
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessHelper businessHelper;
    private final CurrencyDisplayHelper currencyDisplayHelper;
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final BusinessPaymentSettingRepository paymentSettingRepository;
    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final KhqrGenerator khqrGenerator;
    private final QrImageRenderer qrImageRenderer;
    private final BakongTransactionClient bakongTransactionClient;
    private final CredentialCipher credentialCipher;
    private final OrderMapper orderMapper;
    private final SaleRepository saleRepository;
    private final StockEntryService stockEntryService;
    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final DiscountTargetRepository discountTargetRepository;

    private final ItemChannelStockService itemChannelStockService;
    private final ReceiptService receiptService;
    private final FilterSpecification<Order> filterSpecification;
    private final kh.edu.istad.ite.features.register.repository.RegisterSessionRepository registerSessionRepository;
    private final TelegramAlertService telegramAlertService;
    private final kh.edu.istad.ite.features.channel.service.ChannelPriceResolver channelPriceResolver;
    private final TaxCalculator taxCalculator;

    @Override
    @Transactional
    public OrderResponse createOrder(UUID businessId, CreateOrderRequest request) {
        Business business = businessHelper.findAccessibleBusiness(businessId);

        // Opening hours nothing enforces are a note to self, so a channel that
        // has said it is shut does not take the order.
        if (request.channel() != null) {
            channelPriceResolver.requireOpen(businessId, request.channel().name());
        }

        Order order = new Order();
        order.setBusiness(business);
        order.setChannel(request.channel());
        order.setStatus(OrderStatus.PENDING);
        order.setNote(request.note());
        order.setCurrency(resolveCurrency(request.currency(), business));
        applyDisplayCurrency(business, order);
        order.setInvoiceNumber(nextInvoiceNumber(business.getId()));
        // Whoever is signed in is the one working the till.
        order.setCashierId(AuthHelper.currentUserId());

        Customer customer = null;
        if (request.customerId() != null) {
            // Scoped by business so one shop cannot attach another shop's customer.
            customer = customerRepository.findByIdAndBusinessId(request.customerId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer has not been found"));
            order.setCustomer(customer);
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        int maxLineNumber = 0;
        if (request.items() != null) {
            for (CreateOrderItemRequest itemRequest : request.items()) {
                java.util.Optional<OrderItem> existingOpt = order.getItems().stream()
                        .filter(i -> i.getItem().getId().equals(itemRequest.itemId()) &&
                                (i.getVariant() == null ? itemRequest.variantId() == null : i.getVariant().getId().equals(itemRequest.variantId())))
                        .findFirst();

                if (existingOpt.isPresent()) {
                    OrderItem existing = existingOpt.get();
                    existing.setQuantity(existing.getQuantity() + itemRequest.quantity());
                    BigDecimal itemDiscount = existing.getDiscountAmount() != null ? existing.getDiscountAmount() : BigDecimal.ZERO;
                    existing.setLineTotal(existing.priceWithAddOns().multiply(BigDecimal.valueOf(existing.getQuantity())).subtract(itemDiscount));
                } else {
                    OrderItem item = buildItem(businessId, channelCodeOf(order), itemRequest);
                    maxLineNumber++;
                    item.setLineNumber(maxLineNumber);
                    order.addItem(item);
                }
            }
        }

        for (OrderItem item : order.getItems()) {
            subtotal = subtotal.add(item.getLineTotal());
        }

        BigDecimal discount = resolveOrderDiscountAmount(
                businessId,
                order,
                customer,
                request.discountId(),
                request.discountCode(),
                request.discountAmount(),
                true);

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;

        BigDecimal afterDiscount = subtotal.subtract(discount);
        TaxCalculator.Result taxResult = taxCalculator.apply(business, afterDiscount, scale);
        order.setTaxInclusionType(taxResult.inclusionType());
        order.setTaxRate(taxResult.taxRate());
        order.setTaxAmount(taxResult.taxAmount());

        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(discount.setScale(scale, RoundingMode.HALF_UP));
        order.setTotal(taxResult.total());

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findOrderById(UUID businessId, UUID orderId) {
        businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);
        OrderResponse response = orderMapper.toResponse(order);

        if (OrderStatus.PAID.equals(order.getStatus())) {
            saleRepository.findByOrderId(order.getId())
                    .ifPresent(sale -> response.setPaymentMethod(sale.getPaymentMethod()));
        }

        return response;
    }

    @Override
    @Transactional
    public KhqrResponse generateKhqr(UUID businessId, UUID orderId) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        businessHelper.requireFeature(businessId, BusinessFeature.KHQR_PAYMENT);

        Order order = findOrder(businessId, orderId);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only a pending order can be paid, current status is " + order.getStatus());
        }

        if (order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total must be greater than zero");
        }

        BusinessPaymentSetting setting = paymentSettingRepository.findByBusiness_Id(business.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Bakong settings have not been configured"));

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bakong payment is not activated for this business");
        }

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
                null,
                expiresAt
        );

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

        return new KhqrResponse(
                result.qr(),
                result.md5(),
                order.getTotal(),
                order.getCurrency(),
                order.getInvoiceNumber(),
                expiresAtLocal,
                qrImageRenderer.toPngDataUri(result.qr())
        );
    }

    @Override
    @Transactional
    public SaleResponse payOrder(UUID businessId, UUID orderId, PayOrderRequest request) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);

        requireSettleable(order);

        if (OrderChannel.POS.equals(order.getChannel())) {
            boolean hasOpenSession = false;
            Optional<RegisterSession> sessionOpt =
                    registerSessionRepository.findByBusinessIdAndStatus(business.getId(), SessionStatus.OPEN);
            if (sessionOpt.isPresent() && sessionOpt.get().getParticipants().contains(AuthHelper.currentUserId().toString())) {
                hasOpenSession = true;
            }
            if (!hasOpenSession) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No open register session found for current cashier");
            }
        }

        int scale = scaleFor(order);
        // Tax was already folded into order.getTotal() when the order was
        // created (or last repriced), so this is simply what is owed — adding
        // order.getTaxAmount() on top here would charge it twice.
        BigDecimal effectiveTotal = order.getTotal();

        BigDecimal received;
        if (PaymentMethodType.PAY_LATER.equals(request.paymentMethod())) {
            // Pay later collects nothing right now — whatever the client sent is ignored.
            received = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        } else {
            received = request.receivedAmount() == null
                    ? effectiveTotal
                    : request.receivedAmount().setScale(scale, RoundingMode.HALF_UP);

            if (received.compareTo(effectiveTotal) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Received " + received + " is less than the total " + effectiveTotal);
            }
        }

        return toSaleResponse(settle(business, order, request.paymentMethod(), received, request.note()));
    }

    @Override
    @Transactional
    public PaymentStatusResponse checkPaymentStatus(UUID businessId, UUID orderId) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);

        if (OrderStatus.PAID.equals(order.getStatus())) {
            return new PaymentStatusResponse(
                    order.getId(), order.getStatus(), QrStatus.PAID, true,
                    "Already paid", null, null);
        }

        PaymentQrCode qrCode = paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())
                .stream()
                .filter(qr -> QrStatus.PENDING.equals(qr.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No pending QR for this order"));

        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            qrCode.setStatus(QrStatus.EXPIRED);
            return new PaymentStatusResponse(
                    order.getId(), order.getStatus(), QrStatus.EXPIRED, false,
                    "The QR expired before it was paid", qrCode.getExpiresAt(), null);
        }

        BusinessPaymentSetting setting = paymentSettingRepository.findByBusiness_Id(business.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Bakong settings have not been configured"));

        if (!StringUtils.hasText(setting.getApiTokenEncrypted())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No Bakong API token saved, so payment cannot be confirmed automatically");
        }

        BakongCheckResult result = bakongTransactionClient.checkByMd5(
                credentialCipher.decrypt(setting.getApiTokenEncrypted()),
                qrCode.getMd5Hash());

        if (!result.paid()) {
            return new PaymentStatusResponse(
                    order.getId(), order.getStatus(), QrStatus.PENDING, false,
                    result.message(), qrCode.getExpiresAt(), null);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        qrCode.setStatus(QrStatus.PAID);
        qrCode.setPaidAt(paidAt);

        settle(business, order, PaymentMethodType.DIGITAL, order.getTotal(), null);

        telegramAlertService.sendQrPaymentAlert(order);

        return new PaymentStatusResponse(
                order.getId(), OrderStatus.PAID, QrStatus.PAID, true,
                "Payment confirmed by Bakong", qrCode.getExpiresAt(), paidAt);
    }


    private Sale settle(
            Business business,
            Order order,
            PaymentMethodType paymentMethod,
            BigDecimal received,
            String note
    ) {
        requireSettleable(order);

        // A confirmed order already took its stock off the shelf — pricing
        // it a second time here would empty it twice for one sale. Only a
        // still-pending order needs the shelf touched now.
        if (OrderStatus.PENDING.equals(order.getStatus())) {
            consumeStockForOrder(business, order);
        }

        int scale = scaleFor(order);
        BigDecimal total = order.getTotal();

        // Whichever call took the stock — this one, or confirm() earlier —
        // left its cost on the line and its add-ons, so the total is just a
        // sum of what is already there.
        BigDecimal totalCost = BigDecimal.ZERO;
        int itemCount = 0;

        for (OrderItem line : order.getItems()) {
            BigDecimal unitCost = line.getUnitCost() == null ? BigDecimal.ZERO : line.getUnitCost();
            totalCost = totalCost.add(unitCost.multiply(line.baseQuantity()));

            for (OrderItemAddOn chosen : line.getAddOns()) {
                totalCost = totalCost.add(chosen.getCost() == null ? BigDecimal.ZERO : chosen.getCost());
            }

            itemCount += line.getQuantity();
        }

        TaxInclusionType inclusionType = order.getTaxInclusionType() != null
                ? order.getTaxInclusionType() : TaxInclusionType.EXCLUSIVE;
        BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;

        // Already folded into order.getTotal() at creation/repricing time —
        // this is what is owed, not something to add tax to again.
        BigDecimal effectiveTotal = order.getTotal();

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        Sale sale = new Sale();
        sale.setBusiness(business);
        sale.setOrder(order);
        sale.setCustomer(order.getCustomer());
        sale.setInvoiceNumber(order.getInvoiceNumber());
        sale.setCashierId(AuthHelper.currentUserId());
        sale.setChannel(order.getChannel());
        sale.setSubtotal(order.getSubtotal());
        sale.setDiscountAmount(order.getDiscountAmount());
        sale.setTaxRate(taxRate);
        sale.setTaxAmount(taxAmount);
        sale.setTaxInclusionType(inclusionType);
        sale.setTotalAmount(effectiveTotal);
        sale.setPaidAmount(received);
        sale.setChangeAmount(received.subtract(effectiveTotal).setScale(scale, RoundingMode.HALF_UP));
        sale.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        sale.setCurrency(order.getCurrency());
        // Frozen here, not looked up at render time, so a later rate change
        // cannot alter the figures already printed on this receipt.
        sale.setDisplayCurrency(order.getDisplayCurrency());
        sale.setDisplayExchangeRate(order.getDisplayExchangeRate());
        sale.setPaymentMethod(paymentMethod);
        sale.setItemCount(itemCount);
        sale.setNote(note);
        sale.setSoldAt(LocalDateTime.now());

        recordCouponUsage(order);

        Sale saved = saleRepository.save(sale);

        ReceiptType receiptType = OrderChannel.POS.equals(order.getChannel())
                ? ReceiptType.PHYSICAL
                : ReceiptType.DIGITAL;
        receiptService.createForOrder(business, order, receiptType);

        if (PaymentMethodType.CASH.equals(paymentMethod)) {
            paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())
                    .forEach(qr -> {
                        if (QrStatus.PENDING.equals(qr.getStatus())) {
                            qr.setStatus(QrStatus.CANCELLED);
                        }
                    });
        }

        return saved;
    }

    private void recordCouponUsage(Order order) {
        String code = StringUtils.hasText(order.getDiscountCode()) ? order.getDiscountCode().trim() : null;
        if (!StringUtils.hasText(code) && order.getDiscountId() != null) {
            code = couponRepository.findAllByBusinessIdAndDiscount_IdOrderByCreatedDateDesc(order.getBusiness().getId(), order.getDiscountId()).stream()
                    .map(Coupon::getCode)
                    .findFirst()
                    .orElse(null);
        }
        if (!StringUtils.hasText(code)) {
            return;
        }

        couponRepository.findByBusinessIdAndCodeIgnoreCase(order.getBusiness().getId(), code)
                .ifPresent(coupon -> {
                    int usedCount = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
                    coupon.setUsedCount(usedCount + 1);
                    if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
                        coupon.setStatus(CouponStatus.USED_UP);
                    }
                    couponRepository.save(coupon);
                });
    }

    private void requireSettleable(Order order) {
        if (!OrderStatus.PENDING.equals(order.getStatus()) && !OrderStatus.CONFIRMED.equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending or confirmed order can be paid, current status is " + order.getStatus());
        }
    }

    /**
     * Takes every stock-tracked line off the shelf, batch by batch, and
     * prices the line and its add-ons at what those batches actually cost.
     *
     * Called exactly once per order — from {@link #confirmOrder} if the
     * order is confirmed before it is paid, otherwise from {@link #settle}
     * at payment time. Never both: a line's cost is persisted the moment it
     * is priced, and {@code settle} skips this entirely for an order that
     * arrives already {@link OrderStatus#CONFIRMED}.
     */
    private void consumeStockForOrder(Business business, Order order) {
        // Nothing has been written yet, so a line that would sell past this
        // channel's share stops the whole sale here rather than halfway
        // through the ledger. Items whose stock is shared are untouched by it.
        for (OrderItem line : order.getItems()) {
            if (line.getItem() != null && line.getItem().isStockTracked()) {
                itemChannelStockService.requireAllocation(
                        line.getItem(),
                        line.getVariant(),
                        order.getChannel(),
                        line.baseQuantity());
            }
        }

        for (OrderItem line : order.getItems()) {
            boolean isTracked = line.getItem() != null && line.getItem().isStockTracked();
            BigDecimal unitCost = BigDecimal.ZERO;

            if (isTracked) {
                // The sale consumes stock batches oldest first, so its own entry
                // already carries what those units cost. Asking the item again
                // afterwards would price the sale at whatever is left on the shelf.
                var saleEntry = stockEntryService.recordSale(
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
                );

                if (saleEntry != null && saleEntry.getUnitCost() != null) {
                    unitCost = saleEntry.getUnitCost();
                }

                // The sale uses up the channel's share of the shelf as well as the
                // shelf itself. Does nothing for an item whose stock is shared,
                // which is most of them.
                itemChannelStockService.consume(
                        line.getItem(),
                        line.getVariant(),
                        order.getChannel(),
                        line.baseQuantity());
            }

            line.setUnitCost(unitCost.setScale(2, RoundingMode.HALF_UP));

            // The extras go out with it: a tub of pearls empties whether it
            // was scooped into one drink or ten.
            for (OrderItemAddOn chosen : line.getAddOns()) {
                if (chosen.getAddOn() == null) {
                    continue;
                }

                // What the extra actually cost, from the batches it emptied.
                // Kept on the line so a later total (settle, run separately)
                // can add it up without touching the shelf again.
                BigDecimal addOnCost = stockEntryService.recordAddOnSale(
                        business,
                        chosen.getAddOn(),
                        chosen.getUsePerOrder()
                                .multiply(BigDecimal.valueOf(line.getQuantity())),
                        order.getId(),
                        order.getInvoiceNumber()
                ).getCostOfGoods();

                if (addOnCost == null) {
                    addOnCost = BigDecimal.ZERO;
                }

                chosen.setCost(addOnCost.setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(UUID businessId, UUID orderId) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending order can be confirmed, current status is " + order.getStatus());
        }

        consumeStockForOrder(business, order);

        order.setStatus(OrderStatus.CONFIRMED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    private int scaleFor(Order order) {
        return CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;
    }

    private SaleResponse toSaleResponse(Sale sale) {
        return orderMapper.toSaleResponse(sale);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID businessId, UUID orderId) {
        businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);

        if (OrderStatus.PAID.equals(order.getStatus()) || OrderStatus.CONFIRMED.equals(order.getStatus())) {
            // Stock already left the shelf for both — there is no reversal
            // path here, so cancelling would silently lose it.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A " + order.getStatus().name().toLowerCase() + " order cannot be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);

        paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())
                .forEach(qr -> {
                    if (QrStatus.PENDING.equals(qr.getStatus())) {
                        qr.setStatus(QrStatus.CANCELLED);
                    }
                });

        return orderMapper.toResponse(orderRepository.save(order));
    }

    /** The channel an order came through, as the sales channel knows it. */
    private static String channelCodeOf(Order order) {
        return order.getChannel() == null ? null : order.getChannel().name();
    }

    private OrderItem buildItem(UUID businessId, String channelCode, CreateOrderItemRequest request) {
        Item item = itemRepository.findByIdAndBusinessId(request.itemId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item has not been found: " + request.itemId()));

        BigDecimal unitPrice = item.getPrice();
        ItemVariant variant = null;

        if (request.variantId() != null) {
            variant = item.getVariants().stream()
                    .filter(candidate -> candidate.getId().equals(request.variantId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Variant has not been found: " + request.variantId()));

            if (variant.getPrice() != null) {
                unitPrice = variant.getPrice();
            }
        }

        /*
         * A larger unit is priced in its own right — a case is not twenty-four
         * times a can, or nobody would buy the case. So its price replaces the
         * item's rather than multiplying it, and the factor only says what
         * comes off the shelf.
         */
        Unit unit = null;
        BigDecimal unitFactor = BigDecimal.ONE;

        if (request.unitId() != null && !request.unitId().equals(baseUnitId(item))) {
            /*
             * A larger unit belongs to one option: the case defined for Large
             * is not the one defined for Small, and a shop need not sell both.
             * So the line's option is part of finding it — never a fallback to
             * some other option's case, which would sell what nobody offered.
             */
            final ItemVariant chosenOption = variant;
            final UUID lineVariantId = variant == null ? null : variant.getId();
            ItemUomConversion conversion = item.getUomConversions().stream()
                    .filter(candidate -> candidate.getUnit() != null
                            && candidate.getUnit().getId().equals(request.unitId()))
                    .filter(candidate -> {
                        UUID candidateVariantId = candidate.getVariant() == null
                                ? null
                                : candidate.getVariant().getId();

                        return java.util.Objects.equals(candidateVariantId, lineVariantId);
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "\"" + item.getName() + "\""
                                    + (chosenOption == null
                                            ? ""
                                            : " (" + chosenOption.getVariantName() + ")")
                                    + " is not sold by that unit"));

            if (conversion.getPrice() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + item.getName() + "\""
                                + (variant == null ? "" : " (" + variant.getVariantName() + ")")
                                + " has no price per " + conversion.getUnit().getName());
            }

            unitPrice = conversion.getPrice();
            unit = conversion.getUnit();
            unitFactor = conversion.getFactor();
        } else if (item.getUnit() != null) {
            unit = item.getUnit();
        }

        if (unitPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Item has no price: " + item.getName());
        }

        /*
         * The channel gets the last word on what this costs.
         *
         * A shop that charges more for delivery set that up once, against the
         * business price; charging the business price here regardless would
         * make every one of those exceptions a decoration.
         */
        unitPrice = channelPriceResolver.priceFor(
                businessId,
                channelCode,
                unitPrice,
                item.getId(),
                variant == null ? null : variant.getId(),
                unit == null || unit.getId().equals(baseUnitId(item)) ? null : unit.getId());

        OrderItem orderItem = new OrderItem();
        attachAddOns(orderItem, item, request.addOnIds());
        orderItem.setItem(item);
        orderItem.setVariant(variant);
        orderItem.setUnit(unit);
        orderItem.setUnitFactor(unitFactor);
        orderItem.setItemName(item.getName());
        orderItem.setQuantity(request.quantity());
        orderItem.setUnitPrice(unitPrice);
        orderItem.setUnitCost(BigDecimal.ZERO);
        orderItem.setDiscountAmount(BigDecimal.ZERO);
        orderItem.setLineTotal(
                orderItem.priceWithAddOns().multiply(BigDecimal.valueOf(request.quantity())));

        return orderItem;
    }

    private static UUID baseUnitId(Item item) {
        return item.getUnit() == null ? null : item.getUnit().getId();
    }

    /**
     * Whether a line is the same sale as what is being added.
     *
     * A case and a can of the same beer are different lines: they carry
     * different prices and take different amounts off the shelf, so merging
     * them would lose both.
     */
    private static boolean sameLine(
            OrderItem line, UUID itemId, UUID variantId, UUID unitId, List<UUID> addOnIds) {
        boolean sameItem = line.getItem().getId().equals(itemId);
        boolean sameVariant = line.getVariant() == null
                ? variantId == null
                : line.getVariant().getId().equals(variantId);
        UUID lineUnitId = line.getUnit() == null ? null : line.getUnit().getId();
        // A line in the base unit may carry the unit or not, depending on when
        // it was written, and both mean the same thing.
        boolean sameUnit = unitId == null
                ? lineUnitId == null || lineUnitId.equals(baseUnitId(line.getItem()))
                : unitId.equals(lineUnitId);

        // A latte with pearls is not the same line as one without.
        Set<UUID> lineAddOns = line.getAddOns().stream()
                .map(addOn -> addOn.getAddOn() == null ? null : addOn.getAddOn().getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> wantedAddOns = addOnIds == null
                ? Set.of()
                : new java.util.HashSet<>(addOnIds);

        return sameItem && sameVariant && sameUnit && lineAddOns.equals(wantedAddOns);
    }

    /**
     * Puts the chosen extras on the line, at what they cost right now.
     *
     * Only what the item actually sells: an add-on it does not offer, or one
     * switched off for it, is refused rather than quietly charged for. The
     * price and usage are copied onto the line, so the receipt and the stock
     * it consumed stay readable however the add-on changes later.
     */
    private void attachAddOns(OrderItem orderItem, Item item, List<UUID> addOnIds) {
        if (addOnIds == null || addOnIds.isEmpty()) {
            return;
        }

        for (UUID addOnId : addOnIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            ItemAddOn link = item.getAddOns().stream()
                    .filter(candidate -> candidate.getAddOn().getId().equals(addOnId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "\"" + item.getName() + "\" does not offer that add-on"));

            AddOn addOn = link.getAddOn();

            if (!link.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + addOn.getName() + "\" is not on sale with \"" + item.getName() + "\"");
            }

            if (addOn.getPrice() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + addOn.getName() + "\" has no price yet");
            }

            OrderItemAddOn line = new OrderItemAddOn();
            line.setAddOn(addOn);
            line.setAddOnName(addOn.getName());
            line.setUnitPrice(addOn.getPrice());
            line.setUsePerOrder(
                    addOn.getUsePerOrder() == null ? BigDecimal.ONE : addOn.getUsePerOrder());
            orderItem.addAddOn(line);
        }
    }

    private Order findOrder(UUID businessId, UUID orderId) {
        return orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has not been found"));
    }

    private void applyDisplayCurrency(Business business, Order order) {
        currencyDisplayHelper.snapshot(business, order.getCurrency()).ifPresent(snapshot -> {
            order.setDisplayCurrency(snapshot.currency());
            order.setDisplayExchangeRate(snapshot.rate());
        });
    }

    private String resolveCurrency(String requested, Business business) {
        if (StringUtils.hasText(requested)) {
            return requested.trim().toUpperCase();
        }
        return StringUtils.hasText(business.getBaseCurrency()) ? business.getBaseCurrency() : "USD";
    }

    private String nextInvoiceNumber(UUID businessId) {
        String datePart = LocalDateTime.now().format(INVOICE_DATE);
        long sequence = orderRepository.countByBusinessId(businessId) + 1;
        return "INV-" + datePart + "-" + String.format("%05d", sequence);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> filterOrders(UUID businessId, RequestDto requestDto, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        // Add businessId filter implicitly
        SearchRequestDto bizFilter = new SearchRequestDto();
        bizFilter.setColumn("business.id"); // Wait, FilterSpecification handles nested? No, "business" usually requires join or just "business.id" if it maps to simple property, let's use join.
        bizFilter.setOperation(kh.edu.istad.ite.config.filter.SearchRequestDto.Operation.JOIN);
        bizFilter.setJoinTable("business");
        bizFilter.setColumn("id");
        bizFilter.setValue(businessId.toString()); // Wait, FilterSpecification maps string? FilterSpecification uses equal(..., requestDto.getValue()). Value is string. UUID needs to be converted if it doesn't match type.
        // Actually, just fetching by Spec might be tricky if we don't control the UUID type conversion in generic spec.
        // I will use a custom specification combined with FilterSpecification.

        Specification<Order> spec = filterSpecification.getSearchSpecificationDynamic(
                requestDto.getSearchRequestDto(), requestDto.getGlobalOperator());

        Specification<Order> businessSpec = (root, query, cb) ->
                cb.equal(root.get("business").get("id"), businessId);

        Page<Order> orders = orderRepository.findAll(businessSpec.and(spec), pageable);

        // One bulk lookup for the whole page rather than one per row — a sale
        // only exists for a PAID order, so a page of PENDING carts costs
        // nothing extra here.
        List<UUID> paidOrderIds = orders.getContent().stream()
                .filter(order -> OrderStatus.PAID.equals(order.getStatus()))
                .map(Order::getId)
                .toList();

        java.util.Map<UUID, PaymentMethodType> paymentMethodByOrderId = paidOrderIds.isEmpty()
                ? java.util.Map.of()
                : saleRepository.findByOrder_IdIn(paidOrderIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                sale -> sale.getOrder().getId(), Sale::getPaymentMethod));

        return PageResponse.from(orders.map(order -> {
            OrderResponse response = orderMapper.toResponse(order);
            response.setPaymentMethod(paymentMethodByOrderId.get(order.getId()));
            return response;
        }));
    }

    @Override
    @Transactional
    public OrderResponse addOrderItem(UUID businessId, UUID orderId, AddOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);
        BigDecimal orderLevelDiscount = currentOrderLevelDiscount(order);

        java.util.Optional<OrderItem> existingOpt = order.getItems().stream()
                .filter(i -> sameLine(i, request.itemId(), request.variantId(), request.unitId(),
                        request.addOnIds()))
                .findFirst();

        if (existingOpt.isPresent()) {
            OrderItem existing = existingOpt.get();
            existing.setQuantity(existing.getQuantity() + request.quantity());
            BigDecimal itemDiscount = existing.getDiscountAmount() != null ? existing.getDiscountAmount() : BigDecimal.ZERO;
            if (request.discountAmount() != null) {
                itemDiscount = itemDiscount.add(request.discountAmount());
                existing.setDiscountAmount(itemDiscount);
            }
            existing.setLineTotal(existing.priceWithAddOns().multiply(BigDecimal.valueOf(existing.getQuantity())).subtract(itemDiscount));
        } else {
            CreateOrderItemRequest createReq = new CreateOrderItemRequest(
                    request.itemId(), request.variantId(), request.unitId(),
                    request.addOnIds(), request.quantity());

            OrderItem item = buildItem(businessId, channelCodeOf(order), createReq);
            if (request.discountAmount() != null) {
                item.setDiscountAmount(request.discountAmount());
                item.setLineTotal(item.priceWithAddOns().multiply(BigDecimal.valueOf(item.getQuantity())).subtract(request.discountAmount()));
            }
            int maxLine = order.getItems().stream()
                    .mapToInt(i -> i.getLineNumber() == null ? 0 : i.getLineNumber())
                    .max()
                    .orElse(0);
            item.setLineNumber(maxLine + 1);
            order.addItem(item);
        }

        order.setDiscountAmount(itemDiscountTotal(order).add(orderLevelDiscount));
        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderItem(UUID businessId, UUID orderId, UUID orderItemId, UpdateOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);
        BigDecimal orderLevelDiscount = currentOrderLevelDiscount(order);

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));

        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }

        if (request.discountAmount() != null) {
            item.setDiscountAmount(request.discountAmount());
        }

        // Recalculate line total
        BigDecimal discount = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
        item.setLineTotal(item.priceWithAddOns().multiply(BigDecimal.valueOf(item.getQuantity())).subtract(discount));

        order.setDiscountAmount(itemDiscountTotal(order).add(orderLevelDiscount));
        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse removeOrderItem(UUID businessId, UUID orderId, UUID orderItemId) {
        Order order = validateOrderModification(businessId, orderId);
        BigDecimal orderLevelDiscount = currentOrderLevelDiscount(order);

        boolean removed = order.getItems().removeIf(i -> i.getId().equals(orderItemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found");
        }

        order.setDiscountAmount(itemDiscountTotal(order).add(orderLevelDiscount));
        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderNote(UUID businessId, UUID orderId, UpdateOrderNoteRequest request) {
        Order order = validateOrderModification(businessId, orderId);
        order.setNote(request.note());
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderDiscount(UUID businessId, UUID orderId, UpdateOrderDiscountRequest request) {
        Order order = validateOrderModification(businessId, orderId);
        BigDecimal itemDiscount = itemDiscountTotal(order);
        BigDecimal orderLevelDiscount = resolveOrderDiscountAmount(
                businessId,
                order,
                order.getCustomer(),
                request.discountId(),
                request.discountCode(),
                request.discountAmount(),
                false);
        order.setDiscountAmount(itemDiscount.add(orderLevelDiscount));
        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    private BigDecimal resolveOrderDiscountAmount(
            UUID businessId,
            Order order,
            Customer customer,
            UUID requestedDiscountId,
            String requestedDiscountCode,
            BigDecimal manualDiscountAmount,
            boolean applyMembershipDiscount
    ) {
        BigDecimal discountableSubtotal = grossSubtotal(order).subtract(itemDiscountTotal(order));
        if (discountableSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            clearOrderDiscountSource(order);
            return BigDecimal.ZERO;
        }

        if (StringUtils.hasText(requestedDiscountCode)) {
            Coupon coupon = findUsableCoupon(businessId, requestedDiscountCode.trim(), discountableSubtotal, customer);
            Discount discount = coupon.getDiscount();
            if (requestedDiscountId != null && !requestedDiscountId.equals(discount.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Coupon does not belong to the requested discount");
            }
            validateDiscountForOrder(discount, order, customer, true);
            order.setDiscountId(discount.getId());
            order.setDiscountCode(coupon.getCode());
            return calculateDiscountAmount(discount, order, discountableSubtotal);
        }

        if (requestedDiscountId != null) {
            Discount discount = findDiscountForOrder(businessId, requestedDiscountId);
            validateDiscountForOrder(discount, order, customer, false);
            order.setDiscountId(discount.getId());
            order.setDiscountCode(null);
            BigDecimal calculated = calculateDiscountAmount(discount, order, discountableSubtotal);
            if (calculated.compareTo(BigDecimal.ZERO) > 0) {
                return calculated;
            }
            if (manualDiscountAmount != null && manualDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
                return manualDiscountAmount.min(discountableSubtotal);
            }
            return calculated;
        }

        if (applyMembershipDiscount
                && customer != null
                && customer.getMembershipType() != null
                && RecordStatus.ACTIVE.equals(customer.getMembershipType().getStatus())
                && customer.getMembershipType().getDiscount() != null) {
            Discount discount = customer.getMembershipType().getDiscount();
            validateDiscountForOrder(discount, order, customer, false);
            order.setDiscountId(discount.getId());
            order.setDiscountCode(null);
            return calculateDiscountAmount(discount, order, discountableSubtotal);
        }

        clearOrderDiscountSource(order);
        return manualDiscountAmount == null ? BigDecimal.ZERO : manualDiscountAmount;
    }

    private Coupon findUsableCoupon(UUID businessId, String code, BigDecimal subtotal, Customer customer) {
        Coupon coupon = couponRepository.findByBusinessIdAndCodeIgnoreCase(businessId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon has not been found"));

        if (!CouponStatus.ACTIVE.equals(coupon.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon is not active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon is not active yet");
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() != null
                && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon usage limit has been reached");
        }
        if (customer != null && coupon.getUsageLimitPerCustomer() != null) {
            long customerUses = orderRepository.countByBusinessIdAndCustomerIdAndDiscountCodeIgnoreCaseAndStatusNot(
                    businessId, customer.getId(), code, OrderStatus.CANCELLED);
            if (customerUses >= coupon.getUsageLimitPerCustomer()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have reached your maximum usage limit for this coupon");
            }
        }
        if (coupon.getMinPurchaseAmount() != null && subtotal.compareTo(coupon.getMinPurchaseAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not meet coupon minimum purchase amount");
        }

        return coupon;
    }

    private Discount findDiscountForOrder(UUID businessId, UUID discountId) {
        return discountRepository.findByIdAndBusinessId(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
    }

    private void validateDiscountForOrder(Discount discount, Order order, Customer customer, boolean couponProvided) {
        if (!RecordStatus.ACTIVE.equals(discount.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount is not active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (discount.getStartsAt() != null && now.isBefore(discount.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount is not active yet");
        }
        if (discount.getEndsAt() != null && now.isAfter(discount.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount has expired");
        }
        List<DayOfWeek> selectedDays = discount.getSelectedDays();
        if (selectedDays != null && !selectedDays.isEmpty()
                && selectedDays.stream().noneMatch(day -> day == LocalDate.now().getDayOfWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount is not available today");
        }
        List<OrderChannel> channels = discount.getApplicableChannels();
        if (channels != null && !channels.isEmpty() && !channels.contains(order.getChannel())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount is not available for this order channel");
        }
        if (Boolean.TRUE.equals(discount.getRequiresCoupon()) && !couponProvided) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This discount requires a coupon code");
        }
        if (DiscountScope.SPECIFIC_MEMBERSHIP.equals(discount.getScope())
                && (customer == null
                || customer.getMembershipType() == null
                || customer.getMembershipType().getDiscount() == null
                || !discount.getId().equals(customer.getMembershipType().getDiscount().getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount requires a matching customer membership type");
        }
    }

    private BigDecimal calculateDiscountAmount(Discount discount, Order order, BigDecimal discountableSubtotal) {
        BigDecimal eligibleSubtotal = eligibleSubtotal(discount, order).min(discountableSubtotal);
        if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (discount.getMinOrderAmount() != null && discountableSubtotal.compareTo(discount.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not meet discount minimum order amount");
        }
        if (DiscountRuleType.MIN_ORDER_AMOUNT.equals(discount.getRuleType())
                && discount.getMinOrderAmount() != null
                && discountableSubtotal.compareTo(discount.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not meet discount minimum order amount");
        }
        if (DiscountRuleType.MIN_QUANTITY.equals(discount.getRuleType())) {
            int eligibleQuantity = eligibleQuantity(discount, order);
            if (discount.getMinQuantity() == null || eligibleQuantity < discount.getMinQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not meet discount minimum quantity");
            }
        }

        BigDecimal amount;
        if (DiscountRuleType.BUY_X_GET_Y.equals(discount.getRuleType())
                || DiscountType.BUY_X_GET_Y.equals(discount.getType())) {
            amount = calculateBuyXGetYDiscount(discount, order);
        } else if (DiscountType.PERCENTAGE.equals(discount.getType())) {
            amount = eligibleSubtotal.multiply(discount.getValue()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        } else {
            amount = discount.getValue();
        }

        if (discount.getMaxDiscountAmount() != null && amount.compareTo(discount.getMaxDiscountAmount()) > 0) {
            amount = discount.getMaxDiscountAmount();
        }
        if (amount.compareTo(eligibleSubtotal) > 0) {
            amount = eligibleSubtotal;
        }
        return amount.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateBuyXGetYDiscount(Discount discount, Order order) {
        int buyQuantity = discount.getBuyQuantity() == null ? 0 : discount.getBuyQuantity();
        int getQuantity = discount.getGetQuantity() == null ? 0 : discount.getGetQuantity();
        if (buyQuantity <= 0 || getQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount is missing buy/get quantities");
        }

        List<BigDecimal> eligibleUnitPrices = new ArrayList<>();
        for (OrderItem item : eligibleItems(discount, order)) {
            BigDecimal unitPrice = item.priceWithAddOns();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            for (int i = 0; i < quantity; i++) {
                eligibleUnitPrices.add(unitPrice);
            }
        }

        int freeQuantity = (eligibleUnitPrices.size() / (buyQuantity + getQuantity)) * getQuantity;
        if (freeQuantity <= 0) {
            return BigDecimal.ZERO;
        }

        return eligibleUnitPrices.stream()
                .sorted(Comparator.naturalOrder())
                .limit(freeQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal eligibleSubtotal(Discount discount, Order order) {
        return eligibleItems(discount, order).stream()
                .map(this::grossLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int eligibleQuantity(Discount discount, Order order) {
        return eligibleItems(discount, order).stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
    }

    private List<OrderItem> eligibleItems(Discount discount, Order order) {
        DiscountScope scope = normalizeScope(discount.getScope());
        if (DiscountScope.ALL_ITEMS.equals(scope) || DiscountScope.SPECIFIC_MEMBERSHIP.equals(scope)) {
            return order.getItems();
        }

        List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(discount.getId());
        if (targets == null || targets.isEmpty()) {
            return order.getItems();
        }

        if (DiscountScope.SPECIFIC_ITEMS.equals(scope)) {
            Set<UUID> itemIds = targets.stream()
                    .filter(target -> target.getItem() != null)
                    .map(target -> target.getItem().getId())
                    .collect(java.util.stream.Collectors.toSet());
            if (itemIds.isEmpty()) return order.getItems();
            List<OrderItem> matching = order.getItems().stream()
                    .filter(item -> item.getItem() != null && itemIds.contains(item.getItem().getId()))
                    .toList();
            return matching.isEmpty() ? order.getItems() : matching;
        }
        if (DiscountScope.SPECIFIC_CATEGORIES.equals(scope)) {
            Set<UUID> itemGroupIds = targets.stream()
                    .filter(target -> target.getItemGroup() != null)
                    .map(target -> target.getItemGroup().getId())
                    .collect(java.util.stream.Collectors.toSet());
            if (itemGroupIds.isEmpty()) return order.getItems();
            List<OrderItem> matching = order.getItems().stream()
                    .filter(item -> item.getItem() != null
                            && item.getItem().getItemGroup() != null
                            && itemGroupIds.contains(item.getItem().getItemGroup().getId()))
                    .toList();
            return matching.isEmpty() ? order.getItems() : matching;
        }

        return order.getItems();
    }

    private DiscountScope normalizeScope(DiscountScope scope) {
        if (scope == null || DiscountScope.ORDER.equals(scope) || DiscountScope.ALL_ITEMS.equals(scope)) {
            return DiscountScope.ALL_ITEMS;
        }
        if (DiscountScope.ITEM.equals(scope) || DiscountScope.SPECIFIC_ITEMS.equals(scope)) {
            return DiscountScope.SPECIFIC_ITEMS;
        }
        if (DiscountScope.CATEGORY.equals(scope) || DiscountScope.SPECIFIC_CATEGORIES.equals(scope)) {
            return DiscountScope.SPECIFIC_CATEGORIES;
        }
        return scope;
    }

    private void clearOrderDiscountSource(Order order) {
        order.setDiscountId(null);
        order.setDiscountCode(null);
    }

    private BigDecimal grossSubtotal(Order order) {
        return order.getItems().stream()
                .map(this::grossLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal itemDiscountTotal(Order order) {
        return order.getItems().stream()
                .map(item -> item.getDiscountAmount() == null ? BigDecimal.ZERO : item.getDiscountAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal currentOrderLevelDiscount(Order order) {
        BigDecimal storedTotalDiscount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        BigDecimal orderLevelDiscount = storedTotalDiscount.subtract(itemDiscountTotal(order));
        return orderLevelDiscount.max(BigDecimal.ZERO);
    }

    private BigDecimal grossLineTotal(OrderItem item) {
        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        return item.priceWithAddOns().multiply(BigDecimal.valueOf(qty));
    }

    private Order validateOrderModification(UUID businessId, UUID orderId) {
        Order order = findOrder(businessId, orderId);
        if (OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.CANCELLED.equals(order.getStatus())
                || OrderStatus.CONFIRMED.equals(order.getStatus())) {
            // Confirmed already took its stock off the shelf at whatever the
            // line said then — changing the line after that would desync
            // the two.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot modify an order that is confirmed, paid or cancelled");
        }
        return order;
    }

    private void recalculateOrderTotals(Order order) {
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscount = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            BigDecimal itemGross = grossLineTotal(item);
            BigDecimal itemDisc = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal itemNet = itemGross.subtract(itemDisc);
            if (itemNet.compareTo(BigDecimal.ZERO) < 0) {
                itemNet = BigDecimal.ZERO;
            }
            item.setLineTotal(itemNet);

            grossSubtotal = grossSubtotal.add(itemGross);
            itemDiscount = itemDiscount.add(itemDisc);
        }

        BigDecimal storedTotalDiscount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        BigDecimal orderDiscount = storedTotalDiscount.subtract(itemDiscount);
        if (orderDiscount.compareTo(BigDecimal.ZERO) < 0) {
            orderDiscount = BigDecimal.ZERO;
        }
        BigDecimal maxAllowedOrderDiscount = grossSubtotal.subtract(itemDiscount);
        if (maxAllowedOrderDiscount.compareTo(BigDecimal.ZERO) < 0) {
            maxAllowedOrderDiscount = BigDecimal.ZERO;
        }
        if (orderDiscount.compareTo(maxAllowedOrderDiscount) > 0) {
            orderDiscount = maxAllowedOrderDiscount;
        }
        BigDecimal totalDiscount = itemDiscount.add(orderDiscount);

        int scale = CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;
        order.setSubtotal(grossSubtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(totalDiscount.setScale(scale, RoundingMode.HALF_UP));

        BigDecimal netSubtotal = grossSubtotal.subtract(totalDiscount);
        if (netSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            netSubtotal = BigDecimal.ZERO;
        }

        // The order was first priced (possibly against an empty $0 cart) back
        // in createOrder; every item/discount edit since has to re-run the
        // same calculator against the new net amount, or tax stays frozen at
        // whatever it was on that first, often-empty, cart.
        TaxCalculator.Result taxResult = taxCalculator.apply(order.getBusiness(), netSubtotal, scale);
        order.setTaxInclusionType(taxResult.inclusionType());
        order.setTaxRate(taxResult.taxRate());
        order.setTaxAmount(taxResult.taxAmount());
        order.setTotal(taxResult.total());
    }

    @Override
    @Transactional
    public SyncOfflineOrdersResponse syncOfflineOrders(UUID businessId, SyncOfflineOrdersRequest request) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        List<String> syncedUuids = new ArrayList<>();

        if (request == null || request.orders() == null || request.orders().isEmpty()) {
            return new SyncOfflineOrdersResponse(true, syncedUuids);
        }

        for (OfflineOrderDto dto : request.orders()) {
            if (dto.uuid() == null || dto.uuid().isBlank()) {
                continue;
            }

            Optional<Order> existingOpt = orderRepository.findByBusinessIdAndInvoiceNumber(business.getId(), dto.uuid());

            Order order;
            boolean isNewOrder = false;

            if (existingOpt.isPresent()) {
                order = existingOpt.get();
                // If it is already paid and has a sale, skip duplicate
                if (OrderStatus.PAID.equals(order.getStatus()) && saleRepository.findByOrderId(order.getId()).isPresent()) {
                    syncedUuids.add(dto.uuid());
                    continue;
                }
                // Update existing order status to PAID
                order.setStatus(dto.status() != null ? dto.status() : OrderStatus.PAID);
                if (dto.subtotal() != null && dto.subtotal().compareTo(BigDecimal.ZERO) > 0) {
                    order.setSubtotal(dto.subtotal());
                }
                if (dto.discountAmount() != null) {
                    order.setDiscountAmount(dto.discountAmount());
                }
                if (dto.total() != null && dto.total().compareTo(BigDecimal.ZERO) > 0) {
                    order.setTotal(dto.total());
                }
            } else {
                isNewOrder = true;
                order = new Order();
                order.setBusiness(business);
                order.setInvoiceNumber(dto.uuid());
                order.setChannel(dto.channel() != null ? dto.channel() : OrderChannel.POS);
                order.setStatus(dto.status() != null ? dto.status() : OrderStatus.PAID);
                order.setSubtotal(dto.subtotal() != null ? dto.subtotal() : BigDecimal.ZERO);
                order.setDiscountAmount(dto.discountAmount() != null ? dto.discountAmount() : BigDecimal.ZERO);
                order.setTotal(dto.total() != null ? dto.total() : BigDecimal.ZERO);

                if (dto.items() != null) {
                    for (OfflineOrderItemDto itemDto : dto.items()) {
                        if (itemDto.productId() == null) continue;

                        Item item = itemRepository.findById(itemDto.productId()).orElse(null);
                        if (item == null) continue;

                        OrderItem orderItem = new OrderItem();
                        orderItem.setItem(item);
                        orderItem.setItemName(item.getName() != null ? item.getName() : "Item");
                        orderItem.setQuantity(itemDto.quantity() != null ? itemDto.quantity() : 1);
                        orderItem.setUnitPrice(itemDto.unitPrice() != null ? itemDto.unitPrice() : BigDecimal.ZERO);
                        orderItem.setLineTotal(itemDto.subtotal() != null ? itemDto.subtotal() : BigDecimal.ZERO);

                        order.addItem(orderItem);
                    }
                }
            }

            Order savedOrder = orderRepository.save(order);

            // Create Sale entity if not already present
            if (saleRepository.findByOrderId(savedOrder.getId()).isEmpty()) {
                Sale sale = new Sale();
                sale.setBusiness(business);
                sale.setOrder(savedOrder);
                sale.setInvoiceNumber(savedOrder.getInvoiceNumber());
                sale.setChannel(savedOrder.getChannel());
                sale.setSubtotal(savedOrder.getSubtotal());
                sale.setDiscountAmount(savedOrder.getDiscountAmount());
                sale.setTotalAmount(savedOrder.getTotal());
                sale.setPaidAmount(savedOrder.getTotal());
                sale.setChangeAmount(BigDecimal.ZERO);
                sale.setPaymentMethod(dto.paymentMethod() != null ? dto.paymentMethod() : PaymentMethodType.CASH);
                sale.setItemCount(savedOrder.getItems() != null ? savedOrder.getItems().size() : 0);
                sale.setSoldAt(dto.createdAt() != null ? LocalDateTime.ofInstant(dto.createdAt(), ZoneId.systemDefault()) : LocalDateTime.now());
                saleRepository.save(sale);

                // Deduct Inventory Stock for Tracked Items
                consumeStockForOrder(business, savedOrder);
            }

            syncedUuids.add(dto.uuid());
        }

        return new SyncOfflineOrdersResponse(true, syncedUuids);
    }
}
