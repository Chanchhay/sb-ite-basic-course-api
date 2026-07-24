package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.BusinessCurrencyConfigurationResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessCurrencyRequest;

import java.util.UUID;

public interface BusinessCurrencyService {

    BusinessCurrencyConfigurationResponse createCurrency(UUID businessId, CreateBusinessCurrencyRequest request);

    BusinessCurrencyConfigurationResponse findAllCurrencies(UUID businessId);

    BusinessCurrencyResponse findCurrencyByCode(UUID businessId, String code);

    BusinessCurrencyConfigurationResponse updateCurrency(
            UUID businessId,
            String code,
            UpdateBusinessCurrencyRequest request
    );

    BusinessCurrencyConfigurationResponse setDisplayCurrency(UUID businessId, String code);

    BusinessCurrencyConfigurationResponse setBaseCurrency(UUID businessId, String code);

    BusinessCurrencyConfigurationResponse removeCurrency(UUID businessId, String code);
}
