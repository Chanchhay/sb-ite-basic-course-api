package kh.edu.istad.ite.features.discount;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    // Used by checkout (POS, website, Telegram/Messenger bot, ...) to ask
    // "what discounts are actually usable right now for this channel /
    // item / category". e.g. GET .../discounts/applicable?channel=POS
    // GET .../discounts/applicable?channel=WEB&itemGroupId=<food-category-id>
    @GetMapping("/applicable")
    public List<DiscountResponse> findApplicableDiscounts(
            @PathVariable UUID businessId,
            @RequestParam OrderChannel channel,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID itemGroupId
    ){
        return discountService.findApplicableDiscounts(businessId, channel, itemId, itemGroupId);
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
