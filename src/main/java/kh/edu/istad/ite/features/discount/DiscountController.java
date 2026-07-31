package kh.edu.istad.ite.features.discount;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.service.DiscountService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    public List<DiscountResponse> findAllDiscounts(@PathVariable UUID businessId) {
        return discountService.findAllDiscounts(businessId);
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

    @PatchMapping("/{discountId}/activate")
    public DiscountResponse activateDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId
    ) {
        return discountService.activateDiscount(businessId, discountId);
    }

    @PatchMapping("/{discountId}/deactivate")
    public DiscountResponse deactivateDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID discountId
    ) {
        return discountService.deactivateDiscount(businessId, discountId);
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
