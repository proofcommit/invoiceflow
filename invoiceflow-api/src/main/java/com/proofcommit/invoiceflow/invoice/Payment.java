package com.proofcommit.invoiceflow.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payment_invoice_reference", columnNames = {"invoice_id", "payment_reference"})
})
public class Payment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "payment_reference", nullable = false)
    private String paymentReference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant receivedAt;

    protected Payment() {
    }

    private Payment(Invoice invoice, String paymentReference, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.paymentReference = paymentReference;
        this.amount = amount;
        this.receivedAt = Instant.now();
    }

    static Payment of(Invoice invoice, String paymentReference, BigDecimal amount) {
        return new Payment(invoice, paymentReference, amount);
    }

    public UUID getId() {
        return id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
