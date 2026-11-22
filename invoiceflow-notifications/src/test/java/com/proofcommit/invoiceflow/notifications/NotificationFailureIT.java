package com.proofcommit.invoiceflow.notifications;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "spring.mail.port=1",
    "invoiceflow.outbox.exchange=test.invoice.events.failure",
    "invoiceflow.notifications.queue=test.invoice.notifications.failure",
    "invoiceflow.notifications.retry.initial-interval-ms=10",
    "invoiceflow.notifications.retry.max-attempts=3"
})
class NotificationFailureIT extends NotificationIntegrationTestSupport {
    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    NotificationDeliveryRepository deliveries;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void failingSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> 1);
    }

    @BeforeEach
    void cleanDeliveries() {
        deliveries.deleteAll();
    }

    @Test
    void recordsFailureAfterRetryingSmtpDelivery() {
        InvoiceEvent event = new InvoiceEvent(UUID.randomUUID(), "invoice.issued", UUID.randomUUID(), Instant.now(),
            objectMapper.valueToTree(Map.of(
                "invoiceNumber", "INV-2026-FAIL",
                "customerEmail", "customer@example.com",
                "currency", "USD",
                "total", new BigDecimal("100.00"),
                "dueDate", "2099-12-31")));

        rabbitTemplate.convertAndSend("test.invoice.events.failure", event.eventType(), event);

        await().atMost(15, SECONDS).until(() -> deliveries.findById(event.eventId())
            .map(NotificationDelivery::getStatus)
            .filter(DeliveryStatus.FAILED::equals)
            .isPresent());
        NotificationDelivery delivery = deliveries.findById(event.eventId()).orElseThrow();
        assertEquals(3, delivery.getAttempts());
        assertNotNull(delivery.getLastError());
    }
}
