package com.proofcommit.invoiceflow.notifications;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "invoiceflow.outbox.exchange=test.invoice.events.success",
    "invoiceflow.notifications.queue=test.invoice.notifications.success"
})
class NotificationConsumerIT extends NotificationIntegrationTestSupport {
    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    NotificationDeliveryRepository deliveries;

    @Autowired
    ObjectMapper objectMapper;

    @SpyBean
    NotificationService notifications;

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @BeforeEach
    void cleanDeliveries() {
        deliveries.deleteAll();
    }

    @Test
    void sendsInvoiceEmailsAndSuppressesDuplicateEvents() throws Exception {
        InvoiceEvent issued = issuedEvent();
        InvoiceEvent payment = paymentEvent();

        rabbitTemplate.convertAndSend("test.invoice.events.success", issued.eventType(), issued);
        await().atMost(15, SECONDS).until(() -> deliveries.findById(issued.eventId())
            .map(NotificationDelivery::getStatus)
            .filter(DeliveryStatus.SENT::equals)
            .isPresent());

        rabbitTemplate.convertAndSend("test.invoice.events.success", payment.eventType(), payment);
        await().atMost(15, SECONDS).until(() -> deliveries.findById(payment.eventId())
            .map(NotificationDelivery::getStatus)
            .filter(DeliveryStatus.SENT::equals)
            .isPresent());

        rabbitTemplate.convertAndSend("test.invoice.events.success", issued.eventType(), issued);
        verify(notifications, timeout(5000).times(2))
            .deliver(argThat(event -> event.eventId().equals(issued.eventId())));
        verify(notifications, times(1))
            .deliver(argThat(event -> event.eventId().equals(payment.eventId())));

        assertEquals(2, deliveries.count());
        assertEquals(1, deliveries.findById(issued.eventId()).orElseThrow().getAttempts());
        assertEquals(1, deliveries.findById(payment.eventId()).orElseThrow().getAttempts());

        JsonNode messages = mailpitMessages();
        assertEquals(2, messages.path("total").asInt());
        assertNotNull(messages.path("messages").get(0));
    }

    private InvoiceEvent issuedEvent() {
        return new InvoiceEvent(UUID.randomUUID(), "invoice.issued", UUID.randomUUID(), Instant.now(),
            objectMapper.valueToTree(Map.of(
                "invoiceNumber", "INV-2026-TEST",
                "customerEmail", "customer@example.com",
                "currency", "USD",
                "total", new BigDecimal("100.00"),
                "dueDate", "2099-12-31")));
    }

    private InvoiceEvent paymentEvent() {
        return new InvoiceEvent(UUID.randomUUID(), "invoice.payment.recorded", UUID.randomUUID(), Instant.now(),
            objectMapper.valueToTree(Map.of(
                "invoiceNumber", "INV-2026-TEST",
                "customerEmail", "customer@example.com",
                "currency", "USD",
                "amount", new BigDecimal("100.00"),
                "outstanding", new BigDecimal("0.00"))));
    }

    private JsonNode mailpitMessages() throws Exception {
        URL url = new URL("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages");
        try (InputStream response = url.openStream()) {
            return objectMapper.readTree(response);
        }
    }
}
