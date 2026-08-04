package kh.edu.istad.ite.features.discount.dto;

import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record DiscountSummaryResponse(

        UUID id,
        String name,
        DiscountType type,
        DiscountScope scope,
        BigDecimal value

) {

}
