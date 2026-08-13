package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Which add-ons an item offers, and nothing else about the item.
 *
 * The whole list every time rather than "add this one" / "drop that one": a
 * toggle sends what the item should end up offering, so two people toggling at
 * once cannot leave it half-applied.
 */
public record ItemAddOnsRequest(
        @NotNull(message = "addOnIds cannot be null")
        List<UUID> addOnIds
) {
}
