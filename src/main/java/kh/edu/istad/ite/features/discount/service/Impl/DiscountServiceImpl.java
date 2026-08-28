package kh.edu.istad.ite.features.discount.service.Impl;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.entity.DiscountTarget;
import kh.edu.istad.ite.features.discount.mapper.DiscountMapper;
import kh.edu.istad.ite.features.discount.repository.CouponRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.customer.repository.MembershipTypeRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountTargetRepository;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationRequest;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import kh.edu.istad.ite.features.notification.service.NotificationCommandService;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.*;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final BusinessHelper businessHelper;
    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final MembershipTypeRepository membershipTypeRepository;
    private final DiscountMapper discountMapper;
    private final DiscountTargetRepository discountTargetRepository;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final UserProfileRepository userProfileRepository;
    private final NotificationCommandService notificationCommandService;
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;

    @Override
    @Transactional
    public DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        String name = TextHelper.trimRequired(request.name(), "Discount name cannot be empty");
        if (discountRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A discount with this name already exists in your store. Please choose a different name.");
        }

        DiscountType type = request.type() != null ? request.type() : DiscountType.PERCENTAGE;
        DiscountRuleType ruleType = type == DiscountType.BUY_X_GET_Y
                ? DiscountRuleType.BUY_X_GET_Y
                : (request.ruleType() != null ? request.ruleType() : DiscountRuleType.NO_CONDITION);
        DiscountScope scope = normalizeScope(request.scope());

        validateRule(ruleType, request.buyQuantity(), request.getQuantity(), request.minQuantity());
        if (request.value() != null) {
            validateValue(type, request.value());
        }
        validateTargetsMatchScope(scope, request.targetItemIds(), request.targetItemGroupIds());

        Discount discount = new Discount();
        discount.setBusiness(business);
        discount.setName(name);
        discount.setDescription(TextHelper.trimToNull(request.description()));
        discount.setType(type);
        discount.setRuleType(ruleType);
        discount.setBuyQuantity(request.buyQuantity());
        discount.setGetQuantity(request.getQuantity());
        discount.setMinQuantity(request.minQuantity());
        discount.setValue(type == DiscountType.BUY_X_GET_Y ? BigDecimal.ZERO : (request.value() != null ? request.value() : BigDecimal.ZERO));
        discount.setScope(scope);
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setRequiresCoupon(request.requiresCoupon() != null && request.requiresCoupon());
        discount.setStartsAt(request.startsAt() != null ? request.startsAt() : LocalDateTime.now());
        discount.setEndsAt(request.endsAt() != null ? request.endsAt() : LocalDateTime.now().plusYears(10));
        discount.setSelectedDays(request.selectedDays());
        discount.setApplicableChannels(normalizeChannels(request.applicableChannels()));
        discount.setStatus(RecordStatus.ACTIVE);

        if (scope == DiscountScope.SPECIFIC_ITEMS && request.targetItemIds() != null && !request.targetItemIds().isEmpty()) {
            validateNoConflictingItemTargets(businessId, request.targetItemIds(), null);
        }

        try {
            Discount savedDiscount = discountRepository.saveAndFlush(discount);
            List<DiscountTarget> targets = replaceTargets(savedDiscount, businessId, request.targetItemIds(), request.targetItemGroupIds());
            handleStorewidePauseIfRequested(businessId, savedDiscount, request.pauseOtherDiscounts());
            DiscountResponse response = discountMapper.toResponse(savedDiscount, targets);
            notifyShopUsersAboutDiscount(business, savedDiscount);
            return response;
        } catch (DataIntegrityViolationException e) {
            String rootCause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            log.error("Failed to save discount rule for business {}: {}", businessId, rootCause, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database constraint error: " + rootCause, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponse> findAllDiscounts(UUID businessId, Pageable pageable) {

        businessHelper.findOwnedBusiness(businessId);

        return discountRepository.findAllByBusinessId(businessId, pageable)
                .map(discount -> {
                    List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(discount.getId());

                    return discountMapper.toResponse(discount, targets);
                });

    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse findDiscountById(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);
        List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(discount.getId());
        return discountMapper.toResponse(discount, targets);
    }

    @Override
    @Transactional
    public DiscountResponse updateDiscount(UUID businessId, UUID discountId, UpdateDiscountRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Discount name cannot be empty");
            if (!name.equals(discount.getName())) {
                if (discountRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, discountId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount with this name already exists");
                }
                discount.setName(name);
            }
        }
        if (request.description() != null) {
            discount.setDescription(TextHelper.trimToNull(request.description()));
        }

        DiscountType type = request.type() != null ? DiscountType.valueOf(request.type()) : discount.getType();
        DiscountRuleType ruleType = type == DiscountType.BUY_X_GET_Y
                ? DiscountRuleType.BUY_X_GET_Y
                : (request.ruleType() != null ? DiscountRuleType.valueOf(request.ruleType()) : discount.getRuleType());
        DiscountScope scope = request.scope() != null ? request.scope() : discount.getScope();

        Integer buyQuantity = request.buyQuantity() != null ? request.buyQuantity() : discount.getBuyQuantity();
        Integer getQuantity = request.getQuantity() != null ? request.getQuantity() : discount.getGetQuantity();
        Integer minQuantity = request.minQuantity() != null ? request.minQuantity() : discount.getMinQuantity();
        validateRule(ruleType, buyQuantity, getQuantity, minQuantity);

        BigDecimal value = type == DiscountType.BUY_X_GET_Y
                ? BigDecimal.ZERO
                : (request.value() != null ? request.value() : discount.getValue());
        validateValue(type, value);
        validateTargetsMatchScope(scope, request.targetItemIds(), request.targetItemGroupIds());

        RecordStatus newStatus = request.status() != null ? RecordStatus.valueOf(request.status()) : discount.getStatus();
        RecordStatus oldStatus = discount.getStatus();

        if (newStatus == RecordStatus.ACTIVE && scope == DiscountScope.SPECIFIC_ITEMS && request.targetItemIds() != null && !request.targetItemIds().isEmpty()) {
            validateNoConflictingItemTargets(businessId, request.targetItemIds(), discountId);
        }

        discount.setType(type);
        discount.setRuleType(ruleType);
        discount.setScope(scope);
        discount.setBuyQuantity(buyQuantity);
        discount.setGetQuantity(getQuantity);
        discount.setMinQuantity(minQuantity);
        discount.setValue(value);
        discount.setStatus(newStatus);

        if (request.minOrderAmount() != null) {
            discount.setMinOrderAmount(request.minOrderAmount());
        }
        if (request.maxDiscountAmount() != null) {
            discount.setMaxDiscountAmount(request.maxDiscountAmount());
        }
        if (request.requiresCoupon() != null) {
            discount.setRequiresCoupon(request.requiresCoupon());
        }
        if (request.startsAt() != null) {
            discount.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            discount.setEndsAt(request.endsAt());
        }
        if (request.selectedDays() != null) {
            discount.setSelectedDays(request.selectedDays());
        }
        if (request.applicableChannels() != null) {
            discount.setApplicableChannels(normalizeChannels(request.applicableChannels()));
        }

        try {
            Discount savedDiscount = discountRepository.saveAndFlush(discount);
            List<DiscountTarget> targets = replaceTargets(savedDiscount, businessId, request.targetItemIds(), request.targetItemGroupIds());

            if (oldStatus == RecordStatus.ACTIVE && newStatus == RecordStatus.INACTIVE) {
                handleStorewideRestoreIfApplicable(businessId, savedDiscount);
            } else if (newStatus == RecordStatus.ACTIVE && Boolean.TRUE.equals(request.pauseOtherDiscounts())) {
                handleStorewidePauseIfRequested(businessId, savedDiscount, true);
            }

            return discountMapper.toResponse(savedDiscount, targets);
        } catch (DataIntegrityViolationException e) {
            String rootCause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            log.error("Failed to update discount rule {} for business {}: {}", discountId, businessId, rootCause, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database constraint error: " + rootCause, e);
        }
    }

    @Override
    @Transactional
    public DiscountResponse activateDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        if (discount.getScope() == DiscountScope.SPECIFIC_ITEMS) {
            List<UUID> targetItemIds = discountTargetRepository.findAllByDiscountId(discountId)
                    .stream()
                    .filter(t -> t.getTargetType() == DiscountTargetType.ITEM && t.getItem() != null)
                    .map(t -> t.getItem().getId())
                    .toList();
            if (!targetItemIds.isEmpty()) {
                validateNoConflictingItemTargets(businessId, targetItemIds, discountId);
            }
        }

        discount.setStatus(RecordStatus.ACTIVE);
        Discount saved = discountRepository.saveAndFlush(discount);
        List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(saved.getId());
        return discountMapper.toResponse(saved, targets);
    }

    @Override
    @Transactional
    public DiscountResponse deactivateDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        discount.setStatus(RecordStatus.INACTIVE);
        handleStorewideRestoreIfApplicable(businessId, discount);

        Discount saved = discountRepository.saveAndFlush(discount);
        List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(saved.getId());
        return discountMapper.toResponse(saved, targets);
    }

    @Override
    @Transactional
    public void deleteDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        if (couponRepository.existsByDiscount_Id(discountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete discount that is used by coupons");
        }
        if (membershipTypeRepository.existsByDiscount_Id(discountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete discount that is used by membership types");
        }

        discountTargetRepository.findAllByDiscountId(discountId).forEach(discountTargetRepository::delete);
        discountTargetRepository.flush();

        discountRepository.delete(discount);
        discountRepository.flush();
    }

    @Override
    public List<DiscountResponse> findApplicableDiscounts(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId
    ) {
        businessHelper.findBusiness(businessId);

        List<Discount> candidates = discountRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId);
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = LocalDate.now().getDayOfWeek();

        List<DiscountResponse> result = new ArrayList<>();

        for (Discount discount : candidates) {
            if (discount.getStatus() != RecordStatus.ACTIVE) {
                continue;
            }
            if (!isWithinWindow(discount, now)){
                continue;
            }
            if (!isOnSelectedDay(discount, today)){
                continue;
            }
            if (!isChannelAllowed(discount, channel)){
                continue;
            }
            List<DiscountTarget> targets = discountTargetRepository.findAllByDiscountId(discount.getId());

            if (!matchesTarget(discount, targets, itemId, itemGroupId)){
                continue;
            }
            result.add(discountMapper.toResponse(discount, targets));
        }

        return result;
    }

    private Discount findDiscount(UUID discountId, UUID businessId) {
        return discountRepository.findByIdAndBusinessId(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private List<OrderChannel> normalizeChannels(List<OrderChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        return channels.stream().distinct().toList();
    }

    private void validateRule(DiscountRuleType ruleType, Integer buyQuantity, Integer getQuantity, Integer minQuantity) {
        switch (ruleType) {
            case BUY_X_GET_Y -> {
                if (buyQuantity == null || buyQuantity <= 0 || getQuantity == null || getQuantity <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "buyQuantity and getQuantity must be positive for BUY_X_GET_Y rule"
                    );
                }
            }
            case MIN_QUANTITY -> {
                if (minQuantity == null || minQuantity <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minQuantity must be positive for MIN_QUANTITY rule");
                }
            }
            case MIN_ORDER_AMOUNT, NO_CONDITION -> {
            }
        }
    }

    private void validateDateRange(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt cannot be before startsAt");
        }
    }

    private DiscountScope normalizeScope(DiscountScope scope) {
        if (scope == null || scope == DiscountScope.ORDER || scope == DiscountScope.ALL_ITEMS) {
            return DiscountScope.ALL_ITEMS;
        }
        if (scope == DiscountScope.ITEM || scope == DiscountScope.SPECIFIC_ITEMS) {
            return DiscountScope.SPECIFIC_ITEMS;
        }
        if (scope == DiscountScope.CATEGORY || scope == DiscountScope.SPECIFIC_CATEGORIES) {
            return DiscountScope.SPECIFIC_CATEGORIES;
        }
        return scope;
    }

    private void validateTargetsMatchScope(
            DiscountScope scope,
            List<UUID> targetItemIds,
            List<UUID> targetItemGroupIds
    ) {
        DiscountScope normalized = normalizeScope(scope);
        boolean hasItemTargets = targetItemIds != null && !targetItemIds.isEmpty();
        boolean hasGroupTargets = targetItemGroupIds != null && !targetItemGroupIds.isEmpty();

        if (normalized == DiscountScope.SPECIFIC_ITEMS && !hasItemTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemIds is required when scope is SPECIFIC_ITEMS"
            );
        }
        if (normalized == DiscountScope.SPECIFIC_CATEGORIES && !hasGroupTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemGroupIds is required when scope is SPECIFIC_CATEGORIES"
            );
        }
    }

    // Replaces the full target set for this discount, based on whichever of
    // targetItemIds / targetItemGroupIds is non-null. Passing null for one of
    // them leaves the existing targets of that type untouched; passing an
    // empty list clears them.
    private List<DiscountTarget> replaceTargets(
            Discount discount,
            UUID businessId,
            List<UUID> targetItemIds,
            List<UUID> targetItemGroupIds
    ) {
        if (targetItemIds != null) {
            discountTargetRepository.findAllByDiscountId(discount.getId())
                    .stream()
                    .filter(target -> target.getTargetType() == DiscountTargetType.ITEM)
                    .forEach(discountTargetRepository::delete);

            for (UUID itemId : targetItemIds.stream().distinct().toList()) {
                Item item = itemRepository.findByIdAndBusinessId(itemId, businessId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item has not been found"));

                DiscountTarget target = new DiscountTarget();
                target.setDiscount(discount);
                target.setTargetType(DiscountTargetType.ITEM);
                target.setItem(item);
                discountTargetRepository.save(target);
            }
        }

        if (targetItemGroupIds != null) {
            discountTargetRepository.findAllByDiscountId(discount.getId())
                    .stream()
                    .filter(target -> target.getTargetType() == DiscountTargetType.ITEM_GROUP)
                    .forEach(discountTargetRepository::delete);

            for (UUID itemGroupId : targetItemGroupIds.stream().distinct().toList()) {
                ItemGroup itemGroup = itemGroupRepository.findByIdAndBusinessId(itemGroupId, businessId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item group has not been found"));

                DiscountTarget target = new DiscountTarget();
                target.setDiscount(discount);
                target.setTargetType(DiscountTargetType.ITEM_GROUP);
                target.setItemGroup(itemGroup);
                discountTargetRepository.save(target);
            }
        }

        discountTargetRepository.flush();
        return discountTargetRepository.findAllByDiscountId(discount.getId());
    }

    private boolean isWithinWindow(Discount discount, LocalDateTime now) {
        if (discount.getStartsAt() != null && now.isBefore(discount.getStartsAt())) {
            return false;
        }
        return discount.getEndsAt() == null || !now.isAfter(discount.getEndsAt());
    }

    private boolean isOnSelectedDay(Discount discount, DayOfWeek today) {
        List<DayOfWeek> selectedDays = discount.getSelectedDays();
        if (selectedDays == null || selectedDays.isEmpty()) {
            return true;
        }

        return selectedDays.stream().anyMatch(day -> day == today);
    }

    private boolean isChannelAllowed(Discount discount, OrderChannel channel) {
        List<OrderChannel> applicableChannels = discount.getApplicableChannels();
        if (applicableChannels == null || applicableChannels.isEmpty()) {
            return true;
        }
        if (channel == null) {
            return false;
        }

        return applicableChannels.contains(channel);
    }

    private boolean matchesTarget(Discount discount, List<DiscountTarget> targets, UUID itemId, UUID itemGroupId) {
        DiscountScope scope = normalizeScope(discount.getScope());
        if (scope == DiscountScope.ALL_ITEMS) {
            return true;
        }
        if (scope == DiscountScope.SPECIFIC_ITEMS) {
            if (itemId == null) {
                return false;
            }
            return targets.stream()
                    .anyMatch(target -> target.getItem() != null && target.getItem().getId().equals(itemId));
        }
        if (scope == DiscountScope.SPECIFIC_CATEGORIES) {
            if (itemGroupId == null) {
                return false;
            }
            return targets.stream()
                    .anyMatch(target -> target.getItemGroup() != null && target.getItemGroup().getId().equals(itemGroupId));
        }

        return true;
    }

    private void validateValue(DiscountType type, BigDecimal value) {
        if (value == null || type == DiscountType.BUY_X_GET_Y) return;
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value cannot be negative");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage value cannot exceed 100");
        }
    }

    private void validateNoConflictingItemTargets(UUID businessId, List<UUID> targetItemIds, UUID currentDiscountId) {
        if (targetItemIds == null || targetItemIds.isEmpty()) {
            return;
        }
        List<DiscountTarget> conflicts = discountTargetRepository.findActiveItemTargetsByBusinessIdAndItemIds(
                businessId,
                targetItemIds,
                currentDiscountId
        );
        if (!conflicts.isEmpty()) {
            DiscountTarget firstConflict = conflicts.get(0);
            String itemName = firstConflict.getItem() != null ? firstConflict.getItem().getName() : "Selected item";
            String discountName = firstConflict.getDiscount() != null ? firstConflict.getDiscount().getName() : "another active discount";
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Item '" + itemName + "' already has an active discount ('" + discountName + "'). An item cannot be assigned to multiple active discounts."
            );
        }
    }

    private void handleStorewidePauseIfRequested(UUID businessId, Discount discount, Boolean pauseOthers) {
        DiscountScope scope = normalizeScope(discount.getScope());
        if (Boolean.TRUE.equals(pauseOthers) && (scope == DiscountScope.ALL_ITEMS || scope == DiscountScope.ORDER)) {
            List<Discount> activeOthers = discountRepository.findAllByBusinessIdAndStatusAndIdNot(
                    businessId,
                    RecordStatus.ACTIVE,
                    discount.getId()
            );
            if (!activeOthers.isEmpty()) {
                // Merge with any already-tracked paused ids rather than overwriting, so a
                // second "pause others" call (e.g. re-saving this discount after new
                // discounts were created) doesn't orphan discounts paused by an earlier call.
                java.util.LinkedHashSet<UUID> pausedIds = new java.util.LinkedHashSet<>();
                if (discount.getPausedDiscountIds() != null) {
                    pausedIds.addAll(discount.getPausedDiscountIds());
                }
                activeOthers.forEach(other -> pausedIds.add(other.getId()));
                discount.setPausedDiscountIds(new ArrayList<>(pausedIds));
                for (Discount other : activeOthers) {
                    other.setStatus(RecordStatus.INACTIVE);
                    discountRepository.save(other);
                }
                discountRepository.save(discount);
                discountRepository.flush();
            }
        }
    }

    private void handleStorewideRestoreIfApplicable(UUID businessId, Discount discount) {
        if (discount.getPausedDiscountIds() != null && !discount.getPausedDiscountIds().isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (UUID pausedId : discount.getPausedDiscountIds()) {
                discountRepository.findByIdAndBusinessId(pausedId, businessId).ifPresent(other -> {
                    // Don't resurrect a discount that expired while it was paused.
                    if (other.getEndsAt() != null && other.getEndsAt().isBefore(now)) {
                        return;
                    }
                    other.setStatus(RecordStatus.ACTIVE);
                    discountRepository.save(other);
                });
            }
            discount.setPausedDiscountIds(null);
            discountRepository.save(discount);
            discountRepository.flush();
        }
    }

    private void notifyShopUsersAboutDiscount(Business business, Discount discount) {
        try {
            UUID businessId = business.getId();
            Set<String> recipientIds = new LinkedHashSet<>();
            if (business.getKeycloakUserId() != null) {
                recipientIds.add(business.getKeycloakUserId().toString());
            }

            List<UserProfile> staffList = userProfileRepository.findByBusinessIdOrderByJoinedAtDesc(businessId);
            if (staffList != null) {
                for (UserProfile staff : staffList) {
                    if (staff.getStaffStatus() == RecordStatus.ACTIVE && staff.getUserId() != null) {
                        recipientIds.add(staff.getUserId().toString());
                    }
                }
            }

            if (recipientIds.isEmpty()) {
                return;
            }

            String creatorUserId = SecurityUtils.extractUserId();
            String creatorName = "Store Admin";
            if (creatorUserId != null && !creatorUserId.isBlank()) {
                try {
                    UserRepresentation user = keycloak.realm(props.getTargetRealm()).users().get(creatorUserId).toRepresentation();
                    if (user != null) {
                        String first = user.getFirstName();
                        String last = user.getLastName();
                        if ((first != null && !first.isBlank()) || (last != null && !last.isBlank())) {
                            creatorName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                        } else if (user.getUsername() != null && !user.getUsername().isBlank()) {
                            creatorName = user.getUsername();
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String valueDesc = "";
            if (discount.getType() == DiscountType.PERCENTAGE) {
                valueDesc = discount.getValue() + "% off";
            } else if (discount.getType() == DiscountType.FIXED_AMOUNT) {
                valueDesc = "$" + discount.getValue() + " off";
            } else if (discount.getType() == DiscountType.BUY_X_GET_Y) {
                valueDesc = "Buy " + discount.getBuyQuantity() + " Get " + discount.getGetQuantity();
            }

            String storeName = business.getDisplayName() != null && !business.getDisplayName().isBlank()
                    ? business.getDisplayName()
                    : (business.getBusinessName() != null && !business.getBusinessName().isBlank() ? business.getBusinessName() : "your store");

            String content = String.format("A new discount \"%s\"%s has been created for %s.",
                    discount.getName(),
                    valueDesc.isBlank() ? "" : " (" + valueDesc + ")",
                    storeName);

            CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                    creatorUserId != null && !creatorUserId.isBlank() ? creatorUserId : (business.getKeycloakUserId() != null ? business.getKeycloakUserId().toString() : "system"),
                    creatorName,
                    new ArrayList<>(recipientIds),
                    NotificationType.PROMOTION,
                    "New Discount: " + discount.getName(),
                    content,
                    "/sales/discounts"
            );

            UUID senderTenantId = business.getKeycloakUserId() != null ? business.getKeycloakUserId() : business.getId();
            notificationCommandService.send(senderTenantId, notificationRequest);
        } catch (Exception e) {
            log.error("Failed to notify shop users about new discount {}: {}", discount.getName(), e.getMessage(), e);
        }
    }
}
