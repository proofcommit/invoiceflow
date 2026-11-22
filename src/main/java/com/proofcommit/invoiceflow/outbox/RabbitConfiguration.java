package com.proofcommit.invoiceflow.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    TopicExchange invoiceEventsExchange(@Value("${invoiceflow.outbox.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }
}
