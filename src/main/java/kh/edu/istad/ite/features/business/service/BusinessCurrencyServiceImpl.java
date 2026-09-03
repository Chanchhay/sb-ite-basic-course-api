package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.BusinessCurrencyConfigurationResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessCurrencyConfigurationRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCurrencyRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemVariantRepository;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.repository.CouponRepository;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.register.repository.RegisterSessionRepository;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessCurrencyServiceImpl implements BusinessCurrencyService {

    private static final int EXCHANGE_RATE_SCALE = 8;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP);

    private final BusinessRepository businessRepository;
    private final BusinessCurrencyRepository businessCurrencyRepository;
    private final BusinessMapper businessMapper;
    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final StockEntryRepository stockEntryRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final RegisterSessionRepository registerSessionRepository;

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse createCurrency(
            UUID businessId,
            CreateBusinessCurrencyRequest request
    ) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        String code = normalizeCode(request.code());

        if (businessCurrencyRepository.existsByBusinessIdAndCodeIgnoreCase(businessId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency code already exists");
        }

        BusinessCurrency currency = new BusinessCurrency();
        currency.setBusiness(business);
        currency.setCode(code);
        currency.setName(TextHelper.trimRequired(request.name(), "Currency name cannot be empty"));
        currency.setSymbol(TextHelper.trimRequired(request.symbol(), "Currency symbol cannot be empty"));
        currency.setExchangeRate(normalizeExchangeRate(request.exchangeRate()));
        currency.setDecimalPlaces(validateDecimalPlaces(request.decimalPlaces()));

        try {
            businessCurrencyRepository.saveAndFlush(currency);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency code already exists", e);
        }

        return latestConfiguration(business);
    }

    /**
     * Applies the whole configuration at once.
     *
     * <p>The currency list is the desired end state, so codes missing from it
     * are removed. Rates are read as already quoted against the requested base
     * currency, and the base's own rate is forced to 1 rather than validated —
     * a currency's rate against itself is not really the caller's to supply.
     *
     * <p>Doing this in one transaction is the point: applied as separate calls,
     * the intermediate states trip the per-currency rules (an outgoing base
     * whose rate is no longer 1, a delete of the currency still marked for
     * display), so the caller would have to know the safe ordering.
     */
    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse replaceConfiguration(
            UUID businessId,
            UpdateBusinessCurrencyConfigurationRequest request
    ) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        String baseCode = normalizeCode(request.baseCurrency());
        String displayCode = StringUtils.hasText(request.displayCurrency())
                ? normalizeCode(request.displayCurrency())
                : baseCode;

        Map<String, CreateBusinessCurrencyRequest> desired = new LinkedHashMap<>();
        for (CreateBusinessCurrencyRequest currency : request.currencies()) {
            String code = normalizeCode(currency.code());
            if (desired.putIfAbsent(code, currency) != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate currency code: " + code
                );
            }
        }

        if (!desired.containsKey(baseCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Base currency must be one of the submitted currencies"
            );
        }
        if (!desired.containsKey(displayCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Display currency must be one of the submitted currencies"
            );
        }

        Map<String, BusinessCurrency> existingByCode = new LinkedHashMap<>();
        for (BusinessCurrency currency : businessCurrencyRepository.findAllByBusinessIdOrderByCodeAsc(businessId)) {
            existingByCode.put(currency.getCode().toUpperCase(Locale.ROOT), currency);
        }

        // Read before the rates below are overwritten. Prices are held in the
        // base currency, so moving the base has to restate them by however many
        // new base units make up one old base unit.
        BigDecimal repriceFactor = baseCurrencyChangeFactor(
                business.getBaseCurrency(),
                baseCode,
                existingByCode,
                desired
        );
        guardNoOpenRegister(businessId, repriceFactor);

        // Removals first, so a code can be dropped and the rest saved without
        // colliding on the per-business unique constraint.
        List<BusinessCurrency> removed = existingByCode.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!removed.isEmpty()) {
            businessCurrencyRepository.deleteAll(removed);
            businessCurrencyRepository.flush();
        }

        List<BusinessCurrency> currencies = new ArrayList<>();
        for (Map.Entry<String, CreateBusinessCurrencyRequest> entry : desired.entrySet()) {
            String code = entry.getKey();
            CreateBusinessCurrencyRequest source = entry.getValue();

            BusinessCurrency currency = existingByCode.get(code);
            if (currency == null) {
                currency = new BusinessCurrency();
                currency.setBusiness(business);
                currency.setCode(code);
            }

            currency.setName(TextHelper.trimRequired(source.name(), "Currency name cannot be empty"));
            currency.setSymbol(TextHelper.trimRequired(source.symbol(), "Currency symbol cannot be empty"));
            currency.setDecimalPlaces(validateDecimalPlaces(source.decimalPlaces()));
            currency.setExchangeRate(
                    code.equals(baseCode) ? ONE : normalizeExchangeRate(source.exchangeRate())
            );

            currencies.add(currency);
        }

        try {
            businessCurrencyRepository.saveAllAndFlush(currencies);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency code already exists", e);
        }

        business.setBaseCurrency(baseCode);
        business.setDisplayCurrency(displayCode);
        businessRepository.save(business);

        repriceToNewBase(
                businessId,
                baseCode,
                repriceFactor,
                validateDecimalPlaces(desired.get(baseCode).decimalPlaces())
        );

        return businessMapper.toCurrencyConfigurationResponse(business, currencies);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCurrencyConfigurationResponse findAllCurrencies(UUID businessId) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        return latestConfiguration(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCurrencyResponse findCurrencyByCode(UUID businessId, String code) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);
        return businessMapper.toCurrencyResponse(
                currency,
                business.getBaseCurrency(),
                business.getDisplayCurrency()
        );
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse updateCurrency(
            UUID businessId,
            String code,
            UpdateBusinessCurrencyRequest request
    ) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);

        if (request.name() != null) {
            currency.setName(TextHelper.trimRequired(request.name(), "Currency name cannot be empty"));
        }
        if (request.symbol() != null) {
            currency.setSymbol(TextHelper.trimRequired(request.symbol(), "Currency symbol cannot be empty"));
        }
        if (request.decimalPlaces() != null) {
            currency.setDecimalPlaces(validateDecimalPlaces(request.decimalPlaces()));
        }
        if (request.exchangeRate() != null) {
            BigDecimal exchangeRate = normalizeExchangeRate(request.exchangeRate());
            if (currency.getCode().equalsIgnoreCase(business.getBaseCurrency())
                    && exchangeRate.compareTo(ONE) != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Base currency exchange rate must be 1"
                );
            }
            currency.setExchangeRate(exchangeRate);
        }

        businessCurrencyRepository.save(currency);

        return latestConfiguration(business);
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse setDisplayCurrency(UUID businessId, String code) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);

        business.setDisplayCurrency(currency.getCode());
        businessRepository.save(business);

        return latestConfiguration(business);
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse setBaseCurrency(UUID businessId, String code) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        BusinessCurrency newBaseCurrency = findCurrency(businessId, code);

        if (newBaseCurrency.getCode().equalsIgnoreCase(business.getBaseCurrency())) {
            return latestConfiguration(business);
        }

        BigDecimal newBaseOldRate = normalizeExchangeRate(newBaseCurrency.getExchangeRate());
        guardNoOpenRegister(businessId, newBaseOldRate);
        List<BusinessCurrency> currencies = businessCurrencyRepository.findAllByBusinessIdOrderByCodeAsc(businessId);

        for (BusinessCurrency currency : currencies) {
            if (currency.getCode().equalsIgnoreCase(newBaseCurrency.getCode())) {
                currency.setExchangeRate(ONE);
            } else {
                currency.setExchangeRate(
                        normalizeExchangeRate(currency.getExchangeRate())
                                .divide(newBaseOldRate, EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP)
                );
            }
        }

        businessCurrencyRepository.saveAllAndFlush(currencies);
        business.setBaseCurrency(newBaseCurrency.getCode());
        businessRepository.save(business);

        // Prices are denominated in the base, so they move with it. The old
        // base was 1, making the new base's former rate the conversion factor.
        repriceToNewBase(
                businessId,
                newBaseCurrency.getCode(),
                newBaseOldRate,
                newBaseCurrency.getDecimalPlaces()
        );

        return businessMapper.toCurrencyConfigurationResponse(business, currencies);
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse removeCurrency(UUID businessId, String code) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);

        if (currency.getCode().equalsIgnoreCase(business.getBaseCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove base currency");
        }
        if (currency.getCode().equalsIgnoreCase(business.getDisplayCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove display currency");
        }
        if (businessCurrencyRepository.countByBusinessId(businessId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the final remaining currency");
        }

        businessCurrencyRepository.deleteById(currency.getId());
        businessCurrencyRepository.flush();

        return latestConfiguration(business);
    }

    /**
     * Refuses a base-currency move while a till is open.
     *
     * <p>The notes in the drawer are physical and do not convert, and the
     * session's counted balances are a cash record rather than a price. Rather
     * than restate them or leave them mislabelled, the till has to be closed
     * first. Checked before anything is written so the request fails cleanly
     * instead of relying on the rollback.
     */
    private void guardNoOpenRegister(UUID businessId, BigDecimal repriceFactor) {
        if (repriceFactor == null) {
            return;
        }

        registerSessionRepository
                .findByBusinessIdAndStatus(businessId, SessionStatus.OPEN)
                .ifPresent(session -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Close the open register before changing the base currency"
                    );
                });
    }

    /**
     * How many new base units make up one old base unit, or null when the base
     * is not moving and prices should be left alone.
     *
     * <p>Rates are stored against the current base, so the new base's stored
     * rate is exactly that figure. When the new base is only being added in
     * this same request it has no stored rate, and the submitted rates — which
     * are quoted against the incoming base — give it by inversion instead.
     */
    private BigDecimal baseCurrencyChangeFactor(
            String previousBaseCode,
            String nextBaseCode,
            Map<String, BusinessCurrency> existingByCode,
            Map<String, CreateBusinessCurrencyRequest> desired
    ) {
        if (previousBaseCode == null || nextBaseCode.equalsIgnoreCase(previousBaseCode)) {
            return null;
        }

        BusinessCurrency storedNextBase = existingByCode.get(nextBaseCode);
        if (storedNextBase != null && storedNextBase.getExchangeRate() != null) {
            return storedNextBase.getExchangeRate();
        }

        CreateBusinessCurrencyRequest submittedPreviousBase =
                desired.get(previousBaseCode.toUpperCase(Locale.ROOT));
        if (submittedPreviousBase == null
                || submittedPreviousBase.exchangeRate() == null
                || submittedPreviousBase.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            // Nothing relates the two currencies, so repricing would be a guess.
            return null;
        }

        return ONE.divide(
                submittedPreviousBase.exchangeRate(),
                EXCHANGE_RATE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    /**
     * Restates every amount held in the base currency after the base moves.
     *
     * <p>Catalogue prices carry no currency of their own — they are simply
     * "in the base currency" — so moving the base without restating them would
     * silently relabel a $3.50 item as ៛3.50. Amounts are multiplied by the
     * number of new base units per old base unit and rounded to the new base's
     * decimal places.
     *
     * <p>Settled orders and sales are deliberately excluded: they record their
     * own currency and are history, not pricing.
     */
    private void repriceToNewBase(UUID businessId, String baseCode, BigDecimal factor, int decimalPlaces) {
        if (factor == null
                || factor.compareTo(BigDecimal.ZERO) <= 0
                || factor.compareTo(BigDecimal.ONE) == 0) {
            return;
        }


        List<Item> items = itemRepository.findAllByBusinessIdOrderByNameAsc(businessId);
        for (Item item : items) {
            item.setPrice(restate(item.getPrice(), factor, decimalPlaces));
        }
        itemRepository.saveAll(items);

        List<ItemVariant> variants = itemVariantRepository.findAllByBusiness_Id(businessId);
        for (ItemVariant variant : variants) {
            variant.setPrice(restate(variant.getPrice(), factor, decimalPlaces));
        }
        itemVariantRepository.saveAll(variants);

        List<Discount> discounts = discountRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId);
        for (Discount discount : discounts) {
            // A percentage is not money, and neither is a "buy X get Y" count.
            if (DiscountType.FIXED_AMOUNT.equals(discount.getType())) {
                discount.setValue(restate(discount.getValue(), factor, decimalPlaces));
            }
            discount.setMinOrderAmount(restate(discount.getMinOrderAmount(), factor, decimalPlaces));
            discount.setMaxDiscountAmount(restate(discount.getMaxDiscountAmount(), factor, decimalPlaces));
        }
        discountRepository.saveAll(discounts);

        List<Coupon> coupons = couponRepository.findAllByBusinessIdOrderByCreatedDateDesc(businessId);
        for (Coupon coupon : coupons) {
            coupon.setMinPurchaseAmount(restate(coupon.getMinPurchaseAmount(), factor, decimalPlaces));
        }
        couponRepository.saveAll(coupons);

        // Unit costs drive inventory valuation and the cost of goods on every
        // future sale, so leaving them behind would misstate both.
        List<StockEntry> stockEntries =
                stockEntryRepository.findAllByBusiness_IdOrderByCreatedDateDescIdDesc(businessId);
        for (StockEntry entry : stockEntries) {
            entry.setUnitCost(restate(entry.getUnitCost(), factor, decimalPlaces));
        }
        stockEntryRepository.saveAll(stockEntries);

        // Carts are unfinished orders, so their held prices move with the
        // catalogue rather than being honoured at the old number.
        List<CartItem> cartItems = cartItemRepository.findAllByCart_Business_Id(businessId);
        for (CartItem cartItem : cartItems) {
            cartItem.setPriceSnapshot(restate(cartItem.getPriceSnapshot(), factor, decimalPlaces));
        }
        cartItemRepository.saveAll(cartItems);

        repriceOpenOrders(businessId, baseCode, factor, decimalPlaces);
    }

    /**
     * Moves orders still being rung up onto the new base.
     *
     * An order freezes its currency at creation, so one opened before the
     * switch would otherwise keep naming the old code while every line added
     * after it is priced in the new base — the till then labels new-base
     * amounts with the old symbol, and the secondary line converts figures
     * that are already in the base a second time.
     *
     * Only PENDING orders move. A CONFIRMED or PAID one is a record of what
     * the customer was actually charged and stays at the numbers on its
     * receipt.
     */
    private void repriceOpenOrders(UUID businessId, String baseCode, BigDecimal factor, int decimalPlaces) {
        List<Order> orders =
                orderRepository.findAllWithItemsByBusinessIdAndStatus(businessId, OrderStatus.PENDING);

        for (Order order : orders) {
            order.setSubtotal(restate(order.getSubtotal(), factor, decimalPlaces));
            order.setDiscountAmount(restate(order.getDiscountAmount(), factor, decimalPlaces));
            // A rate is not money and a percentage is not either, so tax rate
            // is left alone while the amount it produced moves with the rest.
            order.setTaxAmount(restate(order.getTaxAmount(), factor, decimalPlaces));
            order.setTotal(restate(order.getTotal(), factor, decimalPlaces));
            order.setCurrency(baseCode);

            // Quoted against the order's currency, which just changed, so the
            // stale pair is dropped rather than left to misconvert. The next
            // push re-snapshots it from the configuration this call just saved.
            order.setDisplayCurrency(null);
            order.setDisplayExchangeRate(null);

            for (OrderItem line : order.getItems()) {
                line.setUnitPrice(restate(line.getUnitPrice(), factor, decimalPlaces));
                line.setUnitCost(restate(line.getUnitCost(), factor, decimalPlaces));
                line.setDiscountAmount(restate(line.getDiscountAmount(), factor, decimalPlaces));
                line.setLineTotal(restate(line.getLineTotal(), factor, decimalPlaces));
            }
        }

        orderRepository.saveAll(orders);
    }

    private BigDecimal restate(BigDecimal amount, BigDecimal factor, int decimalPlaces) {
        if (amount == null) {
            return null;
        }

        return amount.multiply(factor).setScale(decimalPlaces, RoundingMode.HALF_UP);
    }

    private BusinessCurrency findCurrency(UUID businessId, String code) {
        return businessCurrencyRepository.findByBusinessIdAndCodeIgnoreCase(businessId, normalizeCode(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Currency has not been found"));
    }

    private BusinessCurrencyConfigurationResponse latestConfiguration(Business business) {
        return businessMapper.toCurrencyConfigurationResponse(
                business,
                businessCurrencyRepository.findAllByBusinessIdOrderByCodeAsc(business.getId())
        );
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || code.trim().length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code must be exactly 3 characters");
        }

        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeExchangeRate(BigDecimal exchangeRate) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exchange rate must be greater than zero");
        }

        return exchangeRate.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private Short validateDecimalPlaces(Short decimalPlaces) {
        if (decimalPlaces == null || decimalPlaces < 0 || decimalPlaces > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decimal places must be between 0 and 3");
        }

        return decimalPlaces;
    }

}
