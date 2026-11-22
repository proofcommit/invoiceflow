package com.proofcommit.invoiceflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, OutboxPublisher publisher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void append(String eventType, UUID aggregateId, Object payload) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", Instant.now());
        envelope.put("payload", payload);
        try {
            repository.save(OutboxEvent.pending(
                eventId, eventType, aggregateId, objectMapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize outbox event", exception);
        }
    }

    @Scheduled(fixedDelayString = "${invoiceflow.outbox.relay-delay-ms}")
    public void publishPending() {
        for (OutboxEvent event : repository.findTop50ByPublishedAtIsNullOrderByCreatedAt()) {
            try {
                publisher.publish(event);
                event.markPublished();
            } catch (RuntimeException exception) {
                event.markFailed(exception.getMessage() == null ? "Publication failed" : exception.getMessage());
                repository.save(event);
                return;
            }
            repository.save(event);
        }
    }
}
