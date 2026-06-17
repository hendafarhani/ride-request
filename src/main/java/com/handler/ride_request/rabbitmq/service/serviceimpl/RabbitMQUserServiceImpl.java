package com.handler.ride_request.rabbitmq.service.serviceimpl;

import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RabbitMQUserServiceImpl implements RabbitMQUserService {

    private final AmqpAdmin amqpAdmin;

    @Qualifier("userExchange")
    private final DirectExchange userExchange;
    private static final String QUEUE_USER = "queue.user.";

    @Override
    public void createUserQueue(String userId) {
        Queue userQueue = declareUserQueue(userId);
        bindQueueToUserExchange(userQueue, userId);
    }

    private Queue declareUserQueue(String userId) {
        Queue userQueue = new Queue(queueName(userId), true);
        amqpAdmin.declareQueue(userQueue);
        return userQueue;
    }

    private void bindQueueToUserExchange(Queue userQueue, String userId) {
        Binding binding = BindingBuilder.bind(userQueue).to(userExchange).with(userId);
        amqpAdmin.declareBinding(binding);
    }

    private String queueName(String userId) {
        return QUEUE_USER + userId;
    }
}
