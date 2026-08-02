package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.dto.SlugAvailabilityResponse;
import kh.edu.istad.ite.features.business.dto.StorefrontSlugRequest;
import kh.edu.istad.ite.features.business.dto.StorefrontStatusResponse;
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
            String cityOrProvince,
            String keyword,
            Pageable pageable
    );

    PublicStoreDetailResponse getPublicStoreBySlug(String slug);

    List<ItemResponse> getPublicStoreItems(String slug);

    Page<PublicStoreResponse> getRecommendedStores(UUID categoryId, Pageable pageable);
}
