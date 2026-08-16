package kh.edu.istad.ite.features.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.AddOrderItemRequest;
import kh.edu.istad.ite.features.order.dto.CreateOrderItemRequest;
import kh.edu.istad.ite.features.order.dto.CreateOrderRequest;
import kh.edu.istad.ite.features.order.dto.OrderResponse;
import kh.edu.istad.ite.features.order.dto.PayOrderRequest;
import kh.edu.istad.ite.features.order.dto.PaymentStatusResponse;
import kh.edu.istad.ite.features.order.dto.SaleResponse;
import kh.edu.istad.ite.features.order.dto.UpdateOrderItemRequest;
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
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.CurrencyDisplayHelper;
import kh.edu.istad.ite.features.social.service.TelegramAlertService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 5;
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

    private final ItemChannelStockService itemChannelStockService;
    private final ReceiptService receiptService;
    private final FilterSpecification<Order> filterSpecification;
    private final kh.edu.istad.ite.features.register.repository.RegisterSessionRepository registerSessionRepository;
    private final TelegramAlertService telegramAlertService;
    private final kh.edu.istad.ite.features.channel.service.ChannelPriceResolver channelPriceResolver;

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

        if (request.customerId() != null) {
            // Scoped by business so one shop cannot attach another shop's customer.
            Customer customer = customerRepository.findByIdAndBusinessId(request.customerId(), businessId)
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

        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();

        if (discount.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount cannot exceed the order subtotal");
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;

        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(discount.setScale(scale, RoundingMode.HALF_UP));
        order.setTotal(subtotal.subtract(discount).setScale(scale, RoundingMode.HALF_UP));

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findOrderById(UUID businessId, UUID orderId) {
        businessHelper.findAccessibleBusiness(businessId);
        return orderMapper.toResponse(findOrder(businessId, orderId));
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

        requirePending(order);

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
        BigDecimal total = order.getTotal();

        BigDecimal received = request.receivedAmount() == null
                ? total
                : request.receivedAmount().setScale(scale, RoundingMode.HALF_UP);

        if (received.compareTo(total) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Received " + received + " is less than the total " + total);
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
        requirePending(order);

        // Nothing has been written yet, so a line that would sell past this
        // channel's share stops the whole sale here rather than halfway
        // through the ledger. Items whose stock is shared are untouched by it.
        for (OrderItem line : order.getItems()) {
            itemChannelStockService.requireAllocation(
                    line.getItem(),
                    line.getVariant(),
                    order.getChannel(),
                    line.baseQuantity());
        }

        int scale = scaleFor(order);
        BigDecimal total = order.getTotal();

        BigDecimal totalCost = BigDecimal.ZERO;
        int itemCount = 0;

        for (OrderItem line : order.getItems()) {
            // The sale consumes stock batches oldest first, so its own entry
            // already carries what those units cost. Asking the item again
            // afterwards would price the sale at whatever is left on the shelf.
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

            // The extras go out with it: a tub of pearls empties whether it
            // was scooped into one drink or ten.
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
                        order.getInvoiceNumber()
                );
            }

            totalCost = totalCost.add(unitCost.multiply(BigDecimal.valueOf(line.getQuantity())));
            itemCount += line.getQuantity();
        }

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
          sale.setTaxRate(order.getTaxRate());
          sale.setTaxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO);
        sale.setTotalAmount(total);
        sale.setPaidAmount(received);
        sale.setChangeAmount(received.subtract(total).setScale(scale, RoundingMode.HALF_UP));
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

    private void requirePending(Order order) {
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending order can be paid, current status is " + order.getStatus());
        }
    }

    private int scaleFor(Order order) {
        return CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;
    }

    private SaleResponse toSaleResponse(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getOrder().getId(),
                sale.getInvoiceNumber(),
                sale.getCashierId(),
                sale.getChannel(),
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getTaxRate(),
                sale.getTaxAmount(),
                sale.getTotalAmount(),
                sale.getPaidAmount(),
                sale.getChangeAmount(),
                sale.getTotalCost(),
                sale.getCurrency(),
                sale.getDisplayCurrency(),
                sale.getDisplayExchangeRate(),
                sale.getPaymentMethod(),
                sale.getItemCount(),
                sale.getNote(),
                sale.getSoldAt()
        );
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID businessId, UUID orderId) {
        businessHelper.findAccessibleBusiness(businessId);
        Order order = findOrder(businessId, orderId);

        if (OrderStatus.PAID.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A paid order cannot be cancelled");
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

        return PageResponse.from(
                orderRepository.findAll(businessSpec.and(spec), pageable).map(orderMapper::toResponse));
    }

    @Override
    @Transactional
    public OrderResponse addOrderItem(UUID businessId, UUID orderId, AddOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);

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

        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderItem(UUID businessId, UUID orderId, UUID orderItemId, UpdateOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);

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

        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse removeOrderItem(UUID businessId, UUID orderId, UUID orderItemId) {
        Order order = validateOrderModification(businessId, orderId);

        boolean removed = order.getItems().removeIf(i -> i.getId().equals(orderItemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found");
        }

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

    private Order validateOrderModification(UUID businessId, UUID orderId) {
        Order order = findOrder(businessId, orderId);
        if (OrderStatus.PAID.equals(order.getStatus()) || OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify an order that is already paid or cancelled");
        }
        return order;
    }

    private void recalculateOrderTotals(Order order) {
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscount = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            BigDecimal itemGross = unitPrice.multiply(BigDecimal.valueOf(qty));
            BigDecimal itemDisc = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal itemNet = itemGross.subtract(itemDisc);
            if (itemNet.compareTo(BigDecimal.ZERO) < 0) {
                itemNet = BigDecimal.ZERO;
            }
            item.setLineTotal(itemNet);

            grossSubtotal = grossSubtotal.add(itemGross);
            itemDiscount = itemDiscount.add(itemDisc);
        }

        BigDecimal subtotal = grossSubtotal.subtract(itemDiscount);
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            subtotal = BigDecimal.ZERO;
        }
        BigDecimal orderDiscount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();

        if (orderDiscount.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount cannot exceed the order subtotal");
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;
        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setDiscountAmount(orderDiscount.setScale(scale, RoundingMode.HALF_UP));

        BigDecimal total = subtotal.subtract(orderDiscount).setScale(scale, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        order.setTotal(total);
    }

}
