package kh.edu.istad.ite.features.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record FacebookDeviceAuthRequest(
        @NotBlank String businessId,
        @NotBlank @Size(max = 100) String deviceId,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 30) String phoneNumber
) {
}
