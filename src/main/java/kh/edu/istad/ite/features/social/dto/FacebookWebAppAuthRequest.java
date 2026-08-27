package kh.edu.istad.ite.features.social.dto;

import jakarta.validation.constraints.NotBlank;

public record FacebookWebAppAuthRequest(
        @NotBlank String businessId,
        @NotBlank String signedRequest
) {
}
