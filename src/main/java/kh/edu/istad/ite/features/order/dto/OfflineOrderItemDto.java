package kh.edu.istad.ite.features.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
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

        /**
         * The extras rung up on this line.
         *
         * They come off the shelf like anything else — a tub of pearls empties
         * whether it was scooped into one drink or ten — and an offline sale
         * that arrives without them leaves that stock uncounted.
         */
        @JsonProperty("add_on_ids")
        List<UUID> addOnIds,

        Integer quantity,

        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        BigDecimal subtotal
) {}
