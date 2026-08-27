package kh.edu.istad.ite.features.business.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "businesses")
public class Business extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private UUID keycloakUserId;

    @Column(nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'ACTIVE'"
    )
    private BusinessOwnerStatus status = BusinessOwnerStatus.ACTIVE;

    @Column(name = "provisioned_at", nullable = false)
    private LocalDateTime provisionedAt;

    @Column(length = 255)
    private String logo;

    @Column(length = 255)
    private String thumbnail;

    @Column(length = 255)
    private String about;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "google_map", length = 255)
    private String googleMap;

    @Column(nullable = false, length = 255)
    private String address;

    /** @deprecated superseded by {@link #provinceName}; kept until every active business has one set. */
    @Deprecated
    @Column(name = "city_or_province", length = 255)
    private String cityOrProvince;

    /**
     * Province/city, district/khan, commune/sangkat — read from a geocoder's
     * address components when the owner drops the map pin, not typed by hand.
     * Plain text rather than a foreign key into a hand-seeded division table:
     * nobody here maintains Cambodia's ~1,600 communes, and a geocoder already
     * normalizes spelling/casing far better than free entry ever did.
     */
    @Column(name = "province_name", length = 150)
    private String provinceName;

    @Column(name = "district_name", length = 150)
    private String districtName;

    @Column(name = "commune_name", length = 150)
    private String communeName;

    /** The shopfront's exact map pin, same source as the names above. */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 255)
    private String website;

    @Column(name = "business_email", nullable = false, length = 255)
    private String businessEmail;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "is_listing", nullable = false)
    private Boolean isListing;

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed;

    @Column(name = "open_time", length = 30)
    private String openTime;

    @Column(name = "close_time", length = 30)
    private String closeTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_businesses_business_category"
            )
    )
    private BusinessCategory businessCategory;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code ASC")
    private List<BusinessCurrency> currencies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "jsonb")
    private List<Map<String, String>> socialLinks = new ArrayList<>();

    @Column(
            name = "base_currency",
            nullable = false,
            length = 10,
            columnDefinition = "varchar(10) default 'USD'"
    )
    private String baseCurrency = "USD";

    @Column(
            name = "display_currency",
            length = 10,
            columnDefinition = "varchar(10) default 'USD'"
    )
    private String displayCurrency = "USD";

    /**
     * The one tax rate this business charges, applied the same way on every
     * channel — POS, web storefront, Telegram, Messenger — rather than each
     * one carrying its own copy.
     */
    // Not NOT NULL: ddl-auto: update cannot add a NOT NULL column to a table
    // that already has rows (see SchemaScriptRunner's note on orders.tax_amount).
    // Every read already treats null the same as false.
    @Column(name = "tax_enabled")
    private Boolean taxEnabled = false;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "tax_inclusion_type",
            length = 20,
            columnDefinition = "varchar(20) default 'EXCLUSIVE'"
    )
    private TaxInclusionType taxInclusionType = TaxInclusionType.EXCLUSIVE;

    /** What to call it on a receipt — "VAT", "GST", "Sales Tax". */
    @Column(name = "tax_label", length = 30)
    private String taxLabel;
}
