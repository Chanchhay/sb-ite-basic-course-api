package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Whether an item currently sells one of the add-ons it offers. */
public record ItemAddOnAvailabilityRequest(
        @NotNull(message = "available cannot be null")
        Boolean available
) {
}
