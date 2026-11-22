package com.proofcommit.invoiceflow.invoice;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(UUID invoiceId) {
        super("Invoice " + invoiceId + " was not found");
    }
}
