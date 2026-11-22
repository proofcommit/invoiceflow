package com.proofcommit.invoiceflow.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.proofcommit.invoiceflow.IntegrationTestSupport;
import com.proofcommit.invoiceflow.invoice.InvoiceRequests.CreateInvoice;
import com.proofcommit.invoiceflow.invoice.InvoiceRequests.InvoiceLineRequest;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceResponse;
import com.proofcommit.invoiceflow.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentConcurrencyIT extends IntegrationTestSupport {
    @Autowired
    InvoiceService invoiceService;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OutboxRepository outbox;

    @Test
    void concurrentPaymentsCannotExceedTheInvoiceBalance() throws Exception {
        InvoiceResponse invoice = invoiceService.create(new CreateInvoice(
            UUID.randomUUID(), "USD", LocalDate.now().plusDays(30),
            List.of(new InvoiceLineRequest("Assessment", 1, new BigDecimal("100.00")))));
        invoiceService.issue(invoice.id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> pay(invoice.id(), "first", ready, start));
            Future<Object> second = executor.submit(() -> pay(invoice.id(), "second", ready, start));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            List<Object> results = List.of(first.get(), second.get());

            assertEquals(1, results.stream().filter(result -> result instanceof InvoiceResponses.PaymentResponse).count());
            assertEquals(1, results.stream().filter(result -> result instanceof InvoiceRuleException).count());
            assertEquals(new BigDecimal("75.00"), invoiceService.get(invoice.id()).paidAmount());
            assertEquals(1, payments.countByInvoiceId(invoice.id()));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object pay(UUID invoiceId, String reference, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return invoiceService.recordPayment(invoiceId, reference, new BigDecimal("75.00"));
        } catch (InvoiceRuleException exception) {
            return exception;
        }
    }
}
