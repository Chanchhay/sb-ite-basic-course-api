package kh.edu.istad.ite.features.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.KhqrAccountType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "business_payment_settings")
public class BusinessPaymentSetting extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false, unique = true)
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private KhqrAccountType accountType;
    @Column(name = "bakong_account_id", nullable = false, length = 32)
    private String bakongAccountId;

    @Column(name = "merchant_name", nullable = false, length = 25)
    private String merchantName;

    @Column(name = "merchant_city", nullable = false, length = 15)
    private String merchantCity;

    @Column(name = "merchant_id", length = 32)
    private String merchantId;

    @Column(name = "acquiring_bank", length = 32)
    private String acquiringBank;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "store_label", length = 25)
    private String storeLabel;

    @Column(name = "api_token_encrypted", columnDefinition = "text")
    private String apiTokenEncrypted;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;
}
