package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.features.order.entity.SelectedModifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID itemId,
        UUID variantId,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        List<SelectedModifier> selectedModifiers,
        BigDecimal modifierTotal,
        BigDecimal lineTotal
) {
}
