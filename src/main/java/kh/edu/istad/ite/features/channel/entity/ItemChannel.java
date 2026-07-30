package kh.edu.istad.ite.features.channel.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.catalog.entity.Item;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "item_channels",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_item_channel",
                        columnNames = {
                                "item_id",
                                "sales_channel_id"
                        }
                )
        }
)
public class ItemChannel extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "item_id",
            nullable = false
    )
    private Item item;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "sales_channel_id",
            nullable = false
    )
    private SalesChannel salesChannel;


    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

}