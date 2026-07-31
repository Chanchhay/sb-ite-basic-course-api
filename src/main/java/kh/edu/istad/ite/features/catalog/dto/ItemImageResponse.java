package kh.edu.istad.ite.features.catalog.dto;

import java.util.UUID;

public record ItemImageResponse(
        UUID id,
        String url,
        Integer position
) {
}
