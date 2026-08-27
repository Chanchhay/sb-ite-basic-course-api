package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyConfigurationResponse;
import kh.edu.istad.ite.features.business.dto.BusinessCurrencyResponse;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.dto.BusinessSubCategoryResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.minio.MinioService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessMapper {

    private final MinioService minioService;

    public BusinessMapper(MinioService minioService) {
        this.minioService = minioService;
    }

    public BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getKeycloakUserId(),
                business.getSlug(),
                business.getDisplayName(),
                business.getStatus(),
                business.getProvisionedAt(),
                business.getLogo() == null ? null : minioService.getPublicUrl(business.getLogo()),
                business.getThumbnail() == null ? null : minioService.getPublicUrl(business.getThumbnail()),
                business.getAbout(),
                business.getPhoneNumber(),
                business.getGoogleMap(),
                business.getAddress(),
                business.getCityOrProvince(),
                business.getProvinceName(),
                business.getDistrictName(),
                business.getCommuneName(),
                business.getLatitude(),
                business.getLongitude(),
                business.getWebsite(),
                business.getBusinessEmail(),
                business.getIsEnabled(),
                business.getIsListing(),
                business.getIsClosed(),
                toSubCategoryResponse(business.getBusinessCategory()),
                business.getBaseCurrency(),
                business.getDisplayCurrency(),
                business.getSocialLinks(),
                business.getOpenTime(),
                business.getCloseTime(),
                business.getTaxEnabled(),
                business.getTaxRate(),
                business.getTaxInclusionType(),
                business.getTaxLabel()
        );
    }

    public BusinessCurrencyResponse toCurrencyResponse(
            BusinessCurrency currency,
            String baseCurrency,
            String displayCurrency
    ) {
        return new BusinessCurrencyResponse(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                currency.getExchangeRate(),
                currency.getSymbol(),
                currency.getDecimalPlaces(),
                currency.getCode().equalsIgnoreCase(baseCurrency),
                currency.getCode().equalsIgnoreCase(displayCurrency)
        );
    }

    public BusinessCurrencyConfigurationResponse toCurrencyConfigurationResponse(
            Business business,
            List<BusinessCurrency> currencies
    ) {
        return new BusinessCurrencyConfigurationResponse(
                business.getBaseCurrency(),
                business.getDisplayCurrency(),
                currencies.stream()
                        .map(currency -> toCurrencyResponse(
                                currency,
                                business.getBaseCurrency(),
                                business.getDisplayCurrency()
                        ))
                        .toList()
        );
    }

    public BusinessSubCategoryResponse toSubCategoryResponse(BusinessCategory category) {
        if (category == null) {
            return null;
        }

        return new BusinessSubCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }

    public BusinessCategoryResponse toCategoryTreeResponse(
            BusinessCategory category,
            List<BusinessCategory> subCategories
    ) {
        return new BusinessCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                subCategories.stream()
                        .map(this::toSubCategoryResponse)
                        .toList()
        );
    }
}
