package kh.edu.istad.ite.features.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
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
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
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
import kh.edu.istad.ite.features.social.service.TelegramAlertService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String CURRENCY_KHR = "KHR";
    private static final int QR_VALIDITY_MINUTES = 5;
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessHelper businessHelper;
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
    private final ReceiptService receiptService;
    private final FilterSpecification<Order> filterSpecification;
    private final kh.edu.istad.ite.features.register.repository.RegisterSessionRepository registerSessionRepository;
    private final TelegramAlertService telegramAlertService;

    @Override
    @Transactional
    public OrderResponse createOrder(UUID businessId, CreateOrderRequest request) {
        Business business = businessHelper.findAccessibleBusiness(businessId);

        Order order = new Order();
        order.setBusiness(business);
        order.setChannel(request.channel());
        order.setStatus(OrderStatus.PENDING);
        order.setNote(request.note());
        order.setCurrency(resolveCurrency(request.currency(), business));
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
                    existing.setLineTotal(existing.getUnitPrice().multiply(BigDecimal.valueOf(existing.getQuantity())).subtract(itemDiscount));
                } else {
                    OrderItem item = buildItem(businessId, itemRequest);
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

        int scale = scaleFor(order);
        BigDecimal total = order.getTotal();

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

        Sale sale = new Sale();
        sale.setBusiness(business);
        sale.setOrder(order);
        sale.setCustomer(order.getCustomer());
        sale.setInvoiceNumber(order.getInvoiceNumber());
        sale.setCashierId(AuthHelper.currentUserId());
        sale.setChannel(order.getChannel());
        sale.setSubtotal(order.getSubtotal());
        sale.setDiscountAmount(order.getDiscountAmount());
        sale.setTotalAmount(total);
        sale.setPaidAmount(received);
        sale.setChangeAmount(received.subtract(total).setScale(scale, RoundingMode.HALF_UP));
        sale.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        sale.setCurrency(order.getCurrency());
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
                sale.getTotalAmount(),
                sale.getPaidAmount(),
                sale.getChangeAmount(),
                sale.getTotalCost(),
                sale.getCurrency(),
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

    private OrderItem buildItem(UUID businessId, CreateOrderItemRequest request) {
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

        if (unitPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Item has no price: " + item.getName());
        }

        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setVariant(variant);
        orderItem.setItemName(item.getName());
        orderItem.setQuantity(request.quantity());
        orderItem.setUnitPrice(unitPrice);
        orderItem.setUnitCost(BigDecimal.ZERO);
        orderItem.setDiscountAmount(BigDecimal.ZERO);
        orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));

        return orderItem;
    }

    private Order findOrder(UUID businessId, UUID orderId) {
        return orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has not been found"));
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
                .filter(i -> i.getItem().getId().equals(request.itemId()) &&
                        (i.getVariant() == null ? request.variantId() == null : i.getVariant().getId().equals(request.variantId())))
                .findFirst();

        if (existingOpt.isPresent()) {
            OrderItem existing = existingOpt.get();
            existing.setQuantity(existing.getQuantity() + request.quantity());
            BigDecimal itemDiscount = existing.getDiscountAmount() != null ? existing.getDiscountAmount() : BigDecimal.ZERO;
            if (request.discountAmount() != null) {
                itemDiscount = itemDiscount.add(request.discountAmount());
                existing.setDiscountAmount(itemDiscount);
            }
            existing.setLineTotal(existing.getUnitPrice().multiply(BigDecimal.valueOf(existing.getQuantity())).subtract(itemDiscount));
        } else {
            CreateOrderItemRequest createReq = new CreateOrderItemRequest(
                    request.itemId(), request.variantId(), request.quantity());

            OrderItem item = buildItem(businessId, createReq);
            if (request.discountAmount() != null) {
                item.setDiscountAmount(request.discountAmount());
                item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).subtract(request.discountAmount()));
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
        item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).subtract(discount));

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
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            subtotal = subtotal.add(item.getLineTotal());
        }

        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        if (discount.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount cannot exceed the order subtotal");
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(order.getCurrency()) ? 0 : 2;
        order.setSubtotal(subtotal.setScale(scale, RoundingMode.HALF_UP));
        order.setTotal(subtotal.subtract(discount).setScale(scale, RoundingMode.HALF_UP));
    }

}
