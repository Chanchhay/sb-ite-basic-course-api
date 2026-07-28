package kh.edu.istad.ite.features.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.ArrayList;

public record PlatformRoleRequest(
        @NotBlank(message = "Role name cannot be empty")
        String name,
        
        List<String> permissions
) {
    public PlatformRoleRequest {
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
    }
}
