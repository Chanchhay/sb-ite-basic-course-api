package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountStatusRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.mapper.DiscountMapper;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.discount.specification.DiscountSpecifications;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    private final BusinessHelper businessHelper;
    private final DiscountRepository discountRepository;
    private final DiscountMapper discountMapper;

    @Override
    @Transactional
    public DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        String name = TextHelper.trimRequired(request.name(), "Discount name cannot be empty");
        if (discountRepository.existsByBusinessIdAndNameIgnoreCaseAndDeletedAtIsNull(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount with this name already exists");
        }

        Discount discount = new Discount();
        discount.setBusiness(business);
        discount.setName(name);
        discount.setDescription(TextHelper.trimToNull(request.description()));
        discount.setType(request.type());
        discount.setRuleType(request.ruleType());
        discount.setBuyQuantity(request.buyQuantity());
        discount.setGetQuantity(request.getQuantity());
        discount.setMinQuantity(request.minQuantity());
        discount.setValue(request.value());
        discount.setScope(request.scope());
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setRequiresCoupon(request.requiresCoupon() != null && request.requiresCoupon());
        discount.setStartsAt(request.startsAt());
        discount.setEndsAt(request.endsAt());
        discount.setBranchId(request.branchId());
        discount.setStatus(RecordStatus.ACTIVE);

        validateBusinessRules(discount);

        try {
            return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponse> searchDiscounts(
            UUID businessId,
            String keyword,
            DiscountType type,
            DiscountRuleType ruleType,
            DiscountScope scope,
            RecordStatus status,
            LocalDateTime activeAt,
            Pageable pageable
    ) {
        businessHelper.findOwnedBusiness(businessId);

        var spec = DiscountSpecifications.withFilters(
                businessId, keyword, type, ruleType, scope, status, activeAt
        );

        return discountRepository.findAll(spec, pageable).map(discountMapper::toResponse);
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
                if (discountRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
                        businessId, name, discountId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount with this name already exists");
                }
                discount.setName(name);
            }
        }
        if (request.description() != null) {
            discount.setDescription(TextHelper.trimToNull(request.description()));
        }
        if (request.type() != null) {
            discount.setType(request.type());
        }
        if (request.ruleType() != null) {
            discount.setRuleType(request.ruleType());
        }
        if (request.buyQuantity() != null) {
            discount.setBuyQuantity(request.buyQuantity());
        }
        if (request.getQuantity() != null) {
            discount.setGetQuantity(request.getQuantity());
        }
        if (request.minQuantity() != null) {
            discount.setMinQuantity(request.minQuantity());
        }
        if (request.value() != null) {
            discount.setValue(request.value());
        }
        if (request.scope() != null) {
            discount.setScope(request.scope());
        }
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
        if (request.branchId() != null) {
            discount.setBranchId(request.branchId());
        }

        validateBusinessRules(discount);

        try {
            return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount already exists", e);
        }
    }

    @Override
    @Transactional
    public DiscountResponse updateDiscountStatus(
            UUID businessId,
            UUID discountId,
            UpdateDiscountStatusRequest request
    ) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        discount.setStatus(request.status());

        return discountMapper.toResponse(discountRepository.saveAndFlush(discount));
    }

    @Override
    @Transactional
    public void deleteDiscount(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(discountId, businessId);

        // Soft delete: keep the row for order/coupon history, hide it from
        // normal lookups via deletedAt (mirrors the deleted_at column).
        discount.setDeletedAt(LocalDateTime.now());
        discount.setStatus(RecordStatus.INACTIVE);
        discountRepository.saveAndFlush(discount);
    }

    private Discount findDiscount(UUID discountId, UUID businessId) {
        return discountRepository.findByIdAndBusinessIdAndDeletedAtIsNull(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
    }

    /**
     * Cross-field rules tying type/ruleType to which quantity/amount fields
     * are required, since the table itself allows them all to be nullable.
     */
    private void validateBusinessRules(Discount discount) {
        switch (discount.getType()) {
            case PERCENTAGE -> {
                requireValue(discount.getValue(), "value is required for a percentage discount");
                if (discount.getValue().compareTo(BigDecimal.ZERO) <= 0
                        || discount.getValue().compareTo(MAX_PERCENTAGE) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "value must be between 0 and 100 for a percentage discount"
                    );
                }
            }
            case FIXED_AMOUNT -> {
                requireValue(discount.getValue(), "value is required for a fixed amount discount");
                if (discount.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "value must be greater than 0 for a fixed amount discount"
                    );
                }
            }
            case BUY_X_GET_Y -> {
                if (discount.getBuyQuantity() == null || discount.getGetQuantity() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "buyQuantity and getQuantity are required for a buy-X-get-Y discount"
                    );
                }
            }
        }

        switch (discount.getRuleType()) {
            case MIN_QUANTITY -> {
                if (discount.getMinQuantity() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "minQuantity is required when ruleType is MIN_QUANTITY"
                    );
                }
            }
            case MIN_ORDER_AMOUNT -> {
                if (discount.getMinOrderAmount() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "minOrderAmount is required when ruleType is MIN_ORDER_AMOUNT"
                    );
                }
            }
            case BUY_X_GET_Y -> {
                if (discount.getBuyQuantity() == null || discount.getGetQuantity() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "buyQuantity and getQuantity are required when ruleType is BUY_X_GET_Y"
                    );
                }
            }
            case NO_CONDITION -> {
                // nothing extra required
            }
        }

        if (discount.getStartsAt() != null && discount.getEndsAt() != null
                && !discount.getEndsAt().isAfter(discount.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt must be after startsAt");
        }
    }

    private void requireValue(BigDecimal value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
