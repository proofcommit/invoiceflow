package com.proofcommit.invoiceflow.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final long confirmTimeoutMs;

    public OutboxPublisher(
        RabbitTemplate rabbitTemplate,
        @Value("${invoiceflow.outbox.exchange}") String exchange,
        @Value("${invoiceflow.outbox.confirm-timeout-ms}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public void publish(OutboxEvent event) {
        try {
            Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(event.getId().toString())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateId", event.getAggregateId().toString())
                .build();
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbitTemplate.send(exchange, event.getEventType(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected outbox event: " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while confirming an outbox event", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish outbox event " + event.getId(), exception);
        }
    }
}
