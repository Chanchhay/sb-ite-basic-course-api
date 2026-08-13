package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateAddOnRequest(
        @NotBlank(message = "add-on name cannot be empty")
        @Size(max = 150, message = "add-on name must be at most 150 characters")
        String name,

        UUID baseUnitId,

        @DecimalMin(value = "0.0", inclusive = false, message = "usePerOrder must be greater than zero")
        @Digits(integer = 9, fraction = 3, message = "usePerOrder must have at most 9 integer digits and 3 decimal places")
        BigDecimal usePerOrder,

        /** What one selection costs, anywhere it is offered. */
        @DecimalMin(value = "0.0", message = "price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 10 integer digits and 2 decimal places")
        BigDecimal price,

        List<@Valid AddOnUomConversionRequest> uomConversions,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note
) {
    public CreateAddOnRequest {
        if (usePerOrder == null) {
            usePerOrder = BigDecimal.ONE;
        }
    }
}
