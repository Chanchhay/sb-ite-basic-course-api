package kh.edu.istad.ite.features.discount;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.CreateCouponRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateCouponRequest;
import kh.edu.istad.ite.features.discount.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CouponResponse createCoupon(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateCouponRequest request
    ) {
        return couponService.createCoupon(businessId, request);
    }

    @GetMapping
    public List<CouponResponse> findAllCoupons(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID discountId
    ) {
        return couponService.findAllCoupons(businessId, discountId);
    }

    @GetMapping("/{couponId}")
    public CouponResponse findCouponById(
            @PathVariable UUID businessId,
            @PathVariable UUID couponId
    ) {
        return couponService.findCouponById(businessId, couponId);
    }

    @GetMapping("/code/{code}")
    public CouponResponse findCouponByCode(
            @PathVariable UUID businessId,
            @PathVariable String code
    ) {
        return couponService.findCouponByCode(businessId, code);
    }

    @PutMapping("/{couponId}")
    public CouponResponse updateCoupon(
            @PathVariable UUID businessId,
            @PathVariable UUID couponId,
            @Valid @RequestBody UpdateCouponRequest request
    ) {
        return couponService.updateCoupon(businessId, couponId, request);
    }

    @PatchMapping("/{couponId}/activate")
    public CouponResponse activateCoupon(
            @PathVariable UUID businessId,
            @PathVariable UUID couponId
    ) {
        return couponService.activateCoupon(businessId, couponId);
    }

    @PatchMapping("/{couponId}/deactivate")
    public CouponResponse deactivateCoupon(
            @PathVariable UUID businessId,
            @PathVariable UUID couponId
    ) {
        return couponService.deactivateCoupon(businessId, couponId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{couponId}")
    public void deleteCoupon(
            @PathVariable UUID businessId,
            @PathVariable UUID couponId
    ) {
        couponService.deleteCoupon(businessId, couponId);
    }
}
