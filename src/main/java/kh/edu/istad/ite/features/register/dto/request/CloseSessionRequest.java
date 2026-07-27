package kh.edu.istad.ite.features.register.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseSessionRequest {
    @NotNull(message = "Actual amount is required")
    @PositiveOrZero(message = "Actual amount must be zero or positive")
    private BigDecimal actualAmount;

    private String closingNote;
}
