package kh.edu.istad.ite.features.catalog.dto;

import java.util.List;

public record DescriptionColumnResponse(
        List<DescriptionBlockResponse> blocks
) {
}
