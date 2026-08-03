package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DescriptionColumnRequest(
        @Size(max = 20, message = "column blocks must have at most 20 items")
        List<@Valid DescriptionBlockRequest> blocks
) {
}
