package kh.edu.istad.ite.features.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One option the shopper picked — "Sugar Level" = "50".
 *
 * Sent by name and stored value rather than by id, because that is what an
 * item attribute is: it has no id of its own, and {@code value} is the identity
 * the catalogue itself treats as stable.
 */
public record CartSelectionRequest(
        @NotBlank @Size(max = 150) String attributeName,
        @NotBlank @Size(max = 150) String value
) {
}
