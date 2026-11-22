package com.proofcommit.invoiceflow.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Future;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public final class InvoiceRequests {
    private InvoiceRequests() {
    }

    public record CreateInvoice(
        @NotNull UUID customerId,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @NotNull @Future LocalDate dueDate,
        @NotEmpty List<@Valid InvoiceLineRequest> lines) {
    }

    public record InvoiceLineRequest(
        @NotBlank @Size(max = 200) String description,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
    }

    public record RecordPayment(
        @NotBlank @Size(max = 100) String paymentReference,
        @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }
}
