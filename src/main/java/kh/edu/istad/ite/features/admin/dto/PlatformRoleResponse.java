package kh.edu.istad.ite.features.admin.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record PlatformRoleResponse(
        String id,
        String name,
        List<String> permissions
) {}
