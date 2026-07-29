package kh.edu.istad.ite.features.discount;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.discount.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountResponse createDiscount(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateDiscountRequest request) {
        return discountService.createDiscount(businessId, request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public DiscountResponse getDiscountById(
            @PathVariable UUID businessId,
            @PathVariable UUID id) {
        return discountService.getDiscountById(businessId, id);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<DiscountResponse> getAllDiscounts(
            @PathVariable UUID businessId,
            Pageable pageable) {
        return discountService.getAllDiscounts(businessId, pageable);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public DiscountResponse updateDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateDiscountRequest request) {
        return discountService.updateDiscount(businessId, id, request);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public DiscountResponse patchDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID id,
            @Valid @RequestBody PatchDiscountRequest request) {
        return discountService.patchDiscount(businessId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID id) {
        discountService.deleteDiscount(businessId, id);
    }
}
