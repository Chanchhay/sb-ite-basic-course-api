package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.entity.Discount;
import org.springframework.stereotype.Component;

@Component
public class DiscountMapper {

    public DiscountResponse toResponse(Discount discount) {
        if (discount == null) {
            return null;
        }

        return new DiscountResponse(
                discount.getId(),
                discount.getBusiness().getId(),
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
                discount.getStatus(),
                discount.getBranchId(),
                discount.getCreatedBy(),
                discount.getCreatedDate(),
                discount.getLastModifiedDate()
        );
    }
}