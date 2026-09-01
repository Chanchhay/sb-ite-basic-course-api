package kh.edu.istad.ite.features.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record OfflineOrderItemDto(
        @JsonProperty("product_id")
        UUID productId,

        /**
         * What the line was actually sold as.
         *
         * A till that has lost its connection still sells options and packs.
         * Without these the sale reconstructs as one of the base item, so a
         * bag of ten kilos takes one kilo off the shelf and the count never
         * recovers — the sale is settled by then.
         *
         * The pack's factor is deliberately not accepted here: what a pack
         * holds is the shop's to say, and it is read from the item's own
         * conversion rather than from whatever the till sent.
         */
        @JsonProperty("variant_id")
        UUID variantId,

        @JsonProperty("unit_id")
        UUID unitId,

        Integer quantity,

        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        BigDecimal subtotal
) {}
