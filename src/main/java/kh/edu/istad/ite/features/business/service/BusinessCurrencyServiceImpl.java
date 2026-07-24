package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyConfigurationResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCurrencyRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessCurrencyServiceImpl implements BusinessCurrencyService {

    private static final int EXCHANGE_RATE_SCALE = 8;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP);

    private final BusinessRepository businessRepository;
    private final BusinessCurrencyRepository businessCurrencyRepository;
    private final BusinessMapper businessMapper;

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse createCurrency(
            UUID businessId,
            CreateBusinessCurrencyRequest request
    ) {
        Business business = findOwnedBusiness(businessId);
        String code = normalizeCode(request.code());

        if (businessCurrencyRepository.existsByBusinessIdAndCodeIgnoreCase(businessId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency code already exists");
        }

        BusinessCurrency currency = new BusinessCurrency();
        currency.setBusiness(business);
        currency.setCode(code);
        currency.setName(request.name().trim());
        currency.setSymbol(request.symbol().trim());
        currency.setExchangeRate(normalizeExchangeRate(request.exchangeRate()));
        currency.setDecimalPlaces(validateDecimalPlaces(request.decimalPlaces()));

        try {
            businessCurrencyRepository.saveAndFlush(currency);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency code already exists", e);
        }

        return latestConfiguration(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCurrencyConfigurationResponse findAllCurrencies(UUID businessId) {
        Business business = findOwnedBusiness(businessId);
        return latestConfiguration(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCurrencyResponse findCurrencyByCode(UUID businessId, String code) {
        Business business = findOwnedBusiness(businessId);
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
        Business business = findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);

        if (request.name() != null) {
            currency.setName(trimRequired(request.name(), "Currency name cannot be empty"));
        }
        if (request.symbol() != null) {
            currency.setSymbol(trimRequired(request.symbol(), "Currency symbol cannot be empty"));
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
        Business business = findOwnedBusiness(businessId);
        BusinessCurrency currency = findCurrency(businessId, code);

        business.setDisplayCurrency(currency.getCode());
        businessRepository.save(business);

        return latestConfiguration(business);
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse setBaseCurrency(UUID businessId, String code) {
        Business business = findOwnedBusiness(businessId);
        BusinessCurrency newBaseCurrency = findCurrency(businessId, code);

        if (newBaseCurrency.getCode().equalsIgnoreCase(business.getBaseCurrency())) {
            return latestConfiguration(business);
        }

        BigDecimal newBaseOldRate = normalizeExchangeRate(newBaseCurrency.getExchangeRate());
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

        return businessMapper.toCurrencyConfigurationResponse(business, currencies);
    }

    @Override
    @Transactional
    public BusinessCurrencyConfigurationResponse removeCurrency(UUID businessId, String code) {
        Business business = findOwnedBusiness(businessId);
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

    private Business findOwnedBusiness(UUID businessId) {
        UUID keycloakUserId = UUID.fromString(SecurityUtils.extractUserId());
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));

        if (!business.getKeycloakUserId().equals(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        return business;
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

    private String trimRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        return value.trim();
    }
}
