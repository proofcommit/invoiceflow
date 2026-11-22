package com.proofcommit.invoiceflow.invoice;

import com.proofcommit.invoiceflow.invoice.InvoiceRequests.CreateInvoice;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceResponse;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceSummary;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.PaymentResponse;
import com.proofcommit.invoiceflow.outbox.OutboxService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final OutboxService outbox;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, OutboxService outbox) {
        this.invoices = invoices;
        this.payments = payments;
        this.outbox = outbox;
    }

    @Transactional
    public InvoiceResponse create(CreateInvoice request) {
        Invoice invoice = Invoice.draft(
            request.customerId(), request.customerEmail(), request.currency(), request.dueDate());
        request.lines().forEach(line -> invoice.addLine(line.description(), line.quantity(), line.unitPrice()));
        return InvoiceResponse.from(invoices.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID invoiceId) {
        return InvoiceResponse.from(invoices.findDetailedById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId)));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummary> list(Pageable pageable) {
        return invoices.findAll(pageable).map(InvoiceSummary::from);
    }

    @Transactional
    public InvoiceResponse issue(UUID invoiceId) {
        Invoice invoice = invoices.findForUpdate(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        invoice.issue();
        outbox.append("invoice.issued", invoice.getId(), Map.of(
            "invoiceNumber", invoice.getInvoiceNumber(),
            "customerId", invoice.getCustomerId(),
            "customerEmail", invoice.getCustomerEmail(),
            "currency", invoice.getCurrency(),
            "total", invoice.getTotal(),
            "dueDate", invoice.getDueDate()));
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public PaymentResponse recordPayment(UUID invoiceId, String paymentReference, java.math.BigDecimal amount) {
        Invoice invoice = invoices.findForUpdate(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        boolean newPayment = !payments.existsByInvoiceIdAndPaymentReference(invoiceId, paymentReference);
        Payment payment = invoice.recordPayment(paymentReference, amount);
        if (newPayment) {
            outbox.append("invoice.payment.recorded", invoice.getId(), Map.of(
                "paymentId", payment.getId(),
                "paymentReference", payment.getPaymentReference(),
                "invoiceNumber", invoice.getInvoiceNumber(),
                "customerEmail", invoice.getCustomerEmail(),
                "currency", invoice.getCurrency(),
                "amount", payment.getAmount(),
                "invoiceStatus", invoice.getStatus(),
                "outstanding", invoice.outstanding()));
        }
        return PaymentResponse.from(payment, invoice);
    }
}
