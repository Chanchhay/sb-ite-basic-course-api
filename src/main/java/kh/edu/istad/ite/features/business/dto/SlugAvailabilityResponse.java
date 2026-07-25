package kh.edu.istad.ite.features.business.dto;

public record SlugAvailabilityResponse(
        String slug,
        boolean available,
        String previewUrl
) {
}
