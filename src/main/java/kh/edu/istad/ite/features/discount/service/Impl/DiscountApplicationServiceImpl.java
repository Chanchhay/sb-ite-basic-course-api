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

    /**
     * "Buy 2 get 1 free, any item" spread across a real order: every unit in
     * the order is one entry, cheapest first, and every complete bundle's
     * free slots come off the cheapest units still left — the same rule a
     * single repeated item already gets for free from the bundle math, made
     * to work when the order mixes different items at different prices too.
     */
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
