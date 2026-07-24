package kh.edu.istad.ite.features.auth.dto;

import lombok.Builder;

@Builder
public record RegisterResponse(
String id,
String username,
String email,
String firstName,
String lastName,
String phoneNumber,
String gender,
String role
) {
}
