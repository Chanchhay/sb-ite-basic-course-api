package kh.edu.istad.ite.features.order.mapper;

import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.order.dto.OrderItemResponse;
import kh.edu.istad.ite.features.order.dto.OrderResponse;
import kh.edu.istad.ite.features.order.dto.SaleResponse;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.entity.Sale;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final DiscountRepository discountRepository;

    @Mapping(target = "taxInclusionType", source = "taxInclusionType")
    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getLineNumber() == null ? 0 : i.getLineNumber()))
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getBusiness().getId(),
                order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getInvoiceNumber(),
                order.getChannel(),
                order.getStatus(),
                // Set by the service layer, which is the one with sale data —
                // the mapper only ever sees the order.
                null,
                order.getSubtotal(),
                order.getDiscountAmount(),
                resolveDiscountLabel(order.getDiscountAmount(), order.getSubtotal(), order.getDiscountCode(), order.getDiscountId()),
                order.getTaxRate(),
                order.getTaxAmount(),
                order.getTaxInclusionType(),
                order.getTotal(),
                order.getCurrency(),
                order.getDisplayCurrency(),
                order.getDisplayExchangeRate(),
                order.getNote(),
                order.isAwaitingPayLaterApproval(),
                items,
                order.getCreatedDate()
        );
    }

    /**
     * What to call a discount on a receipt — the coupon code the shopper
     * typed, the discount's own name, or a plain percentage when neither is
     * on record. Mirrors StorefrontCheckoutServiceImpl's receipt mapping, so
     * every channel names the same discount the same way.
     */
    private String resolveDiscountLabel(BigDecimal discountAmount, BigDecimal subtotal, String discountCode, java.util.UUID discountId) {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (StringUtils.hasText(discountCode)) {
            return discountCode;
        }

        if (discountId != null) {
            String name = discountRepository.findById(discountId).map(Discount::getName).orElse(null);
            if (StringUtils.hasText(name)) {
                return name;
            }
        }

        if (subtotal != null && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = discountAmount.multiply(new BigDecimal("100"))
                    .divide(subtotal, 0, RoundingMode.HALF_UP);
            return pct.toPlainString() + "% OFF";
        }

        return null;
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getItem().getId(),
                item.getVariant() == null ? null : item.getVariant().getId(),
                item.getVariant() == null ? null : item.getVariant().getVariantName(),
                item.getItemName(),
                item.getUnit() == null ? null : item.getUnit().getId(),
                item.getUnit() == null ? null : item.getUnit().getName(),
                item.getUnitFactor(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getLineTotal(),
                item.getItem() == null ? Boolean.TRUE : item.getItem().getTrackInventory(),
                item.getAddOns() == null ? List.of() : item.getAddOns().stream()
                        .map(addOn -> new OrderItemResponse.OrderItemAddOnResponse(
                                addOn.getAddOn() == null ? null : addOn.getAddOn().getId(),
                                addOn.getAddOnName(),
                                addOn.getUnitPrice()
                        ))
                        .toList(),
                item.getSelections() == null ? List.of() : item.getSelections().stream()
                        .map(selection -> new OrderItemResponse.OrderItemSelectionResponse(
                                selection.getAttributeName(),
                                selection.getValue(),
                                selection.display()
                        ))
                        .toList()
        );
    }

    @Mapping(target = "taxInclusionType", source = "taxInclusionType")
    public SaleResponse toSaleResponse(Sale sale) {
        if (sale == null) {
            return null;
        }

        kh.edu.istad.ite.features.customer.entity.Customer customer = sale.getCustomer();
        kh.edu.istad.ite.features.customer.entity.GlobalCustomer globalCustomer =
                customer == null ? null : customer.getGlobalCustomer();
        Order order = sale.getOrder();

        return new SaleResponse(
                sale.getId(),
                order.getId(),
                sale.getInvoiceNumber(),
                sale.getCashierId(),
                customer == null ? null : customer.getId(),
                globalCustomer == null ? null : globalCustomer.getFullName(),
                globalCustomer == null ? null : globalCustomer.getPhoneNumber(),
                globalCustomer == null ? null : globalCustomer.getEmail(),
                sale.getChannel(),
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                resolveDiscountLabel(sale.getDiscountAmount(), sale.getSubtotal(), order.getDiscountCode(), order.getDiscountId()),
                sale.getTaxRate(),
                sale.getTaxAmount(),
                sale.getTaxInclusionType(),
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
}
