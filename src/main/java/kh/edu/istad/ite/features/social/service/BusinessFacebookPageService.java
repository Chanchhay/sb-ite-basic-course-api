package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.facebook.FacebookGraphClient;
import kh.edu.istad.ite.features.social.repository.BusinessFacebookPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessFacebookPageService {

    private final BusinessFacebookPageRepository repository;
    private final BusinessRepository businessRepository;
    private final FacebookGraphClient graphClient;
    private final StorefrontProps storefrontProps;

    @Transactional
    public BusinessFacebookPage registerPage(UUID businessId, String pageId, String pageName, String pageAccessToken) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        Optional<BusinessFacebookPage> existing = repository.findByBusinessId(businessId);

        BusinessFacebookPage page = existing.orElse(new BusinessFacebookPage());
        page.setBusiness(business);
        page.setPageId(pageId);
        page.setPageName(pageName);
        page.setPageAccessTokenEncrypted(pageAccessToken); // TODO: Encrypt using CredentialCipher later
        page.setIsActive(true);
        page.setWelcomeMessage("Welcome to " + business.getBusinessName() + " on Messenger!");

        BusinessFacebookPage saved = repository.save(page);

        applyMessengerProfile(saved);
        graphClient.subscribePageToWebhook(pageId, pageAccessToken);

        return saved;
    }

    public Optional<BusinessFacebookPage> findByPageId(String pageId) {
        return repository.findByPageId(pageId);
    }

    public Optional<BusinessFacebookPage> findByBusinessId(UUID businessId) {
        return repository.findByBusinessId(businessId);
    }

    @Transactional
    public void disconnectPage(UUID businessId) {
        repository.findByBusinessId(businessId).ifPresent(repository::delete);
    }

    /** The old conversational text/button bot flow — independent of Mini App. */
    @Transactional
    public BusinessFacebookPage setActive(UUID businessId, boolean active) {
        BusinessFacebookPage page = findMySetting(businessId);
        page.setIsActive(active);
        BusinessFacebookPage saved = repository.save(page);
        applyMessengerProfile(saved);
        return saved;
    }

    @Transactional
    public BusinessFacebookPage setMiniAppEnabled(UUID businessId, boolean enabled) {
        BusinessFacebookPage page = findMySetting(businessId);
        page.setIsMiniAppEnabled(enabled);
        BusinessFacebookPage saved = repository.save(page);
        applyMessengerProfile(saved);
        return saved;
    }

    private void applyMessengerProfile(BusinessFacebookPage page) {
        String miniAppUrl = storefrontProps.buildMessengerMiniAppUrl(page.getBusiness().getSlug());
        graphClient.setupMessengerProfile(
                page.getPageAccessTokenEncrypted(),
                miniAppUrl,
                Boolean.TRUE.equals(page.getIsActive()),
                Boolean.TRUE.equals(page.getIsMiniAppEnabled()));
    }

    private BusinessFacebookPage findMySetting(UUID businessId) {
        return repository.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Facebook Page has not been connected"));
    }
}
