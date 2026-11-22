package com.proofcommit.invoiceflow.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    TopicExchange invoiceEventsExchange(@Value("${invoiceflow.outbox.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue notificationQueue(@Value("${invoiceflow.notifications.queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding invoiceIssuedBinding(Queue notificationQueue, TopicExchange invoiceEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(invoiceEventsExchange).with("invoice.issued");
    }

    @Bean
    Binding paymentRecordedBinding(Queue notificationQueue, TopicExchange invoiceEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(invoiceEventsExchange).with("invoice.payment.recorded");
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
