package kh.edu.istad.ite.features.cart.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.cart.dto.AddToCartRequest;
import kh.edu.istad.ite.features.cart.dto.CartCountResponse;
import kh.edu.istad.ite.features.cart.dto.CartSelectionRequest;
import kh.edu.istad.ite.features.cart.dto.CartSummaryResponse;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.entity.CartItemAddOn;
import kh.edu.istad.ite.features.cart.entity.CartItemSelection;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemAddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;
import kh.edu.istad.ite.features.catalog.entity.ItemAttributeValue;
import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.shared.enums.AttributePlacement;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.channel.service.ChannelPriceResolver;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class StorefrontCartService {

    /** The seeded channel the online store trades as. */
    private static final String WEB_CHANNEL_CODE = OrderChannel.WEB.name();

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BusinessRepository businessRepository;
    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityService customerIdentityService;
    private final BusinessHelper businessHelper;
    private final MinioService minioService;
    private final ChannelPriceResolver channelPriceResolver;
    private final ItemChannelStockService itemChannelStockService;
    private final StockEntryService stockEntryService;
    private final kh.edu.istad.ite.features.discount.service.DiscountService discountService;


    @Transactional(readOnly = true)
    public CartSummaryResponse findMyCart() {
        GlobalCustomer shopper = currentShopper();

        List<Cart> carts = cartRepository.findAllByCustomerIdInAndStatus(
                customerIdsOf(shopper), CartStatus.ACTIVE);

        List<CartSummaryResponse.StoreCart> stores = carts.stream()
                .filter(cart -> !cart.getItems().isEmpty())
                .map(this::toStoreCart)
                .sorted(Comparator.comparing(CartSummaryResponse.StoreCart::name))
                .toList();

        return new CartSummaryResponse(
                stores.size(),
                stores.stream().mapToInt(CartSummaryResponse.StoreCart::itemCount).sum(),
                stores);
    }

    @Transactional(readOnly = true)
    public CartCountResponse countMyCart() {
        GlobalCustomer shopper = currentShopper();

        List<Cart> carts = cartRepository.findAllByCustomerIdInAndStatus(
                customerIdsOf(shopper), CartStatus.ACTIVE);

        int totalItems = 0;
        int storeCount = 0;

        for (Cart cart : carts) {
            int count = cart.getTotalItemsCount();
            if (count > 0) {
                totalItems += count;
                storeCount++;
            }
        }

        return new CartCountResponse(totalItems, storeCount);
    }


    @Transactional
    public CartSummaryResponse addItem(AddToCartRequest request) {
        GlobalCustomer shopper = currentShopper();

        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop has not been found"));

        requireShoppable(business);

        Item item = itemRepository.findByIdAndBusinessId(request.itemId(), business.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item has not been found in this shop"));

        if (!ItemStatus.ACTIVE.equals(item.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item is not on sale right now");
        }

        ItemVariant variant = resolveVariant(item, request.variantId());
        PricedLine priced = resolveLine(business, item, variant, request.unitId());
        List<CartItemSelection> selections = resolveSelections(item, request.selections());
        List<CartItemAddOn> addOns = resolveAddOns(item, request.addOnIds());

        Customer customer = customerIdentityService.customerFor(business, shopper);
        Cart cart = activeCartFor(customer, business);

        String selectionKey = selections.stream()
                .map(selection -> selection.getAttributeName() + "=" + selection.getValue())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));

        String addOnKey = addOns.stream()
                .map(addOn -> addOn.getAddOn().getId().toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));

        CartItem line = findLine(
                cart, item.getId(), request.variantId(), unitIdOf(priced.unit()),
                selectionKey, addOnKey)
                .orElse(null);

        // What the line would hold once this is added, so that adding one at a
        // time cannot creep past what the web may sell.
        int alreadyHeld = line == null || line.getQuantity() == null ? 0 : line.getQuantity();
        requireStock(business, item, variant,
                priced.unitFactor().multiply(BigDecimal.valueOf(alreadyHeld + request.quantity())));

        if (line == null) {
            line = CartItem.builder()
                    .cart(cart)
                    .item(item)
                    .variant(variant)
                    .unit(priced.unit())
                    .unitFactor(priced.unitFactor())
                    .quantity(request.quantity())
                    .priceSnapshot(priced.unitPrice())
                    .basePrice(priced.channelPrice())
                    .build();

            selections.forEach(line::addSelection);
            addOns.forEach(line::addAddOn);
            cart.getItems().add(line);
        } else {
            line.setQuantity(line.getQuantity() + request.quantity());
            line.setPriceSnapshot(priced.unitPrice());
            line.setBasePrice(priced.channelPrice());
        }

        cartRepository.save(cart);

        return findMyCart();
    }

    @Transactional
    public CartSummaryResponse updateItem(UUID cartItemId, int quantity) {
        CartItem line = requireOwnedLine(cartItemId);

        if (quantity <= 0) {
            Cart cart = line.getCart();
            cart.getItems().removeIf(candidate -> candidate.getId().equals(cartItemId));
            cartRepository.save(cart);
        } else {
            // Raising the quantity here is the same promise as adding it in
            // the first place, so it answers to the same ceiling — in base
            // units, since the line may be a pack.
            BigDecimal factor = line.getUnitFactor() == null
                    ? BigDecimal.ONE
                    : line.getUnitFactor();

            requireStock(
                    line.getCart().getBusiness(),
                    line.getItem(),
                    line.getVariant(),
                    factor.multiply(BigDecimal.valueOf(quantity)));
            line.setQuantity(quantity);
            cartItemRepository.save(line);
        }

        return findMyCart();
    }

    @Transactional
    public CartSummaryResponse removeItem(UUID cartItemId) {
        return updateItem(cartItemId, 0);
    }

    @Transactional
    public CartSummaryResponse removeStore(UUID businessId) {
        GlobalCustomer shopper = currentShopper();

        Customer customer = customerRepository
                .findByBusiness_IdAndGlobalCustomer_Id(businessId, shopper.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop"));

        cartRepository
                .findByCustomerIdAndBusinessIdAndStatus(customer.getId(), businessId, CartStatus.ACTIVE)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                });

        return findMyCart();
    }


    private CartItem requireOwnedLine(UUID cartItemId) {
        GlobalCustomer shopper = currentShopper();

        CartItem line = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart line has not been found"));

        UUID owner = line.getCart().getCustomer().getGlobalCustomer() == null
                ? null
                : line.getCart().getCustomer().getGlobalCustomer().getId();

        if (!shopper.getId().equals(owner)) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart line has not been found");
        }

        return line;
    }

    private Cart activeCartFor(Customer customer, Business business) {
        return cartRepository
                .findByCustomerIdAndBusinessIdAndStatus(customer.getId(), business.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .customer(customer)
                        .business(business)
                        .status(CartStatus.ACTIVE)
                        .build()));
    }

    private List<UUID> customerIdsOf(GlobalCustomer shopper) {
        List<UUID> ids = customerRepository.findAllByGlobalCustomer_Id(shopper.getId()).stream()
                .map(Customer::getId)
                .toList();

        return ids.isEmpty() ? List.of(new UUID(0L, 0L)) : ids;
    }


    private GlobalCustomer currentShopper() {
        return customerIdentityService.resolve(
                AuthHelper.currentUserId(),
                claim("email"),
                claim("phone_number"),
                claim("name"));
    }

    private String claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwt) {
            Object value = jwt.getToken().getClaims().get(name);
            return value == null ? null : String.valueOf(value);
        }

        return null;
    }

    private void requireShoppable(Business business) {
        if (!Boolean.TRUE.equals(business.getIsEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, business.getDisplayName() + " is closed right now");
        }

        businessHelper.requireFeature(business.getId(), BusinessFeature.STOREFRONT);
        // Hours the shop set for its online store, enforced where the till's
        // are: unenforced opening hours are a note to self.
        channelPriceResolver.requireOpen(business.getId(), WEB_CHANNEL_CODE);
    }

    private static UUID unitIdOf(Unit unit) {
        return unit == null ? null : unit.getId();
    }

    private Optional<CartItem> findLine(
            Cart cart, UUID itemId, UUID variantId, UUID unitId,
            String selectionKey, String addOnKey) {
        return cart.getItems().stream()
                .filter(line -> line.getItem().getId().equals(itemId))
                .filter(line -> {
                    UUID lineVariant = line.getVariant() == null ? null : line.getVariant().getId();
                    return variantId == null ? lineVariant == null : variantId.equals(lineVariant);
                })
                // A six-pack is not six singles: different price, different
                // line. Merging them would lose which one was bought.
                .filter(line -> Objects.equals(unitIdOf(line.getUnit()), unitId))
                // Two of the same drink at different sweetness are two orders,
                // not one of quantity two. Merging them would hand the counter
                // a ticket that cannot be made.
                .filter(line -> line.selectionKey().equals(selectionKey))
                // And one with pearls is not one without: different money,
                // different thing to make.
                .filter(line -> line.addOnKey().equals(addOnKey))
                .findFirst();
    }

    /**
     * Turns what the shopper picked into what the line will carry.
     *
     * Checked against the item rather than trusted, for the ordinary reason:
     * this arrives from a browser. An attribute the item does not have, or a
     * value it does not offer, is a line the shop cannot make — and a value the
     * seller switched off is one they said they would not make today.
     *
     * Only {@code OPTION} attributes are choices. The rest are copy: a
     * HIGHLIGHT is a delivery promise, a SPECIFICATION is a fact about the
     * item, and a HIDDEN one was never meant to leave the back office.
     */
    /** Absent placement means OPTION — that is what an attribute was before the field existed. */
    private boolean isOption(ItemAttribute attribute) {
        return attribute.getPlacement() == null
                || AttributePlacement.OPTION.equals(attribute.getPlacement());
    }

    private List<CartItemSelection> resolveSelections(
            Item item, List<CartSelectionRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }

        List<ItemAttribute> attributes = item.getAttributes() == null
                ? List.of()
                : item.getAttributes();

        List<CartItemSelection> resolved = new ArrayList<>(requested.size());
        Set<String> seen = new HashSet<>();

        for (CartSelectionRequest choice : requested) {
            ItemAttribute attribute = attributes.stream()
                    .filter(candidate -> isOption(candidate))
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(choice.attributeName()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "\"" + item.getName() + "\" has no option called \""
                                    + choice.attributeName() + "\""));

            if (!seen.add(attribute.getName().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + attribute.getName() + "\" was chosen twice");
            }

            ItemAttributeValue value = (attribute.getValues() == null ? List.<ItemAttributeValue>of()
                    : attribute.getValues())
                    .stream()
                    .filter(candidate -> candidate.getValue().equals(choice.value()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "\"" + choice.value() + "\" is not a " + attribute.getName()
                                    + " that \"" + item.getName() + "\" comes in"));

            if (Boolean.FALSE.equals(value.getAvailable())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        attribute.getName() + " \"" + choice.value() + "\" is not available today");
            }

            resolved.add(CartItemSelection.builder()
                    .attributeName(attribute.getName())
                    .value(value.getValue())
                    .label(value.getLabel())
                    .build());
        }

        // Every option the item offers has to be answered, or the line reaches
        // the counter with a question on it.
        attributes.stream()
                .filter(this::isOption)
                .filter(attribute -> attribute.getValues() != null && !attribute.getValues().isEmpty())
                .filter(attribute -> !seen.contains(attribute.getName().toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(missing -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Choose a " + missing.getName() + " for \"" + item.getName() + "\"");
                });

        return resolved;
    }

    /**
     * Turns the extras ticked into what the line will carry.
     *
     * Checked against the item rather than trusted, for the same reason the
     * options are: this arrives from a browser. An add-on the item does not
     * offer, one the shop has taken off that item for now, or one nobody has
     * priced yet is an extra the counter cannot make good on — and an unpriced
     * one would otherwise ride along free.
     *
     * The name, price and usage are copied onto the line as they stand now, so
     * a basket left open overnight still costs what it said it cost. This is
     * the till's {@code attachAddOns} rule, kept in step deliberately: the same
     * extra must not be sellable on one channel and not the other.
     */
    private List<CartItemAddOn> resolveAddOns(Item item, List<UUID> addOnIds) {
        if (addOnIds == null || addOnIds.isEmpty()) {
            return List.of();
        }

        List<ItemAddOn> offered = item.getAddOns() == null ? List.of() : item.getAddOns();
        List<CartItemAddOn> resolved = new ArrayList<>();

        for (UUID addOnId : addOnIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ItemAddOn link = offered.stream()
                    .filter(candidate -> candidate.getAddOn() != null)
                    .filter(candidate -> candidate.getAddOn().getId().equals(addOnId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "\"" + item.getName() + "\" does not come with that extra"));

            AddOn addOn = link.getAddOn();

            if (!link.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + addOn.getName() + "\" is not on sale with \"" + item.getName() + "\"");
            }

            if (addOn.getPrice() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + addOn.getName() + "\" has no price yet");
            }

            CartItemAddOn chosen = new CartItemAddOn();
            chosen.setAddOn(addOn);
            chosen.setAddOnName(addOn.getName());
            chosen.setUnitPrice(addOn.getPrice());
            chosen.setUsePerOrder(
                    addOn.getUsePerOrder() == null ? BigDecimal.ONE : addOn.getUsePerOrder());
            resolved.add(chosen);
        }

        return resolved;
    }

    private ItemVariant resolveVariant(Item item, UUID variantId) {
        if (variantId == null) {
            return null;
        }

        return item.getVariants().stream()
                .filter(candidate -> candidate.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Variant has not been found: " + variantId));
    }

    /**
     * What this line is: the unit it is sold in, what one of them holds, and
     * what the web charges for it.
     */
    private record PricedLine(Unit unit, BigDecimal unitFactor, BigDecimal unitPrice, BigDecimal channelPrice) {
    }

    /**
     * What the web charges for this line, in whatever unit it is being bought.
     *
     * A larger unit is priced in its own right — a case is not twenty-four
     * times a can, or nobody would buy the case — so a pack's price replaces
     * the single price rather than multiplying it, and the factor only says
     * what comes off the shelf.
     *
     * The channel gets the last word either way, because a shop that marked
     * its online prices up meant the basket too. Without that the storefront
     * quotes the web price and the basket quietly bills the business price.
     */
    private PricedLine resolveLine(
            Business business, Item item, ItemVariant variant, UUID unitId) {

        Unit unit = item.getUnit();
        BigDecimal unitFactor = BigDecimal.ONE;
        BigDecimal price = variant != null && variant.getPrice() != null
                ? variant.getPrice()
                : item.getPrice();

        UUID baseUnitId = item.getUnit() == null ? null : item.getUnit().getId();

        if (unitId != null && !unitId.equals(baseUnitId)) {
            // A pack belongs to one option: the case defined for Large is not
            // the one defined for Small, and a shop need not sell both. So the
            // line's option is part of finding it, never a fallback to some
            // other option's case — that would sell what nobody offered.
            UUID lineVariantId = variant == null ? null : variant.getId();

            ItemUomConversion conversion = item.getUomConversions().stream()
                    .filter(candidate -> candidate.getUnit() != null
                            && candidate.getUnit().getId().equals(unitId))
                    .filter(candidate -> Objects.equals(
                            candidate.getVariant() == null ? null : candidate.getVariant().getId(),
                            lineVariantId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "\"" + nameOf(item, variant) + "\" is not sold by that unit"));

            if (conversion.getPrice() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + nameOf(item, variant) + "\" has no price per "
                                + conversion.getUnit().getName());
            }

            unit = conversion.getUnit();
            unitFactor = conversion.getFactor() == null ? BigDecimal.ONE : conversion.getFactor();
            price = conversion.getPrice();
        }

        if (price == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item has no price yet");
        }

        BigDecimal channelPrice = channelPriceResolver.priceFor(
                business.getId(),
                WEB_CHANNEL_CODE,
                price,
                item.getId(),
                variant == null ? null : variant.getId(),
                // A pack on the item's own base unit is the item sold plainly,
                // and that exception is stored with no unit at all.
                unit == null || unit.getId().equals(baseUnitId) ? null : unit.getId());

        BigDecimal finalPrice = channelPrice;
        if (discountService != null) {
            try {
                UUID itemGroupId = item.getItemGroup() != null ? item.getItemGroup().getId() : null;
                List<kh.edu.istad.ite.features.discount.dto.DiscountResponse> discounts = discountService.findApplicableDiscounts(
                        business.getId(), OrderChannel.WEB, item.getId(), itemGroupId);

                List<kh.edu.istad.ite.features.discount.dto.DiscountResponse> autoDiscounts = discounts.stream()
                        .filter(d -> !Boolean.TRUE.equals(d.requiresCoupon()))
                        .toList();

                if (!autoDiscounts.isEmpty()) {
                    kh.edu.istad.ite.features.discount.dto.DiscountResponse best = autoDiscounts.stream()
                            .sorted((d1, d2) -> {
                                int s1 = (d1.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.SPECIFIC_ITEMS || d1.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.ITEM) ? 2
                                        : (d1.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.SPECIFIC_CATEGORIES || d1.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.CATEGORY) ? 1 : 0;
                                int s2 = (d2.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.SPECIFIC_ITEMS || d2.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.ITEM) ? 2
                                        : (d2.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.SPECIFIC_CATEGORIES || d2.scope() == kh.edu.istad.ite.shared.enums.DiscountScope.CATEGORY) ? 1 : 0;
                                if (s1 != s2) return Integer.compare(s2, s1);

                                int r1 = d1.ruleType() == kh.edu.istad.ite.shared.enums.DiscountRuleType.BUY_X_GET_Y ? 2 : 0;
                                int r2 = d2.ruleType() == kh.edu.istad.ite.shared.enums.DiscountRuleType.BUY_X_GET_Y ? 2 : 0;
                                if (r1 != r2) return Integer.compare(r2, r1);

                                BigDecimal v1 = d1.value() != null ? d1.value() : BigDecimal.ZERO;
                                BigDecimal v2 = d2.value() != null ? d2.value() : BigDecimal.ZERO;
                                return v2.compareTo(v1);
                            })
                            .findFirst()
                            .orElse(autoDiscounts.get(0));

                    BigDecimal discountAmount = BigDecimal.ZERO;
                    if (best.type() == kh.edu.istad.ite.shared.enums.DiscountType.PERCENTAGE && best.value() != null) {
                        discountAmount = channelPrice.multiply(best.value()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    } else if (best.type() == kh.edu.istad.ite.shared.enums.DiscountType.FIXED_AMOUNT && best.value() != null) {
                        discountAmount = best.value();
                    }

                    if (best.maxDiscountAmount() != null && discountAmount.compareTo(best.maxDiscountAmount()) > 0) {
                        discountAmount = best.maxDiscountAmount();
                    }

                    finalPrice = channelPrice.subtract(discountAmount);
                    if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
                        finalPrice = BigDecimal.ZERO;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not apply discount to cart line: {}", e.getMessage());
            }
        }

        return new PricedLine(unit, unitFactor, finalPrice, channelPrice);
    }

    /** How a line should be named when something goes wrong with it. */
    private String nameOf(Item item, ItemVariant variant) {
        return variant == null
                ? item.getName()
                : item.getName() + " (" + variant.getVariantName() + ")";
    }

    /**
     * Refuses a basket the web cannot supply, while it can still be changed.
     *
     * The checkout checks this too and must, since the shelf moves in between
     * — but finding out at the payment screen that the second of three lines
     * was never available is the worst moment to be told. Counted against what
     * the line would hold in total, not the increment, or adding one at a time
     * walks straight past the ceiling.
     *
     * Measured in base units, because that is how the shelf is counted: two
     * six-packs need twelve bottles, not two.
     */
    private void requireStock(
            Business business, Item item, ItemVariant variant, BigDecimal totalBaseQuantity) {
        if (!ItemType.PHYSICAL.equals(item.getItemType())) {
            return;
        }

        StockSummaryResponse summary = stockEntryService.findAvailableStock(
                business.getId(), item.getId(), variant == null ? null : variant.getId());

        // No entries at all means the shop is not tracking this item, which is
        // not the same as it having none.
        if (summary == null || summary.lastEntryId() == null) {
            return;
        }

        BigDecimal onHand = summary.quantityOnHand() == null ? BigDecimal.ZERO : summary.quantityOnHand();
        BigDecimal available = itemChannelStockService.availableFor(
                item, variant, OrderChannel.WEB, onHand);

        if (available.compareTo(totalBaseQuantity) < 0) {
            // Named by the option, or the shopper is told the item is out
            // while the shop is full of the size they did not ask for.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "\"" + nameOf(item, variant) + "\" only has "
                            + available.stripTrailingZeros().toPlainString() + " left");
        }
    }

    private String toPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return minioService.getPublicUrl(key);
    }

    private CartSummaryResponse.StoreCart toStoreCart(Cart cart) {
        Business business = cart.getBusiness();

        List<CartSummaryResponse.Line> lines = cart.getItems().stream()
                .map(this::toLine)
                .toList();

        return new CartSummaryResponse.StoreCart(
                cart.getId(),
                business.getId(),
                business.getSlug(),
                business.getDisplayName(),
                business.getBusinessCategory() == null ? null : business.getBusinessCategory().getName(),
                toPublicUrl(business.getLogo()),
                business.getAddress(),

                null,
                StringUtils.hasText(business.getBaseCurrency()) ? business.getBaseCurrency() : "USD",
                // The online store's hours count as much as the switch does:
                // the checkout refuses an out-of-hours basket, so a cart that
                // reads "Open" would only be setting the shopper up to be told
                // no at the payment screen.
                Boolean.TRUE.equals(business.getIsEnabled())
                        && !Boolean.TRUE.equals(business.getIsClosed())
                        && channelPriceResolver.isOpenNow(business.getId(), WEB_CHANNEL_CODE),
                cart.getTotalItemsCount(),
                cart.getTotalAmount(),
                lines);
    }

    private CartSummaryResponse.Line toLine(CartItem line) {
        ItemVariant variant = line.getVariant();

        List<String> badges = new ArrayList<>();

        if (variant != null && StringUtils.hasText(variant.getVariantName())) {
            badges.add(variant.getVariantName());
        }

        List<CartItemSelection> chosen = line.getSelections() == null
                ? List.of()
                : line.getSelections();

        // "Sugar Level: 50%" reads as one chip; the name alone would not say
        // enough and the value alone would not say what it answers.
        chosen.forEach(selection ->
                badges.add(selection.getAttributeName() + ": " + selection.display()));

        Unit unit = line.getUnit();
        BigDecimal factor = line.getUnitFactor() == null ? BigDecimal.ONE : line.getUnitFactor();

        // Only worth a chip when it is a pack. "per Bottle" on every single
        // line is noise the shopper already knows.
        if (unit != null && factor.compareTo(BigDecimal.ONE) > 0) {
            badges.add(unit.getName());
        }

        List<CartItemAddOn> extras = line.getAddOns() == null ? List.of() : line.getAddOns();

        // "+ Extra shot" reads as an addition rather than as another option,
        // which is what it is — and what the counter's ticket says too.
        extras.forEach(addOn -> badges.add("+ " + addOn.getAddOnName()));

        return new CartSummaryResponse.Line(
                line.getId(),
                line.getItem().getId(),
                variant == null ? null : variant.getId(),
                line.getItem().getName(),
                line.getItem().getDescription(),
                coverImageOf(line.getItem()),
                badges,
                chosen.stream()
                        .map(selection -> new CartSummaryResponse.Selection(
                                selection.getAttributeName(),
                                selection.getValue(),
                                selection.display()))
                        .toList(),
                extras.stream()
                        .map(addOn -> new CartSummaryResponse.AddOn(
                                addOn.getAddOn() == null ? null : addOn.getAddOn().getId(),
                                addOn.getAddOnName(),
                                addOn.getUnitPrice()))
                        .toList(),
                unitIdOf(unit),
                unit == null ? null : unit.getName(),
                factor,
                line.getQuantity(),
                line.getPriceSnapshot(),
                line.priceWithAddOns(),
                line.getSubtotal());
    }


    private String coverImageOf(Item item) {
        if (item.getImages() == null || item.getImages().isEmpty()) {
            return null;
        }

        return toPublicUrl(item.getImages().getFirst().getImageKey());
    }
}