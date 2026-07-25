package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.business.dto.SlugAvailabilityResponse;
import kh.edu.istad.ite.features.business.dto.StorefrontSlugRequest;
import kh.edu.istad.ite.features.business.dto.StorefrontStatusResponse;
import kh.edu.istad.ite.features.business.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/storefront")
@RequiredArgsConstructor
public class StorefrontController {

    private final StorefrontService storefrontService;

    @GetMapping
    public StorefrontStatusResponse getMyStorefront() {
        return storefrontService.getMyStorefront();
    }

    @GetMapping("/slug-availability")
    public SlugAvailabilityResponse checkSlugAvailability(@RequestParam String slug) {
        return storefrontService.checkSlugAvailability(slug);
    }

    @PatchMapping("/slug")
    public StorefrontStatusResponse changeSlug(@Valid @RequestBody StorefrontSlugRequest request) {
        return storefrontService.changeSlug(request);
    }

    @PatchMapping("/enable")
    public StorefrontStatusResponse enableStorefront() {
        return storefrontService.enableStorefront();
    }

    @PatchMapping("/disable")
    public StorefrontStatusResponse disableStorefront() {
        return storefrontService.disableStorefront();
    }
}
