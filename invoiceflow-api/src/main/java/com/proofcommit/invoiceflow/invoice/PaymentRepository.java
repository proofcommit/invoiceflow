package com.proofcommit.invoiceflow.invoice;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    long countByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceIdAndPaymentReference(UUID invoiceId, String paymentReference);
}
