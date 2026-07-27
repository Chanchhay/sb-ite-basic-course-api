package kh.edu.istad.ite.features.order.mapper;

import kh.edu.istad.ite.features.order.dto.OrderItemResponse;
import kh.edu.istad.ite.features.order.dto.OrderResponse;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getBusiness().getId(),
                order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getInvoiceNumber(),
                order.getChannel(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getTotal(),
                order.getCurrency(),
                order.getNote(),
                items,
                order.getCreatedDate()
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getItem().getId(),
                item.getVariant() == null ? null : item.getVariant().getId(),
                item.getItemName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getLineTotal()
        );
    }
}
