package kh.edu.istad.ite.features.business.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "keycloak_user_id",
            nullable = false,
            unique = true,
            length = 100
    )
    private String keycloakUserId;

    @Column(nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            foreignKey = @ForeignKey(
                    name = "fk_businesses_business_category"
            )
    )
    private BusinessCategory businessCategory;
}