package kh.edu.istad.ite.features.customer.mapper;

import kh.edu.istad.ite.features.customer.dto.MembershipTypeResponse;
import kh.edu.istad.ite.features.customer.entity.MembershipType;
import org.springframework.stereotype.Component;

@Component
public class MembershipTypeMapper {

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
                membershipType.getStatus(),
                membershipType.getCreatedBy()
        );
    }
}
