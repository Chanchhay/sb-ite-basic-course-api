package kh.edu.istad.ite.features.discount.service.Impl;

import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.LineDiscountApplication;
import kh.edu.istad.ite.features.discount.dto.OrderLineUnits;
import kh.edu.istad.ite.features.discount.service.DiscountApplicationService;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountApplicationServiceImpl implements DiscountApplicationService {

    private static final int PERCENT_SCALE = 4;

    private final DiscountService discountService;

    @Override
    public Optional<LineDiscountApplication> resolveLineDiscount(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal orderSubtotalForCondition
    ) {
        if (unitPrice == null || quantity <= 0) {
            return Optional.empty();
        }

        List<DiscountResponse> candidates = discountService
                .findApplicableDiscounts(businessId, channel, itemId, itemGroupId)
                .stream()
                .filter(d -> !Boolean.TRUE.equals(d.requiresCoupon()))
                .filter(d -> !isOrderScoped(d))
                .filter(d -> meetsCondition(d, quantity, orderSubtotalForCondition))
                .toList();

        return pickBest(candidates)
                .map(discount -> {
                    BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                    BigDecimal amount = computeAmount(discount, unitPrice, quantity, lineSubtotal);
                    return new LineDiscountApplication(discount.id(), labelFor(discount), amount);
                })
                .filter(application -> application.amount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Override
    public Optional<LineDiscountApplication> resolveOrderDiscount(
            UUID businessId,
            OrderChannel channel,
            List<OrderLineUnits> lines
    ) {
        BigDecimal orderSubtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = lines.stream().mapToInt(OrderLineUnits::quantity).sum();

        if (orderSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        List<DiscountResponse> candidates = discountService
                .findApplicableDiscounts(businessId, channel, null, null)
                .stream()
                .filter(d -> !Boolean.TRUE.equals(d.requiresCoupon()))
                .filter(this::isOrderScoped)
                .filter(d -> meetsCondition(d, totalQuantity, orderSubtotal))
                .toList();

        return pickBest(candidates)
                .map(discount -> {
                    BigDecimal amount = discount.ruleType() == DiscountRuleType.BUY_X_GET_Y
                            ? computeOrderBundleAmount(discount, lines)
                            : computeAmount(discount, orderSubtotal, 1, orderSubtotal);
                    return new LineDiscountApplication(discount.id(), labelFor(discount), amount);
                })
                .filter(application -> application.amount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Override
    public Optional<LineDiscountApplication> resolveDisplayUnitDiscount(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId,
            BigDecimal unitPrice
    ) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        // A discount aimed at this item (or its category) is the more
        // specific offer and is what the order will actually apply, so it
        // wins outright over any storewide one.
        Optional<LineDiscountApplication> targeted =
                resolveLineDiscount(businessId, channel, itemId, itemGroupId, unitPrice, 1, null);
        if (targeted.isPresent()) {
            return targeted;
        }

        List<DiscountResponse> candidates = discountService
                .findApplicableDiscounts(businessId, channel, itemId, itemGroupId)
                .stream()
                .filter(d -> !Boolean.TRUE.equals(d.requiresCoupon()))
                .filter(this::isOrderScoped)
                .filter(this::isUniformPerUnit)
                .toList();

        return pickBest(candidates)
                .map(discount -> new LineDiscountApplication(
                        discount.id(),
                        labelFor(discount),
                        computeAmount(discount, unitPrice, 1, unitPrice)))
                .filter(application -> application.amount().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Whether an order-wide discount comes to the same thing taken one unit
     * at a time as it does taken over the whole order — the only case a
     * listing may show it as a reduced per-item price.
     *
     * A percentage does, since a tenth off every line is a tenth off their
     * sum. A flat amount does not: "$5 off the order" is not $5 off each
     * item. Neither does a bundle, whose free unit depends on what else is
     * in the basket. Nor a capped percentage, which stops being proportional
     * as soon as the cap is reached. And a condition that can only be judged
     * against a real cart (a minimum spend, a minimum quantity) is not met
     * by one browsed unit, so it is no basis for a price either.
     */
    private boolean isUniformPerUnit(DiscountResponse discount) {
        return discount.type() == DiscountType.PERCENTAGE
                && discount.value() != null
                && discount.value().compareTo(BigDecimal.ZERO) > 0
                && discount.maxDiscountAmount() == null
                && (discount.ruleType() == null || discount.ruleType() == DiscountRuleType.NO_CONDITION);
    }

    @Override
    public Optional<String> previewDiscountLabel(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId
    ) {

        List<DiscountResponse> candidates = discountService
                .findApplicableDiscounts(businessId, channel, itemId, itemGroupId)
                .stream()
                .filter(d -> !Boolean.TRUE.equals(d.requiresCoupon()))
                .toList();

        return pickBest(candidates).map(this::labelFor);
    }


    private BigDecimal computeOrderBundleAmount(DiscountResponse discount, List<OrderLineUnits> lines) {
        int bundle = bundleSize(discount);
        int get = discount.getQuantity() != null && discount.getQuantity() > 0 ? discount.getQuantity() : 1;

        List<BigDecimal> units = new ArrayList<>();
        for (OrderLineUnits line : lines) {
            for (int i = 0; i < line.quantity(); i++) {
                units.add(line.unitPrice());
            }
        }
        units.sort(Comparator.naturalOrder());

        int freeCount = (units.size() / bundle) * get;
        BigDecimal amount = units.stream()
                .limit(freeCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (discount.maxDiscountAmount() != null && amount.compareTo(discount.maxDiscountAmount()) > 0) {
            amount = discount.maxDiscountAmount();
        }
        return amount;
    }

    private boolean isOrderScoped(DiscountResponse discount) {
        return discount.scope() == DiscountScope.ALL_ITEMS || discount.scope() == DiscountScope.ORDER;
    }

    /** Whether this discount's own stated condition is actually met right now — the check every caller used to skip. */
    private boolean meetsCondition(DiscountResponse discount, int quantity, BigDecimal orderSubtotalForCondition) {
        DiscountRuleType ruleType = discount.ruleType();
        if (ruleType == null) {
            return true;
        }

        return switch (ruleType) {
            case NO_CONDITION -> true;
            case MIN_QUANTITY -> discount.minQuantity() != null && quantity >= discount.minQuantity();
            case MIN_ORDER_AMOUNT -> orderSubtotalForCondition != null
                    && discount.minOrderAmount() != null
                    && orderSubtotalForCondition.compareTo(discount.minOrderAmount()) >= 0;
            case BUY_X_GET_Y -> quantity >= bundleSize(discount);
        };
    }

    private int bundleSize(DiscountResponse discount) {
        int buy = discount.buyQuantity() != null && discount.buyQuantity() > 0 ? discount.buyQuantity() : 1;
        int get = discount.getQuantity() != null && discount.getQuantity() > 0 ? discount.getQuantity() : 1;
        return buy + get;
    }

    /**
     * Same tie-break every caller used to duplicate: the more specifically
     * targeted discount wins, then a BUY_X_GET_Y bundle over a plain
     * percentage/fixed cut, then the larger stated value.
     */
    private Optional<DiscountResponse> pickBest(List<DiscountResponse> candidates) {
        return candidates.stream().max(Comparator
                .comparingInt(this::scopeSpecificity)
                .thenComparing(d -> d.ruleType() == DiscountRuleType.BUY_X_GET_Y ? 1 : 0)
                .thenComparing(d -> d.value() != null ? d.value() : BigDecimal.ZERO));
    }

    private int scopeSpecificity(DiscountResponse discount) {
        DiscountScope scope = discount.scope();
        if (scope == DiscountScope.SPECIFIC_ITEMS || scope == DiscountScope.ITEM) {
            return 2;
        }
        if (scope == DiscountScope.SPECIFIC_CATEGORIES || scope == DiscountScope.CATEGORY) {
            return 1;
        }
        return 0;
    }

    private BigDecimal computeAmount(
            DiscountResponse discount, BigDecimal unitPrice, int quantity, BigDecimal lineSubtotal) {
        BigDecimal amount;

        if (discount.ruleType() == DiscountRuleType.BUY_X_GET_Y) {
            int buy = discount.buyQuantity() != null && discount.buyQuantity() > 0 ? discount.buyQuantity() : 1;
            int get = discount.getQuantity() != null && discount.getQuantity() > 0 ? discount.getQuantity() : 1;
            int bundle = buy + get;
            int freeUnits = (quantity / bundle) * get;
            amount = unitPrice.multiply(BigDecimal.valueOf(freeUnits));
        } else if (discount.type() == DiscountType.PERCENTAGE && discount.value() != null) {
            BigDecimal unitDiscount = unitPrice
                    .multiply(discount.value())
                    .divide(BigDecimal.valueOf(100), PERCENT_SCALE, RoundingMode.HALF_UP);
            amount = unitDiscount.multiply(BigDecimal.valueOf(quantity));
        } else if (discount.type() == DiscountType.FIXED_AMOUNT && discount.value() != null) {
            amount = discount.value().multiply(BigDecimal.valueOf(quantity));
        } else {
            amount = BigDecimal.ZERO;
        }

        if (discount.maxDiscountAmount() != null && amount.compareTo(discount.maxDiscountAmount()) > 0) {
            amount = discount.maxDiscountAmount();
        }
        if (amount.compareTo(lineSubtotal) > 0) {
            amount = lineSubtotal;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }

        return amount;
    }

    private String labelFor(DiscountResponse discount) {
        if (discount.name() != null && !discount.name().isBlank()) {
            return discount.name();
        }
        if (discount.ruleType() == DiscountRuleType.BUY_X_GET_Y) {
            int buy = discount.buyQuantity() != null ? discount.buyQuantity() : 1;
            int get = discount.getQuantity() != null ? discount.getQuantity() : 1;
            return "Buy " + buy + " Get " + get;
        }
        if (discount.type() == DiscountType.PERCENTAGE && discount.value() != null) {
            return discount.value().stripTrailingZeros().toPlainString() + "% OFF";
        }
        if (discount.type() == DiscountType.FIXED_AMOUNT && discount.value() != null) {
            return "$" + discount.value().stripTrailingZeros().toPlainString() + " OFF";
        }
        return null;
    }
}
