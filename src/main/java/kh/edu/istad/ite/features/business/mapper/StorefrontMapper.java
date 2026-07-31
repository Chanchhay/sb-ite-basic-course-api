package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorefrontMapper {

    private final StorefrontProps storefrontProps;
    private final BusinessMapper businessMapper;
    private final MinioService minioService;

    public String buildStorefrontUrl(String slug) {
        if (slug == null) {
            return null;
        }

        if (storefrontProps.isSubdomainEnabled()) {
            return storefrontProps.getProtocol() + "://" + slug + "." + storefrontProps.getBaseDomain();
        }

        return storefrontProps.getProtocol() + "://" + storefrontProps.getBaseDomain()
                + storefrontProps.getPathPrefix() + "/" + slug;
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

    public PublicStoreResponse toPublicResponse(Business business) {
        boolean isClosed = Boolean.TRUE.equals(business.getIsClosed());
        return new PublicStoreResponse(
                business.getId(),
                business.getSlug(),
                business.getDisplayName(),
                toPublicUrl(business.getLogo()),
                toPublicUrl(business.getThumbnail()),
                business.getAbout(),
                business.getCityOrProvince(),
                buildStorefrontUrl(business.getSlug()),
                businessMapper.toSubCategoryResponse(business.getBusinessCategory()),
                isClosed,
                !isClosed,
                null
        );
    }

    public PublicStoreDetailResponse toPublicDetailResponse(Business business) {
        boolean isClosed = Boolean.TRUE.equals(business.getIsClosed());
        return new PublicStoreDetailResponse(
                business.getId(),
                business.getSlug(),
                business.getDisplayName(),
                toPublicUrl(business.getLogo()),
                toPublicUrl(business.getThumbnail()),
                business.getAbout(),
                business.getPhoneNumber(),
                business.getAddress(),
                business.getCityOrProvince(),
                business.getGoogleMap(),
                business.getWebsite(),
                buildStorefrontUrl(business.getSlug()),
                business.getBaseCurrency(),
                business.getDisplayCurrency(),
                businessMapper.toSubCategoryResponse(business.getBusinessCategory()),
                business.getSocialLinks(),
                isClosed,
                !isClosed,
                null
        );
    }
}