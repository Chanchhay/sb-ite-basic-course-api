package kh.edu.istad.ite.features.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartSummaryResponse(
        int storeCount,
        int totalItems,
        List<StoreCart> stores
) {

    public record StoreCart(
            UUID cartId,
            UUID businessId,
            String slug,
            String name,
            String category,
            String logo,
            String location,
            String hours,
            String currency,
            boolean open,
            int itemCount,
            BigDecimal subtotal,
            List<Line> items
    ) {
    }

    public record Line(
            UUID cartItemId,
            UUID itemId,
            UUID variantId,
            String name,
            String description,
            String imageUrl,
            List<String> badges,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}