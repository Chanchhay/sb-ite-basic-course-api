package kh.edu.istad.ite.features.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_profiles")
public class UserProfile extends BasedAuditingEntity {
    @Id
    private UUID userId; // From Keycloak
    private String gender;
    private String address;
    private String profilePicture;
    private String phoneNumber;
}
