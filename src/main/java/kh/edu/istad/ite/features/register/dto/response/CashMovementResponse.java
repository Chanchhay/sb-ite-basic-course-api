package kh.edu.istad.ite.features.register.dto.response;

import kh.edu.istad.ite.shared.enums.CashMovementType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashMovementResponse {
    private Long id;
    private Long sessionId;
    private CashMovementType type;
    /** Inherited from the session — cash movements are counted in its currency. */
    private String currency;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime createdAt;
}
