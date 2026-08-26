package kh.edu.istad.ite.features.customer.dto;

public record CustomerSelfProfileResponse(
        String fullName,
        String email,
        String gender,
        String phoneNumber,
        String address,
        boolean profileComplete
) {
}
