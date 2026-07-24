package kh.edu.istad.ite.features.customer.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

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
public class GlobalCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
