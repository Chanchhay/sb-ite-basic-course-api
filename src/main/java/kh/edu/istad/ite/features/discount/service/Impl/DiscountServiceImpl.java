package kh.edu.istad.ite.features.discount.service.Impl;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.repository.MembershipTypeRepository;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.mapper.DiscountMapper;
import kh.edu.istad.ite.features.discount.repository.CouponRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final BusinessHelper businessHelper;
    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final MembershipTypeRepository membershipTypeRepository;
    private final DiscountMapper discountMapper;

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
        DiscountScope scope = request.scope() != null ? DiscountScope.valueOf(request.scope()) : discount.getScope();

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

    private Discount findDiscount(UUID discountId, UUID businessId) {
        return discountRepository.findByIdAndBusinessId(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
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

    private void validateValue(DiscountType type, BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must be greater than zero");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage value cannot exceed 100");
        }
    }
}
