package com.proofcommit.invoiceflow.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    protected Invoice() {
    }

    private Invoice(UUID customerId, String currency, LocalDate dueDate) {
        this.id = UUID.randomUUID();
        this.invoiceNumber = "INV-" + Year.now(ZoneOffset.UTC) + "-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        this.customerId = customerId;
        this.currency = currency.toUpperCase(Locale.ROOT);
        this.dueDate = dueDate;
        this.status = InvoiceStatus.DRAFT;
        this.total = money(BigDecimal.ZERO);
        this.paidAmount = money(BigDecimal.ZERO);
        this.createdAt = Instant.now();
    }

    public static Invoice draft(UUID customerId, String currency, LocalDate dueDate) {
        return new Invoice(customerId, currency, dueDate);
    }

    public void addLine(String description, int quantity, BigDecimal unitPrice) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvoiceRuleException("Lines can only be added to a draft invoice");
        }
        InvoiceLine line = InvoiceLine.of(this, description, quantity, unitPrice);
        lines.add(line);
        total = money(total.add(line.getAmount()));
    }

    public void issue() {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvoiceRuleException("Only a draft invoice can be issued");
        }
        if (lines.isEmpty() || total.signum() <= 0) {
            throw new InvoiceRuleException("An issued invoice needs a positive total");
        }
        status = InvoiceStatus.ISSUED;
    }

    public Payment recordPayment(String paymentReference, BigDecimal amount) {
        BigDecimal normalizedAmount = money(amount);
        Optional<Payment> existing = payments.stream()
            .filter(payment -> payment.getPaymentReference().equals(paymentReference))
            .findFirst();
        if (existing.isPresent()) {
            if (existing.get().getAmount().compareTo(normalizedAmount) != 0) {
                throw new InvoiceRuleException("Payment reference was already used with a different amount");
            }
            return existing.get();
        }
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PARTIALLY_PAID) {
            throw new InvoiceRuleException("Payments can only be recorded against an unpaid issued invoice");
        }
        if (normalizedAmount.signum() <= 0) {
            throw new InvoiceRuleException("Payment amount must be positive");
        }
        if (paidAmount.add(normalizedAmount).compareTo(total) > 0) {
            throw new InvoiceRuleException("Payment would exceed the invoice balance");
        }
        Payment payment = Payment.of(this, paymentReference, normalizedAmount);
        payments.add(payment);
        paidAmount = money(paidAmount.add(normalizedAmount));
        status = outstanding().signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
        return payment;
    }

    public BigDecimal outstanding() {
        return money(total.subtract(paidAmount));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InvoiceLine> getLines() {
        return List.copyOf(lines);
    }
}
