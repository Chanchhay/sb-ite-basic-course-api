package kh.edu.istad.ite.features.order.entity;

import java.math.BigDecimal;

public record SelectedModifier(
        String groupName,
        String name,
        BigDecimal unitPrice,
        Integer quantity
) {
}