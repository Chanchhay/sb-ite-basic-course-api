import re

with open("src/main/java/kh/edu/istad/ite/features/order/service/OrderServiceImpl.java", "r") as f:
    content = f.read()

# Add imports
imports = """
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.specification.FilterSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
"""
content = content.replace("import kh.edu.istad.ite.shared.helper.BusinessHelper;", "import kh.edu.istad.ite.shared.helper.BusinessHelper;\n" + imports)

# Add FilterSpecification dependency
deps = """
    private final StockEntryService stockEntryService;
    private final ReceiptService receiptService;
    private final FilterSpecification<Order> filterSpecification;
"""
content = content.replace("private final StockEntryService stockEntryService;\n    private final ReceiptService receiptService;", deps.strip())

# Add new methods at the end of the file before the last brace
new_methods = """
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> filterOrders(UUID businessId, RequestDto requestDto, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);
        
        // Add businessId filter implicitly
        kh.edu.istad.ite.config.filter.SearchRequestDto bizFilter = new kh.edu.istad.ite.config.filter.SearchRequestDto();
        bizFilter.setColumn("business.id"); // Wait, FilterSpecification handles nested? No, "business" usually requires join or just "business.id" if it maps to simple property, let's use join.
        bizFilter.setOperation(kh.edu.istad.ite.config.filter.SearchRequestDto.Operation.JOIN);
        bizFilter.setJoinTable("business");
        bizFilter.setColumn("id");
        bizFilter.setValue(businessId.toString()); // Wait, FilterSpecification maps string? FilterSpecification uses equal(..., requestDto.getValue()). Value is string. UUID needs to be converted if it doesn't match type.
        // Actually, just fetching by Spec might be tricky if we don't control the UUID type conversion in generic spec.
        // I will use a custom specification combined with FilterSpecification.
        
        org.springframework.data.jpa.domain.Specification<Order> spec = filterSpecification.getSearchSpecificationDynamic(
                requestDto.getSearchRequestDto(), requestDto.getGlobalOperator());
                
        org.springframework.data.jpa.domain.Specification<Order> businessSpec = (root, query, cb) -> 
                cb.equal(root.get("business").get("id"), businessId);
                
        return orderRepository.findAll(businessSpec.and(spec), pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse addOrderItem(UUID businessId, UUID orderId, AddOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);

        CreateOrderItemRequest createReq = new CreateOrderItemRequest(
                request.itemId(), request.variantId(), request.quantity(), request.discountAmount());
        
        OrderItem item = buildItem(businessId, createReq);
        order.addItem(item);
        
        recalculateOrderTotals(order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderItem(UUID businessId, UUID orderId, UUID itemId, UpdateOrderItemRequest request) {
        Order order = validateOrderModification(businessId, orderId);
        
        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
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
    public OrderResponse removeOrderItem(UUID businessId, UUID orderId, UUID itemId) {
        Order order = validateOrderModification(businessId, orderId);
        
        boolean removed = order.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found");
        }
        
        recalculateOrderTotals(order);
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
"""

content = content.rstrip()
if content.endswith("}"):
    content = content[:-1] + new_methods + "\n}\n"

with open("src/main/java/kh/edu/istad/ite/features/order/service/OrderServiceImpl.java", "w") as f:
    f.write(content)

