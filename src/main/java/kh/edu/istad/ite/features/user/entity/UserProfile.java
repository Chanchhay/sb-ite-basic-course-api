package kh.edu.istad.ite.features.user.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_profiles")
public class UserProfile extends BasedAuditingEntity {

    @Id
    private UUID userId;

    private String gender;
    private String address;
    private String profilePicture;
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_status", length = 20)
    private RecordStatus staffStatus;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
