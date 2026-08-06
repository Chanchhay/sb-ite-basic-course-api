package kh.edu.istad.ite.features.customer.mapper;

import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.customer.dto.CustomerResponse;
import kh.edu.istad.ite.features.customer.dto.GlobalCustomerResponse;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerMapper {

    private final MembershipTypeMapper membershipTypeMapper;

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return new CustomerResponse(
                customer.getId(),
                customer.getBusiness() == null ? null : customer.getBusiness().getId(),
                toGlobalCustomerResponse(customer.getGlobalCustomer()),
                membershipTypeMapper.toResponse(customer.getMembershipType()),
                toSalesChannelResponse(customer.getSalesChannel()),
                customer.getAddress(),
                customer.getTotalSpend(),
                customer.getBecameMembershipAt(),
                customer.getActive(),
                customer.getCreatedDate(),
                customer.getLastModifiedDate()
        );
    }

    private GlobalCustomerResponse toGlobalCustomerResponse(GlobalCustomer globalCustomer) {
        if (globalCustomer == null) {
            return null;
        }

        return new GlobalCustomerResponse(
                globalCustomer.getId(),
                globalCustomer.getKeycloakUserId(),
                globalCustomer.getEmail(),
                globalCustomer.getFullName(),
                globalCustomer.getPhoneNumber()
        );
    }

    private SalesChannelResponse toSalesChannelResponse(SalesChannel salesChannel) {
        if (salesChannel == null) {
            return null;
        }

        return SalesChannelResponse.builder()
                .id(salesChannel.getId())
                .name(salesChannel.getName())
                .code(salesChannel.getCode())
                .isActive(salesChannel.getIsActive())
                .build();
    }
}
