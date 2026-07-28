package kh.edu.istad.ite.features.business.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record BusinessRoleResponse(
        String id,
        String name,
        List<String> permissions
) {}
