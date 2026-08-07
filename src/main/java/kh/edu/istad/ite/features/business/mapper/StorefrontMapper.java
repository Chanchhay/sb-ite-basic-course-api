package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StorefrontMapper {

    private final StorefrontProps storefrontProps;
    private final BusinessMapper businessMapper;
    private final MinioService minioService;
    private final DiscountRepository discountRepository;

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

    public String resolveDiscountLabel(Business business) {
        if (business == null || business.getId() == null) {
            return null;
        }
        List<Discount> activeDiscounts = discountRepository.findActiveDiscountsByBusinessId(
                business.getId(),
                RecordStatus.ACTIVE,
                LocalDateTime.now()
        ).stream()
         .filter(d -> d.getScope() == DiscountScope.ORDER || d.getScope() == DiscountScope.ALL_ITEMS)
         .toList();
        if (activeDiscounts == null || activeDiscounts.isEmpty()) {
            return null;
        }

        Discount primary = activeDiscounts.stream()
                .filter(d -> d.getType() == DiscountType.PERCENTAGE)
                .max((d1, d2) -> d1.getValue().compareTo(d2.getValue()))
                .orElse(activeDiscounts.get(0));

        if (primary.getRuleType() == DiscountRuleType.BUY_X_GET_Y) {
            int buy = primary.getBuyQuantity() != null ? primary.getBuyQuantity() : 1;
            int get = primary.getGetQuantity() != null ? primary.getGetQuantity() : 1;
            return "Buy " + buy + " Get " + get;
        }
        if (primary.getType() == DiscountType.PERCENTAGE && primary.getValue() != null) {
            String pct = primary.getValue().stripTrailingZeros().toPlainString();
            return pct + "% OFF";
        }
        if (primary.getType() == DiscountType.FIXED_AMOUNT && primary.getValue() != null) {
            String amt = primary.getValue().stripTrailingZeros().toPlainString();
            return "$" + amt + " OFF";
        }
        return primary.getName();
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
                resolveDiscountLabel(business),
                business.getOpenTime(),
                business.getCloseTime()
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
                resolveDiscountLabel(business),
                business.getOpenTime(),
                business.getCloseTime()
        );
    }
}