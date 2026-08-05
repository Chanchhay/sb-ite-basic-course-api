package kh.edu.istad.ite.features.customer.dto;

import kh.edu.istad.ite.features.discount.dto.DiscountSummaryResponse;
import kh.edu.istad.ite.shared.enums.RecordStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MembershipTypeResponse(
        UUID id,
        UUID businessOwnerId,
        String typeName,
        String remark,
        UUID discountId,
        DiscountSummaryResponse discount,
        RecordStatus status,
        String createdBy
) {
}
