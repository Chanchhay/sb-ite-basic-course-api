package kh.edu.istad.ite.features.customer.dto;

public record CustomerSelfProfileResponse(
        String fullName,
        String phoneNumber,
        String address,
        boolean profileComplete
) {
}
