package kh.edu.istad.ite.features.payment.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.payment.dto.BakongSettingRequest;
import kh.edu.istad.ite.features.payment.dto.BakongSettingResponse;
import kh.edu.istad.ite.features.payment.dto.KhqrPreviewRequest;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.khqr.KhqrGenerator;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.shared.enums.KhqrAccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BakongSettingServiceImpl implements BakongSettingService {

    private static final int QR_VALIDITY_MINUTES = 5;

    private final BusinessRepository businessRepository;
    private final BusinessPaymentSettingRepository settingRepository;
    private final CredentialCipher credentialCipher;
    private final KhqrGenerator khqrGenerator;

    @Override
    @Transactional(readOnly = true)
    public BakongSettingResponse getMySetting() {
        return toResponse(findMySetting());
    }

    @Override
    @Transactional
    public BakongSettingResponse saveMySetting(BakongSettingRequest request) {
        Business business = findMyBusiness();

        if (KhqrAccountType.MERCHANT.equals(request.accountType())) {
            if (!StringUtils.hasText(request.merchantId()) || !StringUtils.hasText(request.acquiringBank())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "merchantId and acquiringBank are required for MERCHANT accounts"
                );
            }
        }

        BusinessPaymentSetting setting = settingRepository.findByBusiness_Id(business.getId())
                .orElseGet(() -> {
                    BusinessPaymentSetting created = new BusinessPaymentSetting();
                    created.setBusiness(business);
                    created.setIsActive(false);
                    return created;
                });

        setting.setAccountType(request.accountType());
        setting.setBakongAccountId(request.bakongAccountId().trim());
        setting.setMerchantName(request.merchantName().trim());
        setting.setMerchantCity(request.merchantCity().trim());
        setting.setMerchantId(trimToNull(request.merchantId()));
        setting.setAcquiringBank(trimToNull(request.acquiringBank()));
        setting.setMobileNumber(trimToNull(request.mobileNumber()));
        setting.setStoreLabel(trimToNull(request.storeLabel()));

        // An omitted token leaves the stored one untouched, so the owner can
        // edit merchant details without re-entering the secret every time.
        if (StringUtils.hasText(request.apiToken())) {
            setting.setApiTokenEncrypted(credentialCipher.encrypt(request.apiToken().trim()));
        }

        return toResponse(settingRepository.save(setting));
    }

    @Override
    @Transactional
    public BakongSettingResponse activate() {
        BusinessPaymentSetting setting = findMySetting();
        setting.setIsActive(true);
        return toResponse(settingRepository.save(setting));
    }

    @Override
    @Transactional
    public BakongSettingResponse deactivate() {
        BusinessPaymentSetting setting = findMySetting();
        setting.setIsActive(false);
        return toResponse(settingRepository.save(setting));
    }

    @Override
    @Transactional(readOnly = true)
    public KhqrResponse preview(KhqrPreviewRequest request) {
        BusinessPaymentSetting setting = findMySetting();

        String currency = StringUtils.hasText(request.currency())
                ? request.currency().trim().toUpperCase()
                : "USD";

        String billNumber = StringUtils.hasText(request.billNumber())
                ? request.billNumber().trim()
                : "PREVIEW-" + System.currentTimeMillis();
        
        Instant expiresAt = Instant.now().plusSeconds(QR_VALIDITY_MINUTES * 60L);

        KhqrGenerator.Result result = khqrGenerator.generate(
                setting,
                request.amount(),
                currency,
                billNumber,
                request.terminalLabel(),
                expiresAt
        );

        return new KhqrResponse(
                result.qr(),
                result.md5(),
                request.amount(),
                currency,
                billNumber,
                LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())
        );
    }

    private BakongSettingResponse toResponse(BusinessPaymentSetting setting) {
        return new BakongSettingResponse(
                setting.getId(),
                setting.getBusiness().getId(),
                setting.getAccountType(),
                setting.getBakongAccountId(),
                setting.getMerchantName(),
                setting.getMerchantCity(),
                setting.getMerchantId(),
                setting.getAcquiringBank(),
                setting.getMobileNumber(),
                setting.getStoreLabel(),
                StringUtils.hasText(setting.getApiTokenEncrypted()),
                Boolean.TRUE.equals(setting.getIsActive())
        );
    }

    private BusinessPaymentSetting findMySetting() {
        return settingRepository.findByBusiness_Id(findMyBusiness().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bakong settings have not been configured"));
    }

    private Business findMyBusiness() {
        return businessRepository.findByKeycloakUserId(UUID.fromString(SecurityUtils.extractUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
