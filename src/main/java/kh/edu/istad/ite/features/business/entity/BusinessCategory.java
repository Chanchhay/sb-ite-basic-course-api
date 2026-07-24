package kh.edu.istad.ite.features.business.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "business_categories")
public class BusinessCategory extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_business_categories_parent"))
    private BusinessCategory parent;

    @Column(nullable = false)
    private Short level = 1;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(length = 255)
    private String icon;
}
