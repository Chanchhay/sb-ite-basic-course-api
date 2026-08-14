package kh.edu.istad.ite.features.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.PriceOverrideKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How one business runs one sales channel: its blanket price rule and when it
 * takes orders.
 *
 * Per business rather than on the channel itself, because a channel is a shared
 * idea — every shop has a counter and a website — while "we charge 10% more for
 * delivery" and "we close at nine" belong to the shop saying them.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "business_channel_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_business_channel_settings",
                columnNames = {"business_id", "sales_channel_id"}
        )
)
public class BusinessChannelSettings extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    /** The rule every line on this channel starts from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "override_kind", nullable = false, length = 20)
    private PriceOverrideKind overrideKind = PriceOverrideKind.INHERIT;

    @Column(name = "override_value", precision = 12, scale = 4)
    private BigDecimal overrideValue;

    /**
     * When the channel takes orders, as the weekly grid the shop filled in.
     *
     * Held as JSON because it is one thing the shop edits and saves whole, and
     * nothing else ever queries a single window of it. Null means nobody has
     * set hours, which is read as always open — a shop that has not answered
     * the question has not said it is closed.
     */
    @Column(name = "schedule_json", columnDefinition = "text")
    private String scheduleJson;
}
