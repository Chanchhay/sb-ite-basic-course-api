package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BusinessRolePatchRequest(
        String name,
        
        @NotNull(message = "Permissions cannot be null")
        List<String> permissions
) {}
