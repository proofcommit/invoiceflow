package com.proofcommit.invoiceflow.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected InvoiceLine() {
    }

    private InvoiceLine(Invoice invoice, String description, int quantity, BigDecimal unitPrice) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = money(unitPrice);
        this.amount = money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    static InvoiceLine of(Invoice invoice, String description, int quantity, BigDecimal unitPrice) {
        return new InvoiceLine(invoice, description, quantity, unitPrice);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
