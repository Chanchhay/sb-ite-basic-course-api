package kh.edu.istad.ite.features.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "business_facebook_pages")
public class BusinessFacebookPage extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false, unique = true)
    private Business business;

    @Column(name = "page_id", nullable = false, unique = true, length = 100)
    private String pageId;

    @Column(name = "page_name", length = 255)
    private String pageName;

    // Never stored/returned in plaintext - encrypted via CredentialCipher
    @Column(name = "page_access_token_encrypted", nullable = false, columnDefinition = "text")
    private String pageAccessTokenEncrypted;

    @Column(name = "welcome_message", columnDefinition = "text")
    private String welcomeMessage;

    // The old conversational text/button flow (catalog browsing, cart, bot-driven checkout).
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    // The Mini App webview ("🛍 Open Shop" button) — independent of isActive,
    // same relationship as BusinessTelegramBot's isActive/isMiniAppEnabled pair.
    @Column(name = "is_mini_app_enabled", nullable = false)
    private Boolean isMiniAppEnabled = false;
}
