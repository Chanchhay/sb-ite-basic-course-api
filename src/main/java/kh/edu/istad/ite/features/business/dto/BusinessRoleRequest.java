package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.ArrayList;

public record BusinessRoleRequest(
        @NotBlank(message = "Role name cannot be empty")
        String name,
        
        List<String> permissions
) {
    public BusinessRoleRequest {
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
    }
}
