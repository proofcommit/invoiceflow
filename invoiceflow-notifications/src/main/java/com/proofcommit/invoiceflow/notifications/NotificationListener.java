package com.proofcommit.invoiceflow.notifications;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private final NotificationService notifications;

    public NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = "${invoiceflow.notifications.queue}")
    public void onInvoiceEvent(InvoiceEvent event) {
        notifications.deliver(event);
    }
}
