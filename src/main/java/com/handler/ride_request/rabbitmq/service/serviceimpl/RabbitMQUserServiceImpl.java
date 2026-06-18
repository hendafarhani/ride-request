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
        Queue userQueue = createQueueDefinition(userId);
        registerQueue(userQueue);
        registerBinding(userQueue, userId);
    }

    private Queue createQueueDefinition(String userId) {
        return new Queue(queueName(userId), true);
    }

    private void registerQueue(Queue userQueue) {
        amqpAdmin.declareQueue(userQueue);
    }

    private void registerBinding(Queue userQueue, String userId) {
        amqpAdmin.declareBinding(createUserBinding(userQueue, userId));
    }

    private Binding createUserBinding(Queue userQueue, String userId) {
        return BindingBuilder.bind(userQueue).to(userExchange).with(userId);
    }

    private String queueName(String userId) {
        return QUEUE_USER + userId;
    }
}
