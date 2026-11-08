package com.finance.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "financial.exchange";
    public static final String QUEUE_NAME = "financial.transaction.reconcile";
    public static final String ROUTING_KEY = "financial.transaction.reconcile";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue reconciliationQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public Binding reconciliationBinding(Queue reconciliationQueue, DirectExchange exchange) {
        return BindingBuilder.bind(reconciliationQueue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}