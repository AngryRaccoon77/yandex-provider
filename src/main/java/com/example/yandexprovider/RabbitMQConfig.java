package com.example.yandexprovider;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    private static final String QUEUE_NAME = "deviceStatusQueue1";
    private static final String EXCHANGE_NAME = "deviceStatusExchange";

    @Bean
    public Queue deviceStatusQueue2() {
        return new Queue(QUEUE_NAME, false);
    }

    @Bean
    Exchange deviceStatusExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    Binding binding2(Queue deviceStatusQueue2, Exchange deviceStatusExchange) {
        return BindingBuilder.bind(deviceStatusQueue2).to(deviceStatusExchange).with("device.status").noargs();
    }
}