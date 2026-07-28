package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.admin.dto.response.BusinessChannelResponse;
import kh.edu.istad.ite.features.admin.repository.PlatformFeatureFlagRepository;
import kh.edu.istad.ite.features.admin.service.AdminChannelService;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminChannelServiceImpl implements AdminChannelService {

    private final BusinessRepository businessRepository;
    private final BusinessTelegramBotRepository telegramBotRepository;
    private final BusinessPaymentSettingRepository paymentSettingRepository;
    private final PlatformFeatureFlagRepository platformFeatureFlagRepository;
    private final StorefrontProps storefrontProps;

    @Override
    @Transactional(readOnly = true)
    public List<BusinessChannelResponse> findAllChannels() {
        Map<UUID, BusinessTelegramBot> botsByBusiness = telegramBotRepository.findAll().stream()
                .collect(Collectors.toMap(bot -> bot.getBusiness().getId(), Function.identity(), (a, b) -> a));

        Map<UUID, BusinessPaymentSetting> settingsByBusiness = paymentSettingRepository.findAll().stream()
                .collect(Collectors.toMap(
                        setting -> setting.getBusiness().getId(), Function.identity(), (a, b) -> a));

        boolean storefrontOffPlatformWide = isDisabledPlatformWide(BusinessFeature.STOREFRONT);
        boolean telegramOffPlatformWide = isDisabledPlatformWide(BusinessFeature.TELEGRAM_BOT);
        boolean bakongOffPlatformWide = isDisabledPlatformWide(BusinessFeature.KHQR_PAYMENT);

        return businessRepository.findAll().stream()
                .map(business -> toResponse(
                        business,
                        botsByBusiness.get(business.getId()),
                        settingsByBusiness.get(business.getId()),
                        storefrontOffPlatformWide,
                        telegramOffPlatformWide,
                        bakongOffPlatformWide))
                .toList();
    }

    private BusinessChannelResponse toResponse(
            Business business,
            BusinessTelegramBot bot,
            BusinessPaymentSetting setting,
            boolean storefrontOffPlatformWide,
            boolean telegramOffPlatformWide,
            boolean bakongOffPlatformWide
    ) {
        boolean published = Boolean.TRUE.equals(business.getIsListing())
                && !Boolean.TRUE.equals(business.getIsClosed())
                && !storefrontOffPlatformWide;

        boolean telegramActive = bot != null
                && Boolean.TRUE.equals(bot.getIsActive())
                && !telegramOffPlatformWide;

        boolean bakongActive = setting != null
                && Boolean.TRUE.equals(setting.getIsActive())
                && !bakongOffPlatformWide;

        return new BusinessChannelResponse(
                business.getId(),
                business.getDisplayName(),
                business.getSlug(),
                published,
                published ? buildStorefrontUrl(business.getSlug()) : null,
                business.getWebsite(),
                bot != null,
                bot == null ? null : bot.getBotUsername(),
                bot == null ? null : bot.getTelegramBotId(),
                telegramActive,
                setting != null,
                bakongActive,
                business.getProvisionedAt()
        );
    }

    private boolean isDisabledPlatformWide(BusinessFeature feature) {
        return platformFeatureFlagRepository.existsByFeatureAndEnabledFalse(feature);
    }

    private String buildStorefrontUrl(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }

        if (storefrontProps.isSubdomainEnabled()) {
            return storefrontProps.getProtocol() + "://" + slug + "." + storefrontProps.getBaseDomain();
        }

        return storefrontProps.getProtocol() + "://" + storefrontProps.getBaseDomain()
                + storefrontProps.getPathPrefix() + "/" + slug;
    }
}
