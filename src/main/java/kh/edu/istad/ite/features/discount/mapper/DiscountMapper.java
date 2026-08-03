package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.DiscountSummaryResponse;
import kh.edu.istad.ite.features.discount.dto.DiscountTargetResponse;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.entity.DiscountTarget;
import kh.edu.istad.ite.shared.enums.DiscountTargetType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DiscountMapper {

    // Full response without target resolution (empty targets list).
    // Prefer the overload below when targets have already been loaded.
    public DiscountResponse toResponse(Discount discount) {

        return toResponse(discount, Collections.emptyList());
    }

    public DiscountResponse toResponse(Discount discount, List<DiscountTarget> targets) {
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
                discount.getApplicableChannels(),
                toTargetResponses(targets),
                discount.getStatus()
        );
    }

    // Lightweight view, embedded inside CouponResponse / MembershipTypeResponse.
    public DiscountSummaryResponse toSummary(Discount discount) {
        if (discount == null) {
            return null;
        }

        return new DiscountSummaryResponse(
                discount.getId(),
                discount.getName(),
                discount.getType(),
                discount.getScope(),
                discount.getValue()
        );
    }

    private List<DiscountTargetResponse> toTargetResponses(List<DiscountTarget> targets) {
        if (targets == null) {
            return Collections.emptyList();
        }

        return targets.stream()
                .map(this::toTargetResponse)
                .toList();
    }

    private DiscountTargetResponse toTargetResponse(DiscountTarget target) {
        boolean isItem = target.getTargetType() == DiscountTargetType.ITEM;

        return new DiscountTargetResponse(
                target.getId(),
                target.getTargetType(),
                isItem
                        ? (target.getItem() == null ? null : target.getItem().getId())
                        : (target.getItemGroup() == null ? null : target.getItemGroup().getId()),
                isItem
                        ? (target.getItem() == null ? null : target.getItem().getName())
                        : (target.getItemGroup() == null ? null : target.getItemGroup().getName())
        );
    }

}
