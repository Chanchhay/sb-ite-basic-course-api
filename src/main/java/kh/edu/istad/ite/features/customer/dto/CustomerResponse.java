package kh.edu.istad.ite.features.customer.dto;

import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID businessId,
        GlobalCustomerResponse globalCustomer,
        MembershipTypeResponse membershipType,
        SalesChannelResponse salesChannel,
        String address,
        BigDecimal totalSpend,
        LocalDateTime becameMembershipAt,
        Boolean active,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
) {
}
