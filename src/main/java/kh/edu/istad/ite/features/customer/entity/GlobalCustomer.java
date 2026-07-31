package kh.edu.istad.ite.features.customer.entity;

import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "global_customers")
public class GlobalCustomer extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", unique = true)
    private UUID keycloakUserId;


    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "phone_number", unique = true, length = 30)
    private String phoneNumber;
}