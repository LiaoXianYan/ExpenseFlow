package com.expenseflow.ai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "expense.exchange";
    public static final String REVIEW_QUEUE = "ai.review.queue";
    public static final String REVIEW_KEY = "expense.report.submitted";
    public static final String NOTIFY_QUEUE = "notification.event.queue";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange expenseExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE).build();
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue()).to(expenseExchange()).with(REVIEW_KEY);
    }
}
