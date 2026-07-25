package kh.edu.istad.ite.features.payment.dto;

import kh.edu.istad.ite.shared.enums.KhqrAccountType;

import java.util.UUID;

public record BakongSettingResponse(
        UUID id,
        UUID businessId,
        KhqrAccountType accountType,
        String bakongAccountId,
        String merchantName,
        String merchantCity,
        String merchantId,
        String acquiringBank,
        String mobileNumber,
        String storeLabel,
        boolean apiTokenConfigured,
        boolean active
) {
}
