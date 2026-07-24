package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import org.springframework.stereotype.Component;

@Component
public class BusinessMapper {

    public BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getKeycloakUserId(),
                business.getSlug(),
                business.getDisplayName(),
                business.getStatus(),
                business.getProvisionedAt(),
                business.getLogo(),
                business.getThumbnail(),
                business.getAbout(),
                business.getPhoneNumber(),
                business.getGoogleMap(),
                business.getAddress(),
                business.getCityOrProvince(),
                business.getWebsite(),
                business.getBusinessEmail(),
                business.getIsEnabled(),
                business.getIsListing(),
                business.getIsClosed(),
                toCategoryResponse(business.getBusinessCategory()),
                business.getBaseCurrency(),
                business.getDisplayCurrency(),
                business.getSocialLinks()
        );
    }

    public BusinessCategoryResponse toCategoryResponse(BusinessCategory category) {
        if (category == null) {
            return null;
        }

        return new BusinessCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }
}
