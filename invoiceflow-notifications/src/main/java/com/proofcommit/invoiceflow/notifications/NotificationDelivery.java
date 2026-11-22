package com.proofcommit.invoiceflow.notifications;

import java.time.Instant;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {
    @Id
    private UUID eventId;

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NotificationDelivery() {
    }

    private NotificationDelivery(InvoiceEvent event, String subject) {
        this.eventId = event.eventId();
        this.invoiceId = event.aggregateId();
        this.eventType = event.eventType();
        this.recipient = event.payload().path("customerEmail").asText();
        this.subject = subject;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static NotificationDelivery pending(InvoiceEvent event, String subject) {
        return new NotificationDelivery(event, subject);
    }

    public void recordAttempt() {
        attempts++;
        updatedAt = Instant.now();
    }

    public void markSent() {
        status = DeliveryStatus.SENT;
        lastError = null;
        updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        status = DeliveryStatus.FAILED;
        lastError = error == null ? "SMTP delivery failed" : error.length() <= 500 ? error : error.substring(0, 500);
        updatedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getRecipient() {
        return recipient;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
