package kh.edu.istad.ite.features.customer.mapper;

import kh.edu.istad.ite.features.customer.dto.MembershipTypeResponse;
import kh.edu.istad.ite.features.customer.entity.MembershipType;
import kh.edu.istad.ite.features.discount.mapper.DiscountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MembershipTypeMapper {

    private  final DiscountMapper discountMapper;

    public MembershipTypeResponse toResponse(MembershipType membershipType) {
        if (membershipType == null) {
            return null;
        }

        return new MembershipTypeResponse(
                membershipType.getId(),
                membershipType.getBusiness() == null ? null : membershipType.getBusiness().getId(),
                membershipType.getTypeName(),
                membershipType.getRemark(),
                membershipType.getDiscount() == null ? null : membershipType.getDiscount().getId(),
                discountMapper.toSummary(membershipType.getDiscount()),
                membershipType.getStatus(),
                membershipType.getCreatedBy()
        );
    }
}
