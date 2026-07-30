package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.PatchDiscountRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

@Component
public class DiscountMapper {

    public DiscountResponse toResponse(Discount discount) {
        if (discount == null) {
            return null;
        }

        return new DiscountResponse(
                discount.getId(),
                discount.getBusiness() == null ? null : discount.getBusiness().getId(),
                discount.getName(),
                discount.getDescription(),
                discount.getType(),
                discount.getRuleType(),
                discount.getBuyQuantity(),
                discount.getGetQuantity(),
                discount.getMinQuantity(),
                discount.getValue(),
                discount.getScope(),
                discount.getMinOrderAmount(),
                discount.getMaxDiscountAmount(),
                discount.getRequiresCoupon(),
                discount.getStartsAt(),
                discount.getEndsAt(),
                discount.getSelectedDays(),
                discount.getStatus()
        );
    }
}
