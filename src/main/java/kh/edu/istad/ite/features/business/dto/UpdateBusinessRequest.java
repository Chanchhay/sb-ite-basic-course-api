package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateBusinessRequest(
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "categoryId must be a valid UUID"
        )
        String categoryId,

        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        @Size(max = 255, message = "about must be at most 255 characters")
        String about,

        @Size(min = 8, max = 30, message = "phoneNumber must be between 8 and 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9 ]+$",
                message = "phoneNumber may contain digits, spaces, and an optional leading plus sign"
        )
        String phoneNumber,

        @Size(max = 255, message = "googleMap must be at most 255 characters")
        String googleMap,

        @Size(max = 255, message = "cityOrProvince must be at most 255 characters")
        String cityOrProvince,

        @Size(max = 150, message = "provinceName must be at most 150 characters")
        String provinceName,

        @Size(max = 150, message = "districtName must be at most 150 characters")
        String districtName,

        @Size(max = 150, message = "communeName must be at most 150 characters")
        String communeName,

        @DecimalMin(value = "9.0", message = "latitude must be within Cambodia's bounds")
        @DecimalMax(value = "15.0", message = "latitude must be within Cambodia's bounds")
        BigDecimal latitude,

        @DecimalMin(value = "102.0", message = "longitude must be within Cambodia's bounds")
        @DecimalMax(value = "108.0", message = "longitude must be within Cambodia's bounds")
        BigDecimal longitude,

        @Size(max = 255, message = "website must be at most 255 characters")
        String website,

        @Valid
        @Size(max = 20, message = "socialLinks must contain at most 20 links")
        List<SocialLinkRequest> socialLinks,

        @Size(max = 30, message = "openTime must be at most 30 characters")
        String openTime,

        @Size(max = 30, message = "closeTime must be at most 30 characters")
        String closeTime
) {
}
