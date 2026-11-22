package com.proofcommit.invoiceflow.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {
    @Test
    void calculatesTotalsAndTransitionsThroughPaymentStates() {
        Invoice invoice = invoice();

        invoice.issue();
        invoice.recordPayment("payment-1", new BigDecimal("40.00"));
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(new BigDecimal("110.00"), invoice.outstanding());

        invoice.recordPayment("payment-2", new BigDecimal("110.00"));
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(new BigDecimal("0.00"), invoice.outstanding());
    }

    @Test
    void repeatedPaymentReferenceHasNoAdditionalEffect() {
        Invoice invoice = invoice();
        invoice.issue();

        Payment first = invoice.recordPayment("payment-1", new BigDecimal("150.00"));
        Payment repeated = invoice.recordPayment("payment-1", new BigDecimal("150.00"));

        assertEquals(first.getId(), repeated.getId());
        assertEquals(new BigDecimal("150.00"), invoice.getPaidAmount());
        assertThrows(InvoiceRuleException.class,
            () -> invoice.recordPayment("payment-1", new BigDecimal("50.00")));
    }

    @Test
    void rejectsOverpaymentAndPaymentsBeforeIssue() {
        Invoice invoice = invoice();
        assertThrows(InvoiceRuleException.class,
            () -> invoice.recordPayment("payment-1", new BigDecimal("150.00")));

        invoice.issue();
        assertThrows(InvoiceRuleException.class,
            () -> invoice.recordPayment("payment-2", new BigDecimal("150.01")));
    }

    private static Invoice invoice() {
        Invoice invoice = Invoice.draft(UUID.randomUUID(), "usd", LocalDate.now().plusDays(30));
        invoice.addLine("Assessment", 2, new BigDecimal("50.00"));
        invoice.addLine("Verification", 1, new BigDecimal("50.00"));
        assertEquals(new BigDecimal("150.00"), invoice.getTotal());
        return invoice;
    }
}
