package com.proofcommit.invoiceflow.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record InvoiceEvent(
    UUID eventId,
    String eventType,
    UUID aggregateId,
    Instant occurredAt,
    JsonNode payload) {
}
