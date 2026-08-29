package kh.edu.istad.ite.features.business;

import kh.edu.istad.ite.features.business.dto.PublicFacebookPageResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.service.StorefrontService;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/stores")
@RequiredArgsConstructor
public class PublicStoreController {

    private final StorefrontService storefrontService;

    @GetMapping
    public Page<PublicStoreResponse> getStores(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String cityOrProvince,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @PageableDefault(size = 12, sort = "displayName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return storefrontService.getPublicStores(categoryId, province, district, cityOrProvince, keyword, lat, lng, pageable);
    }

    /** Distinct province names actually used by listed stores — powers the /store filter without a seeded location table. */
    @GetMapping("/provinces")
    public List<String> getProvinces() {
        return storefrontService.getDistinctProvinces();
    }

    @GetMapping("/{slug}")
    public PublicStoreDetailResponse getStore(
            @PathVariable String slug,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return storefrontService.getPublicStoreBySlug(slug, lat, lng);
    }

    @GetMapping("/{slug}/items")
    public List<ItemResponse> getStoreItems(@PathVariable String slug) {
        return storefrontService.getPublicStoreItems(slug);
    }

    @GetMapping("/{slug}/item-groups")
    public List<ItemGroupResponse> getStoreItemGroups(@PathVariable String slug) {
        return storefrontService.getPublicStoreItemGroups(slug);
    }

    /** The public "Find us on Facebook" link — both fields null when this store has no Page connected. */
    @GetMapping("/{slug}/social-settings/facebook")
    public PublicFacebookPageResponse getStoreFacebookSocialSettings(@PathVariable String slug) {
        return storefrontService.getPublicFacebookSocialSettings(slug);
    }

    @GetMapping("/recommended")
    public Page<PublicStoreResponse> getRecommendedStores(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        return storefrontService.getRecommendedStores(categoryId, lat, lng, pageable);
    }
}
