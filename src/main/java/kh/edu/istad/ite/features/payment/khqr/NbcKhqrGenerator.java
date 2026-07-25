package kh.edu.istad.ite.features.payment.khqr;

import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.shared.enums.KhqrAccountType;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class NbcKhqrGenerator implements KhqrGenerator {

    private static final String CURRENCY_KHR = "KHR";

    @Override
    public Result generate(
            BusinessPaymentSetting setting,
            BigDecimal amount,
            String currency,
            String billNumber,
            String terminalLabel,
            Instant expiresAt
    ) {
        KHQRCurrency khqrCurrency = CURRENCY_KHR.equalsIgnoreCase(currency)
                ? KHQRCurrency.KHR
                : KHQRCurrency.USD;

        double normalizedAmount = KHQRCurrency.KHR.equals(khqrCurrency)
                ? amount.setScale(0, java.math.RoundingMode.HALF_UP).doubleValue()
                : amount.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();

        long expirationMillis = expiresAt.toEpochMilli();

        KHQRResponse<KHQRData> response = KhqrAccountType.MERCHANT.equals(setting.getAccountType())
                ? BakongKHQR.generateMerchant(
                        buildMerchantInfo(setting, khqrCurrency, normalizedAmount, billNumber, terminalLabel, expirationMillis))
                : BakongKHQR.generateIndividual(
                        buildIndividualInfo(setting, khqrCurrency, normalizedAmount, billNumber, terminalLabel, expirationMillis));

        if (response == null || response.getKHQRStatus() == null || response.getKHQRStatus().getCode() != 0) {
            String message = response == null || response.getKHQRStatus() == null
                    ? "unknown error"
                    : response.getKHQRStatus().getMessage();

            log.error("KHQR generation failed for business {}: {}", setting.getBusiness().getId(), message);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to generate KHQR: " + message);
        }

        return new Result(response.getData().getQr(), response.getData().getMd5());
    }

    private IndividualInfo buildIndividualInfo(
            BusinessPaymentSetting setting,
            KHQRCurrency currency,
            double amount,
            String billNumber,
            String terminalLabel,
            long expirationMillis
    ) {
        IndividualInfo info = new IndividualInfo();
        info.setBakongAccountId(setting.getBakongAccountId());
        info.setMerchantName(setting.getMerchantName());
        info.setMerchantCity(setting.getMerchantCity());
        info.setCurrency(currency);
        info.setAmount(amount);
        info.setExpirationTimestamp(expirationMillis);
        applyOptional(billNumber, terminalLabel, setting, info::setBillNumber, info::setTerminalLabel,
                info::setMobileNumber, info::setStoreLabel);
        return info;
    }

    private MerchantInfo buildMerchantInfo(
            BusinessPaymentSetting setting,
            KHQRCurrency currency,
            double amount,
            String billNumber,
            String terminalLabel,
            long expirationMillis
    ) {
        MerchantInfo info = new MerchantInfo();
        info.setBakongAccountId(setting.getBakongAccountId());
        info.setMerchantId(setting.getMerchantId());
        info.setAcquiringBank(setting.getAcquiringBank());
        info.setMerchantName(setting.getMerchantName());
        info.setMerchantCity(setting.getMerchantCity());
        info.setCurrency(currency);
        info.setAmount(amount);
        info.setExpirationTimestamp(expirationMillis);
        applyOptional(billNumber, terminalLabel, setting, info::setBillNumber, info::setTerminalLabel,
                info::setMobileNumber, info::setStoreLabel);
        return info;
    }

    private void applyOptional(
            String billNumber,
            String terminalLabel,
            BusinessPaymentSetting setting,
            java.util.function.Consumer<String> billNumberSetter,
            java.util.function.Consumer<String> terminalLabelSetter,
            java.util.function.Consumer<String> mobileNumberSetter,
            java.util.function.Consumer<String> storeLabelSetter
    ) {
        if (StringUtils.hasText(billNumber)) {
            billNumberSetter.accept(billNumber);
        }
        if (StringUtils.hasText(terminalLabel)) {
            terminalLabelSetter.accept(terminalLabel);
        }
        if (StringUtils.hasText(setting.getMobileNumber())) {
            mobileNumberSetter.accept(setting.getMobileNumber());
        }
        if (StringUtils.hasText(setting.getStoreLabel())) {
            storeLabelSetter.accept(setting.getStoreLabel());
        }
    }
}
