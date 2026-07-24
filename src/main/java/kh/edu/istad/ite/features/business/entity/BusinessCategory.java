package kh.edu.istad.ite.features.business.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "business_categories")
public class BusinessCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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