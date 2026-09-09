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
        /**
         * What the customer actually handed over, and what came back.
         *
         * The total is what was owed; these two are what happened at the
         * till, and only the till saw it. Without them a synced sale records
         * the exact money as tendered and no change given, so a receipt
         * reprinted later contradicts the one the customer was handed.
         *
         * Both are optional: a card or QR payment tenders the total by
         * definition, and an older till has no record of either.
         */
        @JsonProperty("paid_amount")
        BigDecimal paidAmount,
        @JsonProperty("change_amount")
        BigDecimal changeAmount,

        @JsonProperty("payment_method")
        PaymentMethodType paymentMethod,

        @JsonProperty("created_at")
        Instant createdAt,

        List<OfflineOrderItemDto> items
) {}
