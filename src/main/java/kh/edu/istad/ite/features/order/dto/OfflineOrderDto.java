package kh.edu.istad.ite.features.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OfflineOrderDto(
        String uuid,
        OrderChannel channel,
        OrderStatus status,
        BigDecimal subtotal,

        @JsonProperty("discount_amount")
        BigDecimal discountAmount,

        /**
         * What was charged in tax, as the till worked it out.
         *
         * The total already includes it, so a sale without these still
         * reconciles to the right money — but the recorded order shows no tax
         * line, and its subtotal and total no longer explain each other.
         */
        @JsonProperty("tax_rate")
        BigDecimal taxRate,

        @JsonProperty("tax_amount")
        BigDecimal taxAmount,

        @JsonProperty("tax_inclusion_type")
        TaxInclusionType taxInclusionType,

        BigDecimal total,

        @JsonProperty("payment_method")
        PaymentMethodType paymentMethod,

        @JsonProperty("created_at")
        Instant createdAt,

        List<OfflineOrderItemDto> items
) {}
