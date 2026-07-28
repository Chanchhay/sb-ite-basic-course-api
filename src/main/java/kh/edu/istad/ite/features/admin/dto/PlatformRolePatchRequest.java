package kh.edu.istad.ite.features.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlatformRolePatchRequest(
        String name,
        
        @NotNull(message = "Permissions cannot be null")
        List<String> permissions
) {}
