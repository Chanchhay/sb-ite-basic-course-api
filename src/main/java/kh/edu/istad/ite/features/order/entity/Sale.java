package kh.edu.istad.ite.features.order.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "sales",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_order",
                columnNames = {"business_owner_id", "order_id"}
        ),
        indexes = {
                @Index(name = "idx_sales_business_sold_at", columnList = "business_owner_id, sold_at"),
                @Index(name = "idx_sales_cashier", columnList = "business_owner_id, cashier_id"),
                @Index(name = "idx_sales_register_session", columnList = "register_session_id")
        }
)
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(name = "cashier_id")
    private UUID cashierId;

    /**
     * The drawer this sale's cash went into, or null if it took none.
     *
     * Stamped when the sale is rung up rather than worked out afterwards from
     * who sold it and when: a shift is shared, and cashier-and-time-window
     * attribution guesses wrong at every boundary — a sale rung up as one shift
     * hands over to the next lands in whichever drawer the clock says, not the
     * one the notes are actually in. Online orders take no cash at a till and
     * leave this null.
     */
    @Column(name = "register_session_id")
    private Long registerSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderChannel channel;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "tax_inclusion_type",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'EXCLUSIVE'"
    )
    private TaxInclusionType taxInclusionType = TaxInclusionType.EXCLUSIVE;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "change_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal changeAmount = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    /** The second currency shown at the time of sale, if the business had one. */
    @Column(name = "display_currency", length = 10)
    private String displayCurrency;

    /** Units of the display currency per one unit of {@link #currency}, frozen at sale time. */
    @Column(name = "display_exchange_rate", precision = 20, scale = 8)
    private BigDecimal displayExchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;
}
