package com.expenseflow.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "expense.exchange";
    public static final String NOTIFY_QUEUE = "notification.event.queue";
    public static final String RESULT_KEY = "expense.result.notified";
    public static final String REVIEW_KEY = "ai.review.completed";

    @Bean
    public TopicExchange expenseExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(RESULT_KEY);
    }

    @Bean
    public Binding reviewCompletedBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(REVIEW_KEY);
    }
}
