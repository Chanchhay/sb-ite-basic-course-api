package kh.edu.istad.ite.features.discount.dto;

import kh.edu.istad.ite.shared.enums.DiscountTargetType;

import java.util.UUID;

public record DiscountTargetResponse(

        UUID id,
        DiscountTargetType targetType,
        UUID targetId,
        String targetName

) {
}
