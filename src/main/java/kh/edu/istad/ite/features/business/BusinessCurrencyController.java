package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyConfigurationResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessCurrencyRequest;
import kh.edu.istad.ite.features.business.service.BusinessCurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/currencies")
@RequiredArgsConstructor
public class BusinessCurrencyController {

    private final BusinessCurrencyService businessCurrencyService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public BusinessCurrencyConfigurationResponse createCurrency(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateBusinessCurrencyRequest request
    ) {
        return businessCurrencyService.createCurrency(businessId, request);
    }

    @GetMapping
    public BusinessCurrencyConfigurationResponse findAllCurrencies(@PathVariable UUID businessId) {
        return businessCurrencyService.findAllCurrencies(businessId);
    }

    @GetMapping("/{code}")
    public BusinessCurrencyResponse findCurrencyByCode(
            @PathVariable UUID businessId,
            @PathVariable String code
    ) {
        return businessCurrencyService.findCurrencyByCode(businessId, code);
    }

    @PutMapping("/{code}")
    public BusinessCurrencyConfigurationResponse updateCurrency(
            @PathVariable UUID businessId,
            @PathVariable String code,
            @Valid @RequestBody UpdateBusinessCurrencyRequest request
    ) {
        return businessCurrencyService.updateCurrency(businessId, code, request);
    }

    @PutMapping("/{code}/display")
    public BusinessCurrencyConfigurationResponse setDisplayCurrency(
            @PathVariable UUID businessId,
            @PathVariable String code
    ) {
        return businessCurrencyService.setDisplayCurrency(businessId, code);
    }

    @PutMapping("/{code}/base")
    public BusinessCurrencyConfigurationResponse setBaseCurrency(
            @PathVariable UUID businessId,
            @PathVariable String code
    ) {
        return businessCurrencyService.setBaseCurrency(businessId, code);
    }

    @DeleteMapping("/{code}")
    public BusinessCurrencyConfigurationResponse removeCurrency(
            @PathVariable UUID businessId,
            @PathVariable String code
    ) {
        return businessCurrencyService.removeCurrency(businessId, code);
    }
}
