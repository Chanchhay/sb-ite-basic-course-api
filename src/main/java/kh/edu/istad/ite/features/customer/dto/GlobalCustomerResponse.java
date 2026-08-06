package kh.edu.istad.ite.features.customer.dto;

import java.util.UUID;

public record GlobalCustomerResponse(
        UUID id,
        UUID keycloakUserId,
        String email,
        String fullName,
        String phoneNumber
) {
}
