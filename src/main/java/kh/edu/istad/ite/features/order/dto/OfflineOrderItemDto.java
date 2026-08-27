package kh.edu.istad.ite.features.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record OfflineOrderItemDto(
        @JsonProperty("product_id")
        UUID productId,

        Integer quantity,

        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        BigDecimal subtotal
) {}
