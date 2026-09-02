package kh.edu.istad.ite.features.user.dto;

import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.Builder;
import java.util.List;
import java.util.UUID;

@Builder
public record StaffResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String gender,
        RecordStatus status,
        List<String> roleIds
) {}
