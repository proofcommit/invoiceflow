package com.proofcommit.invoiceflow.invoice;

import com.proofcommit.invoiceflow.invoice.InvoiceRequests.CreateInvoice;
import com.proofcommit.invoiceflow.invoice.InvoiceRequests.RecordPayment;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceResponse;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceSummary;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.PaymentResponse;
import java.util.UUID;
import javax.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoice request) {
        return invoices.create(request);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID invoiceId) {
        return invoices.get(invoiceId);
    }

    @GetMapping
    public Page<InvoiceSummary> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return invoices.list(pageable);
    }

    @PostMapping("/{invoiceId}/issue")
    public InvoiceResponse issue(@PathVariable UUID invoiceId) {
        return invoices.issue(invoiceId);
    }

    @PostMapping("/{invoiceId}/payments")
    public PaymentResponse recordPayment(@PathVariable UUID invoiceId, @Valid @RequestBody RecordPayment request) {
        return invoices.recordPayment(invoiceId, request.paymentReference(), request.amount());
    }
}
