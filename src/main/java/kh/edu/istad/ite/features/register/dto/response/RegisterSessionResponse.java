package kh.edu.istad.ite.features.register.dto.response;

import kh.edu.istad.ite.shared.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RegisterSessionResponse {
    private Long id;
    private Long registerId;
    private String registerName;
    private String userId;
    private String cashierName;
    private java.util.List<String> cashierNames;
    private UUID businessId;
    private Integer orderCount;
    private Instant openedAt;
    private Instant closedAt;
    /** The currency this session was counted in. */
    private String currency;
    private BigDecimal openingBalance;
    private BigDecimal baseOpeningBalance;
    private String secondaryCurrency;
    private BigDecimal secondaryOpeningBalance;
    private BigDecimal secondaryExchangeRate;
    private BigDecimal totalCashSales;
    private BigDecimal totalPaidIn;
    private BigDecimal totalPaidOut;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal baseActualAmount;
    private BigDecimal secondaryActualAmount;
    private BigDecimal differenceAmount;
    private String reconciliationStatus; // MATCHED, OVER, SHORT
    private SessionStatus status;
    private String note;
}
