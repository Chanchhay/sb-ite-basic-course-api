package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private UUID id;
    private UUID orderId;
    private String invoiceNumber;
    private UUID cashierId;
    private OrderChannel channel;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private TaxInclusionType taxInclusionType;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal changeAmount;
    private BigDecimal totalCost;
    private String currency;
    /** The second currency this sale was shown in, frozen at sale time. */
    private String displayCurrency;
    private BigDecimal displayExchangeRate;
    private PaymentMethodType paymentMethod;
    private Integer itemCount;
    private String note;
    private LocalDateTime soldAt;
}
