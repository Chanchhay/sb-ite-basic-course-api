package kh.edu.istad.ite.features.discount.service.Impl;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.CreateCouponRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateCouponRequest;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.mapper.CouponMapper;
import kh.edu.istad.ite.features.discount.repository.CouponRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.discount.service.CouponService;
import kh.edu.istad.ite.shared.enums.CouponStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final BusinessHelper businessHelper;
    private final CouponRepository couponRepository;
    private final DiscountRepository discountRepository;
    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public CouponResponse createCoupon(UUID businessId, CreateCouponRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        Discount discount = findDiscount(request.discountId(), businessId);

        if (!Boolean.TRUE.equals(discount.getRequiresCoupon())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected discount rule does not require a coupon code. Please edit the discount rule and enable 'Requires Coupon Code' first."
            );
        }

        String code = TextHelper.trimRequired(request.code(), "Coupon code cannot be empty").toUpperCase();
        if (couponRepository.existsByBusinessIdAndCodeIgnoreCase(businessId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A coupon with code '" + code + "' already exists in your store.");
        }
        validateDateRange(request.startsAt(), request.endsAt());

        if (discount.getStartsAt() != null && request.startsAt().isBefore(discount.getStartsAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Coupon start date cannot be earlier than the linked discount start date (" + discount.getStartsAt() + ")"
            );
        }
        if (discount.getEndsAt() != null && request.endsAt().isAfter(discount.getEndsAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Coupon end date cannot be later than the linked discount end date (" + discount.getEndsAt() + ")"
            );
        }

        Coupon coupon = new Coupon();
        coupon.setBusiness(business);
        coupon.setDiscount(discount);
        coupon.setCode(code);
        coupon.setUsageLimit(request.usageLimit());
        coupon.setUsageLimitPerCustomer(request.usageLimitPerCustomer());
        coupon.setUsedCount(0);
        coupon.setMinPurchaseAmount(request.minPurchaseAmount());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setStatus(request.status() != null ? request.status() : CouponStatus.ACTIVE);

        try {
            return couponMapper.toResponse(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException e) {
            String rootCause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            log.error("Failed to save coupon for business {}: {}", businessId, rootCause, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database constraint error: " + rootCause, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> findAllCoupons(UUID businessId, UUID discountId) {
        businessHelper.findOwnedBusiness(businessId);

        List<Coupon> coupons = discountId != null
                ? couponRepository.findAllByBusinessIdAndDiscount_IdOrderByCreatedDateDesc(businessId, discountId)
                : couponRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId);

        return coupons.stream()
                .map(couponMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse findCouponById(UUID businessId, UUID couponId) {
        businessHelper.findOwnedBusiness(businessId);
        return couponMapper.toResponse(findCoupon(couponId, businessId));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse findCouponByCode(UUID businessId, String code) {
        businessHelper.findOwnedBusiness(businessId);

        return couponRepository.findByBusinessIdAndCodeIgnoreCase(businessId, TextHelper.trimRequired(code, "code cannot be empty"))
                .map(couponMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon has not been found"));
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(UUID businessId, UUID couponId, UpdateCouponRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Coupon coupon = findCoupon(couponId, businessId);

        if (request.code() != null) {
            String code = TextHelper.trimRequired(request.code(), "Coupon code cannot be empty").toUpperCase();
            if (!code.equals(coupon.getCode())) {
                if (couponRepository.existsByBusinessIdAndCodeIgnoreCaseAndIdNot(businessId, code, couponId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon with this code already exists");
                }
                coupon.setCode(code);
            }
        }
        if (request.usageLimit() != null) {
            coupon.setUsageLimit(request.usageLimit());
        }
        if (request.usageLimitPerCustomer() != null) {
            coupon.setUsageLimitPerCustomer(request.usageLimitPerCustomer());
        }
        if (request.minPurchaseAmount() != null) {
            coupon.setMinPurchaseAmount(request.minPurchaseAmount());
        }

        LocalDateTimeRange range = resolveDateRange(coupon, request);
        validateDateRange(range.startsAt(), range.endsAt());
        coupon.setStartsAt(range.startsAt());
        coupon.setEndsAt(range.endsAt());

        if (request.status() != null) {
            coupon.setStatus(CouponStatus.valueOf(request.status()));
        }

        try {
            return couponMapper.toResponse(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon already exists", e);
        }
    }

    @Override
    @Transactional
    public CouponResponse activateCoupon(UUID businessId, UUID couponId) {
        businessHelper.findOwnedBusiness(businessId);
        Coupon coupon = findCoupon(couponId, businessId);

        coupon.setStatus(CouponStatus.ACTIVE);
        return couponMapper.toResponse(couponRepository.saveAndFlush(coupon));
    }

    @Override
    @Transactional
    public CouponResponse deactivateCoupon(UUID businessId, UUID couponId) {
        businessHelper.findOwnedBusiness(businessId);
        Coupon coupon = findCoupon(couponId, businessId);

        coupon.setStatus(CouponStatus.INACTIVE);
        return couponMapper.toResponse(couponRepository.saveAndFlush(coupon));
    }

    @Override
    @Transactional
    public void deleteCoupon(UUID businessId, UUID couponId) {
        businessHelper.findOwnedBusiness(businessId);
        Coupon coupon = findCoupon(couponId, businessId);

        couponRepository.delete(coupon);
        couponRepository.flush();
    }

    private Discount findDiscount(UUID discountId, UUID businessId) {
        return discountRepository.findByIdAndBusinessId(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
    }

    private Coupon findCoupon(UUID couponId, UUID businessId) {
        return couponRepository.findByIdAndBusinessId(couponId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon has not been found"));
    }

    private void validateDateRange(java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt must not be before startsAt");
        }
    }

    private LocalDateTimeRange resolveDateRange(Coupon coupon, UpdateCouponRequest request) {
        java.time.LocalDateTime startsAt = request.startsAt() != null ? request.startsAt() : coupon.getStartsAt();
        java.time.LocalDateTime endsAt = request.endsAt() != null ? request.endsAt() : coupon.getEndsAt();
        return new LocalDateTimeRange(startsAt, endsAt);
    }

    private record LocalDateTimeRange(java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt) {
    }
}
