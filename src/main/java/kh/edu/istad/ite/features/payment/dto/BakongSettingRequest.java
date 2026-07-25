package kh.edu.istad.ite.features.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.KhqrAccountType;

public record BakongSettingRequest(
        @NotNull
        KhqrAccountType accountType,

        @NotBlank
        @Size(max = 32)
        String bakongAccountId,

        @NotBlank
        @Size(max = 25)
        String merchantName,

        @NotBlank
        @Size(max = 15)
        String merchantCity,

        @Size(max = 32)
        String merchantId,

        @Size(max = 32)
        String acquiringBank,

        @Size(max = 20)
        String mobileNumber,

        @Size(max = 25)
        String storeLabel,

        String apiToken
) {
}
