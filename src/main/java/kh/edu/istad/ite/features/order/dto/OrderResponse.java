package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private UUID businessId;
    private UUID customerId;
    private String invoiceNumber;
    private OrderChannel channel;
    private OrderStatus status;
    /** How the sale that closed this order was paid. Null while still PENDING. */
    private PaymentMethodType paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private TaxInclusionType taxInclusionType;
    private BigDecimal total;
    private String currency;
    /** The second currency this order was shown in, frozen at creation. */
    private String displayCurrency;
    private BigDecimal displayExchangeRate;
    private String note;
    /** True only for a Pay Later web order still waiting on the owner to approve it. */
    private boolean awaitingPayLaterApproval;
    private List<OrderItemResponse> items;
    private LocalDateTime createdDate;
}
