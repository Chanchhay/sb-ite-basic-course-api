package kh.edu.istad.ite.features.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "membership_types",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_membership_types_business_type_name",
                        columnNames = {"business_owner_id", "type_name"}
                )
        }
)
public class MembershipType extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "business_owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_types_business")
    )
    private Business business;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(columnDefinition = "text")
    private String remark;

    // Column is named "discount_type" to match the ER diagram, but it is
    // actually a foreign key to the discount granted to members of this tier.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "discount_type",
            foreignKey = @ForeignKey(name = "fk_membership_types_discount")
    )
    private Discount discount;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'ACTIVE'"
    )
    private RecordStatus status = RecordStatus.ACTIVE;
}
