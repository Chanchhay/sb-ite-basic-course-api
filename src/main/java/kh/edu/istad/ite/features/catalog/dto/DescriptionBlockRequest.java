package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.DescriptionBlockType;

import java.util.List;

public record DescriptionBlockRequest(
        @NotNull(message = "block type cannot be null")
        DescriptionBlockType type,

        @Size(max = 2000, message = "block text must be at most 2000 characters")
        String text,

        @Size(max = 20, message = "block items must have at most 20 items")
        List<@Size(max = 300, message = "each block item must be at most 300 characters") String> items,

        @Size(max = 2048, message = "block url must be at most 2048 characters")
        String url,

        @Size(max = 150, message = "block caption must be at most 150 characters")
        String caption,

        @Size(min = 2, max = 3, message = "columns array must have between 2 and 3 items")
        List<@Valid DescriptionColumnRequest> columns
) {
}
