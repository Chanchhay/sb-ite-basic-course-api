package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.PublicFacebookPageResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.dto.SlugAvailabilityResponse;
import kh.edu.istad.ite.features.business.dto.StorefrontSlugRequest;
import kh.edu.istad.ite.features.business.dto.StorefrontStatusResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StorefrontService {

    StorefrontStatusResponse getMyStorefront();

    StorefrontStatusResponse enableStorefront();

    StorefrontStatusResponse disableStorefront();

    StorefrontStatusResponse changeSlug(StorefrontSlugRequest request);

    SlugAvailabilityResponse checkSlugAvailability(String slug);

    Page<PublicStoreResponse> getPublicStores(
            UUID categoryId,
            String province,
            String district,
            String cityOrProvince,
            String keyword,
            Double lat,
            Double lng,
            Pageable pageable
    );

    PublicStoreDetailResponse getPublicStoreBySlug(String slug, Double lat, Double lng);

    List<ItemResponse> getPublicStoreItems(String slug);

    /** The category (and sub-category) tree for a public menu — same shape the business owner's own inventory screen uses. */
    List<ItemGroupResponse> getPublicStoreItemGroups(String slug);

    Page<PublicStoreResponse> getRecommendedStores(UUID categoryId, Double lat, Double lng, Pageable pageable);

    /** Every province name actually in use by a publicly-listed store — self-populating, nothing to seed. */
    List<String> getDistinctProvinces();

    /** The public "Find us on Facebook" link — both fields null when no Page is connected. */
    PublicFacebookPageResponse getPublicFacebookSocialSettings(String slugOrId);
}
