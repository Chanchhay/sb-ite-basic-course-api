package kh.edu.istad.ite.features.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.ChannelType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.features.business.entity.Business;

// Links a raw chat-platform identity (e.g. a Telegram chat id) to a business-scoped
// Customer, so returning shoppers are auto-recognized on their next message.
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "customer_channel_identities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_channel_identities_business_channel_external_id",
                columnNames = {"business_owner_id", "channel", "external_id"}
        )
)
public class CustomerChannelIdentity extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    // Telegram chat id (as string) - same value BotSession.externalId uses
    @Column(name = "external_id", nullable = false, length = 150)
    private String externalId;

    @Column(name = "channel_username", length = 150)
    private String channelUsername;
}
