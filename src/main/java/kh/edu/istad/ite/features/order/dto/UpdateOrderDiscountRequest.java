package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateOrderDiscountRequest(
        @PositiveOrZero
        BigDecimal discountAmount,

        UUID discountId,

        String discountCode,

        /**
         * More than one simultaneously-active catalog discount (e.g. two
         * item-scoped promos auto-matched to different lines, with no single
         * one explicitly picked). Each is resolved and attributed to its own
         * matching line independently. Null or a single entry behaves the
         * same as the plain discountId field.
         */
        List<UUID> discountIds
) {
}
