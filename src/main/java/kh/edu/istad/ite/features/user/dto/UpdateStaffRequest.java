package kh.edu.istad.ite.features.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateStaffRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotBlank(message = "Gender is required")
        String gender,

        List<String> roleIds // optional
) {}
