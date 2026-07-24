package kh.edu.istad.ite.features.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitUpsertRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @Size(max = 255)
        String note
) {
}
