package com.proofcommit.invoiceflow.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceResponses {
    private InvoiceResponses() {
    }

    public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        String currency,
        LocalDate dueDate,
        InvoiceStatus status,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        Instant createdAt,
        List<InvoiceLineResponse> lines) {

        public static InvoiceResponse from(Invoice invoice) {
            return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getCustomerId(), invoice.getCurrency(),
                invoice.getDueDate(), invoice.getStatus(), invoice.getTotal(), invoice.getPaidAmount(),
                invoice.outstanding(), invoice.getCreatedAt(),
                invoice.getLines().stream().map(InvoiceLineResponse::from).toList());
        }
    }

    public record InvoiceLineResponse(
        String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
        static InvoiceLineResponse from(InvoiceLine line) {
            return new InvoiceLineResponse(line.getDescription(), line.getQuantity(), line.getUnitPrice(), line.getAmount());
        }
    }

    public record InvoiceSummary(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        InvoiceStatus status,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal outstanding) {

        public static InvoiceSummary from(Invoice invoice) {
            return new InvoiceSummary(invoice.getId(), invoice.getInvoiceNumber(), invoice.getCustomerId(),
                invoice.getStatus(), invoice.getTotal(), invoice.getPaidAmount(), invoice.outstanding());
        }
    }

    public record PaymentResponse(
        UUID id,
        String paymentReference,
        BigDecimal amount,
        Instant receivedAt,
        InvoiceStatus invoiceStatus,
        BigDecimal outstanding) {

        public static PaymentResponse from(Payment payment, Invoice invoice) {
            return new PaymentResponse(payment.getId(), payment.getPaymentReference(), payment.getAmount(),
                payment.getReceivedAt(), invoice.getStatus(), invoice.outstanding());
        }
    }
}
