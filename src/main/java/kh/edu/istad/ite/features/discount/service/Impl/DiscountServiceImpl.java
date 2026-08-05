package kh.edu.istad.ite.features.discount.service.Impl;

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
import kh.edu.istad.ite.shared.enums.*;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

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

    @Override
    @Transactional
    public DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        String name = TextHelper.trimRequired(request.name(), "Discount name cannot be empty");
        if (discountRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount with this name already exists");
        }

        DiscountType type = request.type();
        DiscountRuleType ruleType = request.ruleType();
        DiscountScope scope = request.scope();
        validateRule(ruleType, request.buyQuantity(), request.getQuantity(), request.minQuantity());
        validateValue(type, request.value());

        Discount discount = new Discount();
        discount.setBusiness(business);
        discount.setName(name);
        discount.setDescription(TextHelper.trimToNull(request.description()));
        discount.setType(type);
        discount.setRuleType(ruleType);
        discount.setBuyQuantity(request.buyQuantity());
        discount.setGetQuantity(request.getQuantity());
        discount.setMinQuantity(request.minQuantity());
        discount.setValue(request.value());
        discount.setScope(scope);
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setRequiresCoupon(request.requiresCoupon() != null && request.requiresCoupon());
        discount.setStartsAt(request.startsAt());
        discount.setEndsAt(request.endsAt());
        discount.setSelectedDays(request.selectedDays());
        discount.setStatus(RecordStatus.ACTIVE);

        try {
            return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscountResponse> findAllDiscounts(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);

        return discountRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId)
                .stream()
                .map(discountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse findDiscountById(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        return discountMapper.toResponse(findDiscount(discountId, businessId));
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
        DiscountRuleType ruleType = request.ruleType() != null ? DiscountRuleType.valueOf(request.ruleType()) : discount.getRuleType();
        DiscountScope scope = request.scope() != null ? request.scope() : discount.getScope();

        Integer buyQuantity = request.buyQuantity() != null ? request.buyQuantity() : discount.getBuyQuantity();
        Integer getQuantity = request.getQuantity() != null ? request.getQuantity() : discount.getGetQuantity();
        Integer minQuantity = request.minQuantity() != null ? request.minQuantity() : discount.getMinQuantity();
        validateRule(ruleType, buyQuantity, getQuantity, minQuantity);

        BigDecimal value = request.value() != null ? request.value() : discount.getValue();
        validateValue(type, value);

        discount.setType(type);
        discount.setRuleType(ruleType);
        discount.setScope(scope);
        discount.setBuyQuantity(buyQuantity);
        discount.setGetQuantity(getQuantity);
        discount.setMinQuantity(minQuantity);
        discount.setValue(value);

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
        if (request.status() != null) {
            discount.setStatus(RecordStatus.valueOf(request.status()));
        }

        try {
            return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount already exists", e);
        }
    }

    @Override
    @Transactional
    public DiscountResponse activateDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        discount.setStatus(RecordStatus.ACTIVE);
        return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
    }

    @Override
    @Transactional
    public DiscountResponse deactivateDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        discount.setStatus(RecordStatus.INACTIVE);
        return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
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
        businessHelper.findOwnedBusiness(businessId);

        List<Discount> candidates = discountRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId);
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = LocalDate.now().getDayOfWeek();

        List<DiscountResponse> result = new ArrayList<>();

        for (Discount discount : candidates) {
            if (discount.getStatus() == RecordStatus.ACTIVE) {
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

    // scope = ITEM must target specific product(s); scope = CATEGORY must
    // target specific item group(s); scope = ORDER shouldn't target either.
    private void validateTargetsMatchScope(
            DiscountScope scope,
            List<UUID> targetItemIds,
            List<UUID> targetItemGroupIds
    ) {
        boolean hasItemTargets = targetItemIds != null && !targetItemIds.isEmpty();
        boolean hasGroupTargets = targetItemGroupIds != null && !targetItemGroupIds.isEmpty();

        if (scope == DiscountScope.ITEM && !hasItemTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemIds is required when scope is ITEM"
            );
        }
        if (scope == DiscountScope.CATEGORY && !hasGroupTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemGroupIds is required when scope is CATEGORY"
            );
        }
        if (scope == DiscountScope.ORDER && (hasItemTargets || hasGroupTargets)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order-level discounts cannot have item/category targets"
            );
        }
        if (scope == DiscountScope.ITEM && hasGroupTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemGroupIds is not allowed when scope is ITEM"
            );
        }
        if (scope == DiscountScope.CATEGORY && hasItemTargets) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetItemIds is not allowed when scope is CATEGORY"
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
        if (discount.getScope() == DiscountScope.ORDER) {
            return true;
        }
        if (discount.getScope() == DiscountScope.ITEM) {
            if (itemId == null) {
                return false;
            }
            return targets.stream()
                    .anyMatch(target -> target.getItem() != null && target.getItem().getId().equals(itemId));
        }
        if (discount.getScope() == DiscountScope.CATEGORY) {
            if (itemGroupId == null) {
                return false;
            }
            return targets.stream()
                    .anyMatch(target -> target.getItemGroup() != null && target.getItemGroup().getId().equals(itemGroupId));
        }

        return false;
    }

    private void validateValue(DiscountType type, BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must be greater than zero");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage value cannot exceed 100");
        }
    }
}
