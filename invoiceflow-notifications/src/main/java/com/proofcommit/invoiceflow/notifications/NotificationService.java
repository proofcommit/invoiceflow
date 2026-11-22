package com.proofcommit.invoiceflow.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationDeliveryRepository deliveries;
    private final JavaMailSender mailSender;
    private final String from;
    private final RetryTemplate retryTemplate;

    public NotificationService(
        NotificationDeliveryRepository deliveries,
        JavaMailSender mailSender,
        @Value("${invoiceflow.notifications.from}") String from,
        @Value("${invoiceflow.notifications.retry.max-attempts}") int maxAttempts,
        @Value("${invoiceflow.notifications.retry.initial-interval-ms}") long initialIntervalMs) {
        this.deliveries = deliveries;
        this.mailSender = mailSender;
        this.from = from;
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(5000);
        this.retryTemplate = new RetryTemplate();
        this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));
        this.retryTemplate.setBackOffPolicy(backOff);
    }

    @Transactional
    public void deliver(InvoiceEvent event) {
        if (deliveries.existsById(event.eventId())) {
            return;
        }
        NotificationDelivery delivery = deliveries.saveAndFlush(NotificationDelivery.pending(event, subject(event)));
        retryTemplate.execute(context -> {
            delivery.recordAttempt();
            send(event, delivery);
            delivery.markSent();
            return null;
        }, context -> {
            Throwable failure = context.getLastThrowable();
            delivery.markFailed(failure == null ? null : failure.getMessage());
            return null;
        });
        deliveries.save(delivery);
    }

    private void send(InvoiceEvent event, NotificationDelivery delivery) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(delivery.getRecipient());
        message.setSubject(subject(event));
        message.setText(body(event));
        mailSender.send(message);
    }

    private String subject(InvoiceEvent event) {
        String invoiceNumber = event.payload().path("invoiceNumber").asText();
        return switch (event.eventType()) {
            case "invoice.issued" -> "Invoice " + invoiceNumber + " issued";
            case "invoice.payment.recorded" -> "Payment received for invoice " + invoiceNumber;
            default -> throw new IllegalArgumentException("Unsupported invoice event: " + event.eventType());
        };
    }

    private String body(InvoiceEvent event) {
        return switch (event.eventType()) {
            case "invoice.issued" -> """
                Your invoice %s is now issued.
                Total: %s %s
                Due date: %s
                """.formatted(
                event.payload().path("invoiceNumber").asText(),
                event.payload().path("currency").asText(),
                event.payload().path("total").asText(),
                event.payload().path("dueDate").asText());
            case "invoice.payment.recorded" -> """
                I recorded your payment for invoice %s.
                Amount: %s %s
                Outstanding balance: %s %s
                """.formatted(
                event.payload().path("invoiceNumber").asText(),
                event.payload().path("currency").asText(),
                event.payload().path("amount").asText(),
                event.payload().path("currency").asText(),
                event.payload().path("outstanding").asText());
            default -> throw new IllegalArgumentException("Unsupported invoice event: " + event.eventType());
        };
    }
}
