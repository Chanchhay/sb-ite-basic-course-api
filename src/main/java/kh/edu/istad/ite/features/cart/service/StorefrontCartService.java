package kh.edu.istad.ite.features.cart.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.cart.dto.AddToCartRequest;
import kh.edu.istad.ite.features.cart.dto.CartCountResponse;
import kh.edu.istad.ite.features.cart.dto.CartSummaryResponse;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.ItemStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class StorefrontCartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BusinessRepository businessRepository;
    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityService customerIdentityService;
    private final BusinessHelper businessHelper;
    private final MinioService minioService;


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
        BigDecimal unitPrice = resolvePrice(item, variant);

        Customer customer = customerIdentityService.customerFor(business, shopper);
        Cart cart = activeCartFor(customer, business);

        CartItem line = findLine(cart, item.getId(), request.variantId()).orElse(null);

        if (line == null) {
            line = CartItem.builder()
                    .cart(cart)
                    .item(item)
                    .variant(variant)
                    .quantity(request.quantity())
                    .priceSnapshot(unitPrice)
                    .build();

            cart.getItems().add(line);
        } else {
            line.setQuantity(line.getQuantity() + request.quantity());
            line.setPriceSnapshot(unitPrice);
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
    }

    private Optional<CartItem> findLine(Cart cart, UUID itemId, UUID variantId) {
        return cart.getItems().stream()
                .filter(line -> line.getItem().getId().equals(itemId))
                .filter(line -> {
                    UUID lineVariant = line.getVariant() == null ? null : line.getVariant().getId();
                    return variantId == null ? lineVariant == null : variantId.equals(lineVariant);
                })
                .findFirst();
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

    private BigDecimal resolvePrice(Item item, ItemVariant variant) {
        BigDecimal price = variant != null && variant.getPrice() != null ? variant.getPrice() : item.getPrice();

        if (price == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item has no price yet");
        }

        return price;
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
                Boolean.TRUE.equals(business.getIsEnabled()) && !Boolean.TRUE.equals(business.getIsClosed()),
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

        return new CartSummaryResponse.Line(
                line.getId(),
                line.getItem().getId(),
                variant == null ? null : variant.getId(),
                line.getItem().getName(),
                line.getItem().getDescription(),
                coverImageOf(line.getItem()),
                badges,
                line.getQuantity(),
                line.getPriceSnapshot(),
                line.getSubtotal());
    }


    private String coverImageOf(Item item) {
        if (item.getImages() == null || item.getImages().isEmpty()) {
            return null;
        }

        return toPublicUrl(item.getImages().getFirst());
    }
}