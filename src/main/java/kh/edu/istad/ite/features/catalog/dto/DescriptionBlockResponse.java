package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.DescriptionBlockType;

import java.util.List;

public record DescriptionBlockResponse(
        DescriptionBlockType type,
        String text,
        List<String> items,
        String url,
        String caption,
        List<DescriptionColumnResponse> columns
) {
}
