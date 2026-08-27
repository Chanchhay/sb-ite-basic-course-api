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
@Table(name = "business_telegram_bots")
public class BusinessTelegramBot extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false, unique = true)
    private Business business;

    // Never stored/returned in plaintext - encrypted via CredentialCipher
    @Column(name = "bot_token_encrypted", nullable = false, columnDefinition = "text")
    private String botTokenEncrypted;

    // Fetched from Telegram's getMe - not user-entered, prevents owners typing a fake/mismatched value
    @Column(name = "telegram_bot_id")
    private Long telegramBotId;

    @Column(name = "bot_username", length = 150)
    private String botUsername;

    // Random per-business routing key + Telegram "secret_token" header double-check for the webhook
    @Column(name = "webhook_secret", nullable = false, length = 100, unique = true)
    private String webhookSecret;

    @Column(name = "welcome_message", columnDefinition = "text")
    private String welcomeMessage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "notification_chat_id", length = 100)
    private String notificationChatId;

    /** Whether the bot's menu button opens the Mini App (a real web UI) instead of Telegram's default commands list. */
    @Column(name = "is_mini_app_enabled", nullable = false)
    private Boolean isMiniAppEnabled = false;
}
