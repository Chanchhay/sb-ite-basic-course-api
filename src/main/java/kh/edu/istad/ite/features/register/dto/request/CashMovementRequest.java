package kh.edu.istad.ite.features.register.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kh.edu.istad.ite.shared.enums.CashMovementType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashMovementRequest {
    @NotNull(message = "Movement type is required")
    private CashMovementType type;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String reason;
}
