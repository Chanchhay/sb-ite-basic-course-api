package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.repository.GlobalCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerIdentityService {


    private static final String UNKNOWN_KEYCLOAK_ID = "unknown";

    private final GlobalCustomerRepository globalCustomerRepository;
    private final CustomerRepository customerRepository;


    @Transactional
    public GlobalCustomer resolve(
            UUID keycloakUserId,
            String email,
            String phoneNumber,
            String fullName
    ) {
        String normalisedEmail = normalise(email);
        String normalisedPhone = normalise(phoneNumber);

        GlobalCustomer found = lookup(keycloakUserId, normalisedEmail, normalisedPhone).orElse(null);

        if (found == null) {
            GlobalCustomer created = new GlobalCustomer();
            created.setKeycloakUserId(keycloakUserId);
            created.setEmail(normalisedEmail);
            created.setPhoneNumber(normalisedPhone);
            created.setFullName(fullName);

            GlobalCustomer saved = globalCustomerRepository.save(created);

            log.info("Created global customer {} (keycloak={}, email={})",
                    saved.getId(), keycloakUserId, normalisedEmail);

            return saved;
        }

        return backfill(found, keycloakUserId, normalisedEmail, normalisedPhone, fullName);
    }

    @Transactional
    public Customer customerFor(Business business, GlobalCustomer globalCustomer) {
        return customerRepository
                .findByBusiness_IdAndGlobalCustomer_Id(business.getId(), globalCustomer.getId())
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setBusiness(business);
                    customer.setGlobalCustomer(globalCustomer);
                    return customerRepository.save(customer);
                });
    }

    public static UUID parseKeycloakId(String rawId) {
        if (!StringUtils.hasText(rawId) || UNKNOWN_KEYCLOAK_ID.equalsIgnoreCase(rawId)) {
            return null;
        }

        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            log.warn("Keycloak returned an id that is not a UUID: {}", rawId);
            return null;
        }
    }


    private Optional<GlobalCustomer> lookup(UUID keycloakUserId, String email, String phone) {
        if (keycloakUserId != null) {
            Optional<GlobalCustomer> byKeycloak = globalCustomerRepository.findByKeycloakUserId(keycloakUserId);
            if (byKeycloak.isPresent()) {
                return byKeycloak;
            }
        }

        if (StringUtils.hasText(email)) {
            Optional<GlobalCustomer> byEmail = globalCustomerRepository.findByEmailIgnoreCase(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        if (StringUtils.hasText(phone)) {
            return globalCustomerRepository.findByPhoneNumber(phone);
        }

        return Optional.empty();
    }


    private GlobalCustomer backfill(
            GlobalCustomer customer,
            UUID keycloakUserId,
            String email,
            String phone,
            String fullName
    ) {
        boolean dirty = false;

        if (customer.getKeycloakUserId() == null && keycloakUserId != null) {
            customer.setKeycloakUserId(keycloakUserId);
            dirty = true;
        } else if (keycloakUserId != null && !keycloakUserId.equals(customer.getKeycloakUserId())) {

            log.warn("Global customer {} is linked to keycloak {} but {} was presented. Not relinking.",
                    customer.getId(), customer.getKeycloakUserId(), keycloakUserId);
        }

        if (!StringUtils.hasText(customer.getEmail()) && StringUtils.hasText(email)) {
            customer.setEmail(email);
            dirty = true;
        }

        if (!StringUtils.hasText(customer.getPhoneNumber()) && StringUtils.hasText(phone)) {
            customer.setPhoneNumber(phone);
            dirty = true;
        }

        if (StringUtils.hasText(fullName) && !fullName.equals(customer.getFullName())) {
            customer.setFullName(fullName);
            dirty = true;
        }

        return dirty ? globalCustomerRepository.save(customer) : customer;
    }

    private String normalise(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();

        if ("N/A".equalsIgnoreCase(trimmed) || UNKNOWN_KEYCLOAK_ID.equalsIgnoreCase(trimmed)) {
            return null;
        }

        return trimmed.toLowerCase();
    }
}