package kh.edu.istad.ite.features.customer;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.customer.dto.CustomerSelfProfileRequest;
import kh.edu.istad.ite.features.customer.dto.CustomerSelfProfileResponse;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.repository.GlobalCustomerRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a logged-in customer edits about themselves — phone/address for one
 * business — as opposed to {@code CustomerController}, which is the business
 * owner's dashboard view of their whole customer list. Used first by the
 * Telegram Mini App's "complete your profile" step, but not Telegram-specific:
 * anything authenticated as a customer (any channel) can call it.
 */
@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
public class CustomerSelfServiceController {

    private final BusinessRepository businessRepository;
    private final GlobalCustomerRepository globalCustomerRepository;
    private final CustomerIdentityService customerIdentityService;
    private final CustomerRepository customerRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * Lets a checkout screen (web, Telegram, or Messenger) check whether this
     * customer already has a phone number on file before deciding whether to
     * prompt for one — {@code phoneNumber} lives on {@link GlobalCustomer},
     * not per-business, so no {@code businessId} is needed here the way the
     * PUT below needs one for {@code address}.
     *
     * A number entered on the {@code /user-profile} page lands on
     * {@link UserProfile} instead — a separate, Keycloak-user-scoped record
     * this checkout flow never used to read — so a shopper who filled that in
     * first still got asked again here. Falling back to it when
     * {@link GlobalCustomer}'s own is empty means either place someone
     * entered it satisfies both from now on.
     */
    @GetMapping
    public CustomerSelfProfileResponse getMyProfile() {
        var keycloakUserId = AuthHelper.currentUserId();
        if (keycloakUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }

        String profilePhone = userProfileRepository.findById(keycloakUserId)
                .map(UserProfile::getPhoneNumber)
                .filter(StringUtils::hasText)
                .orElse(null);

        return globalCustomerRepository.findByKeycloakUserId(keycloakUserId)
                .map(gc -> new CustomerSelfProfileResponse(
                        gc.getFullName(),
                        gc.getEmail(),
                        gc.getGender(),
                        StringUtils.hasText(gc.getPhoneNumber()) ? gc.getPhoneNumber() : profilePhone,
                        null,
                        false))
                .orElseGet(() -> new CustomerSelfProfileResponse(null, null, null, profilePhone, null, false));
    }

    @PutMapping
    @Transactional
    public CustomerSelfProfileResponse updateMyProfile(@Valid @RequestBody CustomerSelfProfileRequest request) {
        var keycloakUserId = AuthHelper.currentUserId();
        if (keycloakUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }

        GlobalCustomer globalCustomer = globalCustomerRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile has not been found"));

        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store has not been found"));

        String normalizedPhone = TextHelper.trimToNull(request.phoneNumber());
        if (normalizedPhone != null && !normalizedPhone.equals(globalCustomer.getPhoneNumber())) {
            globalCustomerRepository.findByPhoneNumber(normalizedPhone)
                    .filter(existing -> !existing.getId().equals(globalCustomer.getId()))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "That phone number is already in use");
                    });
            globalCustomer.setPhoneNumber(normalizedPhone);

            // Kept in step with UserProfile too, so a number entered at
            // checkout also satisfies the /user-profile page instead of
            // being asked for a second time there.
            UserProfile userProfile = userProfileRepository.findById(keycloakUserId)
                    .orElseGet(() -> {
                        UserProfile created = new UserProfile();
                        created.setUserId(keycloakUserId);
                        return created;
                    });
            userProfile.setPhoneNumber(normalizedPhone);
            userProfileRepository.save(userProfile);
        }

        String normalizedEmail = TextHelper.trimToNull(request.email());
        if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(globalCustomer.getEmail())) {
            globalCustomerRepository.findByEmailIgnoreCase(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(globalCustomer.getId()))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already in use");
                    });
            globalCustomer.setEmail(normalizedEmail);
        }

        String firstName = TextHelper.trimToNull(request.firstName());
        String lastName = TextHelper.trimToNull(request.lastName());
        String fullName = (firstName != null || lastName != null)
                ? String.join(" ", firstName != null ? firstName : "", lastName != null ? lastName : "").trim()
                : globalCustomer.getFullName();
        globalCustomer.setFullName(fullName);
        String normalizedGender = TextHelper.trimToNull(request.gender());
        if (normalizedGender != null) {
            globalCustomer.setGender(normalizedGender);
        }
        globalCustomerRepository.save(globalCustomer);

        Customer customer = customerIdentityService.customerFor(business, globalCustomer);
        String normalizedAddress = TextHelper.trimToNull(request.address());
        if (normalizedAddress != null) {
            customer.setAddress(normalizedAddress);
        }
        customerRepository.save(customer);

        boolean profileComplete = StringUtils.hasText(globalCustomer.getEmail())
                && StringUtils.hasText(globalCustomer.getGender())
                && StringUtils.hasText(globalCustomer.getPhoneNumber())
                && StringUtils.hasText(customer.getAddress());

        return new CustomerSelfProfileResponse(
                globalCustomer.getFullName(),
                globalCustomer.getEmail(),
                globalCustomer.getGender(),
                globalCustomer.getPhoneNumber(),
                customer.getAddress(),
                profileComplete
        );
    }
}
