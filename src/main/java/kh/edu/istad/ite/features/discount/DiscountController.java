package kh.edu.istad.ite.features.discount;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountStatusRequest;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public DiscountResponse createDiscount(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateDiscountRequest request
    ) {
        return discountService.createDiscount(businessId, request);
    }

    @GetMapping
    public Page<DiscountResponse> searchDiscounts(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DiscountType type,
            @RequestParam(required = false) DiscountRuleType ruleType,
            @RequestParam(required = false) DiscountScope scope,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime activeAt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return discountService.searchDiscounts(
                businessId, keyword, type, ruleType, scope, status, activeAt, pageable
        );
    }

    @GetMapping("/{discountId}")
    public DiscountResponse findDiscountById(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId
    ) {
        return discountService.findDiscountById(businessId, discountId);
    }

    @PutMapping("/{discountId}")
    public DiscountResponse updateDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId,
            @Valid @RequestBody UpdateDiscountRequest request
    ) {
        return discountService.updateDiscount(businessId, discountId, request);
    }

    @PutMapping("/{discountId}/status")
    public DiscountResponse updateDiscountStatus(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId,
            @Valid @RequestBody UpdateDiscountStatusRequest request
    ) {
        return discountService.updateDiscountStatus(businessId, discountId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{discountId}")
    public void deleteDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId
    ) {
        discountService.deleteDiscount(businessId, discountId);
    }
}
