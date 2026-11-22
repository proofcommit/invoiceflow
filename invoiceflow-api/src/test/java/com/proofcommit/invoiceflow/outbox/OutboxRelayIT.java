package com.proofcommit.invoiceflow.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofcommit.invoiceflow.IntegrationTestSupport;
import com.proofcommit.invoiceflow.invoice.InvoiceRequests.CreateInvoice;
import com.proofcommit.invoiceflow.invoice.InvoiceRequests.InvoiceLineRequest;
import com.proofcommit.invoiceflow.invoice.InvoiceResponses.InvoiceResponse;
import com.proofcommit.invoiceflow.invoice.InvoiceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class OutboxRelayIT extends IntegrationTestSupport {
    @Autowired
    InvoiceService invoices;

    @Autowired
    OutboxService outboxService;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void declareCaptureQueue() {
        Queue queue = new Queue("test.invoice.events", true);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue)
            .to(new TopicExchange("invoice.events", true, false)).with("invoice.#"));
        rabbitAdmin.purgeQueue(queue.getName());
        outboxRepository.deleteAll();
    }

    @Test
    void publishesConfirmedInvoiceEventsWithEventEnvelopes() throws Exception {
        InvoiceResponse invoice = invoices.create(new CreateInvoice(
            UUID.randomUUID(), "customer@example.com", "USD", LocalDate.now().plusDays(30),
            List.of(new InvoiceLineRequest("Assessment", 1, new BigDecimal("100.00")))));

        invoices.issue(invoice.id());
        outboxService.publishPending();
        Message issued = rabbitTemplate.receive("test.invoice.events", 5000);

        assertNotNull(issued);
        JsonNode issuedBody = objectMapper.readTree(issued.getBody());
        assertEquals("invoice.issued", issuedBody.get("eventType").asText());
        assertEquals(invoice.id().toString(), issuedBody.get("aggregateId").asText());
        assertEquals("customer@example.com", issuedBody.at("/payload/customerEmail").asText());
        assertNotNull(issuedBody.get("eventId"));
        assertEquals(1, outboxRepository.countByPublishedAtIsNotNull());
    }
}
