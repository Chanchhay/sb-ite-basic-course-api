package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import kh.edu.istad.ite.features.customer.dto.CreateCustomerRequest;
import kh.edu.istad.ite.features.customer.dto.CustomerResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateCustomerRequest;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.entity.MembershipType;
import kh.edu.istad.ite.features.customer.mapper.CustomerMapper;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.repository.GlobalCustomerRepository;
import kh.edu.istad.ite.features.customer.repository.MembershipTypeRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final BusinessHelper businessHelper;
    private final CustomerRepository customerRepository;
    private final GlobalCustomerRepository globalCustomerRepository;
    private final MembershipTypeRepository membershipTypeRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public CustomerResponse createCustomer(UUID businessId, CreateCustomerRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        Customer customer = new Customer();
        customer.setBusiness(business);
        customer.setGlobalCustomer(resolveGlobalCustomer(
                request.keycloakUserId(),
                request.email(),
                request.fullName(),
                request.phoneNumber()
        ));
        customer.setMembershipType(findMembershipTypeOrNull(request.membershipTypeId(), businessId));
        customer.setSalesChannel(findSalesChannelOrNull(request.salesChannelId()));
        customer.setAddress(TextHelper.trimToNull(request.address()));
        customer.setTotalSpend(request.totalSpend() == null ? BigDecimal.ZERO : request.totalSpend());
        customer.setBecameMembershipAt(request.becameMembershipAt());
        customer.setActive(request.active() == null || request.active());

        ensureCustomerDoesNotExist(businessId, customer.getGlobalCustomer(), null);

        try {
            return customerMapper.toResponse(customerRepository.saveAndFlush(customer));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> findAllCustomers(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);

        return customerRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId)
                .stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findCustomerById(UUID businessId, UUID customerId) {
        businessHelper.findOwnedBusiness(businessId);
        return customerMapper.toResponse(findCustomer(customerId, businessId));
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(UUID businessId, UUID customerId, UpdateCustomerRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Customer customer = findCustomer(customerId, businessId);

        if (hasGlobalCustomerUpdate(request)) {
            GlobalCustomer globalCustomer = resolveGlobalCustomer(
                    request.keycloakUserId(),
                    request.email(),
                    request.fullName(),
                    request.phoneNumber()
            );
            ensureCustomerDoesNotExist(businessId, globalCustomer, customerId);
            customer.setGlobalCustomer(globalCustomer);
        }
        if (request.membershipTypeId() != null) {
            customer.setMembershipType(findMembershipTypeOrNull(request.membershipTypeId(), businessId));
        }
        if (request.salesChannelId() != null) {
            customer.setSalesChannel(findSalesChannelOrNull(request.salesChannelId()));
        }
        if (request.address() != null) {
            customer.setAddress(TextHelper.trimToNull(request.address()));
        }
        if (request.totalSpend() != null) {
            customer.setTotalSpend(request.totalSpend());
        }
        if (request.becameMembershipAt() != null) {
            customer.setBecameMembershipAt(request.becameMembershipAt());
        }
        if (request.active() != null) {
            customer.setActive(request.active());
        }

        try {
            return customerMapper.toResponse(customerRepository.saveAndFlush(customer));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer already exists", e);
        }
    }

    @Override
    @Transactional
    public CustomerResponse activateCustomer(UUID businessId, UUID customerId) {
        businessHelper.findOwnedBusiness(businessId);
        Customer customer = findCustomer(customerId, businessId);
        customer.setActive(true);
        return customerMapper.toResponse(customerRepository.saveAndFlush(customer));
    }

    @Override
    @Transactional
    public CustomerResponse deactivateCustomer(UUID businessId, UUID customerId) {
        businessHelper.findOwnedBusiness(businessId);
        Customer customer = findCustomer(customerId, businessId);
        customer.setActive(false);
        return customerMapper.toResponse(customerRepository.saveAndFlush(customer));
    }

    @Override
    @Transactional
    public void deleteCustomer(UUID businessId, UUID customerId) {
        businessHelper.findOwnedBusiness(businessId);
        Customer customer = findCustomer(customerId, businessId);
        customerRepository.delete(customer);
        customerRepository.flush();
    }

    private Customer findCustomer(UUID customerId, UUID businessId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer has not been found"));
    }

    private MembershipType findMembershipTypeOrNull(UUID membershipTypeId, UUID businessId) {
        if (membershipTypeId == null) {
            return null;
        }

        return membershipTypeRepository.findByIdAndBusinessId(membershipTypeId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership type has not been found"));
    }

    private SalesChannel findSalesChannelOrNull(UUID salesChannelId) {
        if (salesChannelId == null) {
            return null;
        }

        return salesChannelRepository.findByIdAndIsActiveTrue(salesChannelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales channel has not been found"));
    }

    private GlobalCustomer resolveGlobalCustomer(
            UUID keycloakUserId,
            String email,
            String fullName,
            String phoneNumber
    ) {
        String normalizedEmail = normalize(email);
        String normalizedPhone = normalize(phoneNumber);
        String normalizedFullName = TextHelper.trimToNull(fullName);

        if (keycloakUserId == null && normalizedEmail == null && normalizedPhone == null && normalizedFullName == null) {
            return null;
        }

        GlobalCustomer found = lookupGlobalCustomer(keycloakUserId, normalizedEmail, normalizedPhone);
        if (found != null) {
            return backfillGlobalCustomer(found, keycloakUserId, normalizedEmail, normalizedPhone, normalizedFullName);
        }

        GlobalCustomer created = new GlobalCustomer();
        created.setKeycloakUserId(keycloakUserId);
        created.setEmail(normalizedEmail);
        created.setFullName(normalizedFullName);
        created.setPhoneNumber(normalizedPhone);
        return globalCustomerRepository.saveAndFlush(created);
    }

    private GlobalCustomer lookupGlobalCustomer(UUID keycloakUserId, String email, String phoneNumber) {
        if (keycloakUserId != null) {
            return globalCustomerRepository.findByKeycloakUserId(keycloakUserId).orElse(null);
        }
        if (StringUtils.hasText(email)) {
            return globalCustomerRepository.findByEmailIgnoreCase(email).orElse(null);
        }
        if (StringUtils.hasText(phoneNumber)) {
            return globalCustomerRepository.findByPhoneNumber(phoneNumber).orElse(null);
        }
        return null;
    }

    private GlobalCustomer backfillGlobalCustomer(
            GlobalCustomer globalCustomer,
            UUID keycloakUserId,
            String email,
            String phoneNumber,
            String fullName
    ) {
        boolean dirty = false;

        if (globalCustomer.getKeycloakUserId() == null && keycloakUserId != null) {
            globalCustomer.setKeycloakUserId(keycloakUserId);
            dirty = true;
        }
        if (!StringUtils.hasText(globalCustomer.getEmail()) && StringUtils.hasText(email)) {
            globalCustomer.setEmail(email);
            dirty = true;
        }
        if (!StringUtils.hasText(globalCustomer.getPhoneNumber()) && StringUtils.hasText(phoneNumber)) {
            globalCustomer.setPhoneNumber(phoneNumber);
            dirty = true;
        }
        if (StringUtils.hasText(fullName) && !fullName.equals(globalCustomer.getFullName())) {
            globalCustomer.setFullName(fullName);
            dirty = true;
        }

        return dirty ? globalCustomerRepository.saveAndFlush(globalCustomer) : globalCustomer;
    }

    private void ensureCustomerDoesNotExist(UUID businessId, GlobalCustomer globalCustomer, UUID currentCustomerId) {
        if (globalCustomer == null) {
            return;
        }

        customerRepository.findByBusiness_IdAndGlobalCustomer_Id(businessId, globalCustomer.getId())
                .filter(existing -> !existing.getId().equals(currentCustomerId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer already exists for this business");
                });
    }

    private boolean hasGlobalCustomerUpdate(UpdateCustomerRequest request) {
        return request.keycloakUserId() != null
                || request.email() != null
                || request.fullName() != null
                || request.phoneNumber() != null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase();
    }
}
