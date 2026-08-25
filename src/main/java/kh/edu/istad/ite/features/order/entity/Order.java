package kh.edu.istad.ite.features.order.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_business_invoice",
                        columnNames = {"business_owner_id", "invoice_number"}
                ),
                @UniqueConstraint(
                        name = "uk_orders_invoice_number",
                        columnNames = {"invoice_number"}
                )
        }
)
public class Order extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "invoice_number", nullable = false, length = 60)
    private String invoiceNumber;

    @Column(name = "cashier_id")
    private UUID cashierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderChannel channel = OrderChannel.POS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_id")
    private UUID discountId;

    @Column(name = "discount_code", length = 100)
    private String discountCode;

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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    /** The second currency shown at the time of sale, if the business had one. */
    @Column(name = "display_currency", length = 10)
    private String displayCurrency;

    /** Units of the display currency per one unit of {@link #currency}, frozen at sale time. */
    @Column(name = "display_exchange_rate", precision = 20, scale = 8)
    private BigDecimal displayExchangeRate;

    @Column(columnDefinition = "text")
    private String note;

    /**
     * A storefront order placed with Pay Later sits here as PENDING and
     * untouched — stock only leaves the shelf once the business owner
     * approves it from the dashboard, unlike every other channel where
     * confirming/paying and consuming stock happen in the same step.
     */
    @Column(name = "awaiting_pay_later_approval", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean awaitingPayLaterApproval = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        item.setBusiness(this.business);
        this.items.add(item);
    }
}
