package kh.edu.istad.ite.features.catalog.dto;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String slug,
        String note
) {
}
