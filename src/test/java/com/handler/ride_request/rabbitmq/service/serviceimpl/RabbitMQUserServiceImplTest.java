package com.handler.ride_request.rabbitmq.service.serviceimpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMQUserServiceImplTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private DirectExchange userExchange;

    private RabbitMQUserServiceImpl rabbitMQUserService;

    @BeforeEach
    void setUp() {
        rabbitMQUserService = new RabbitMQUserServiceImpl(amqpAdmin, userExchange);
        when(userExchange.getName()).thenReturn("user-exchange");
    }

    @Test
    void createUserQueueShouldDeclareDurableQueueAndBindingForUser() {
        rabbitMQUserService.createUserQueue("user-123");

        ArgumentCaptor<Queue> queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(1)).declareQueue(queueCaptor.capture());

        Queue declaredQueue = queueCaptor.getValue();
        assertEquals("queue.user.user-123", declaredQueue.getName());
        assertTrue(declaredQueue.isDurable());
        assertFalse(declaredQueue.isExclusive());
        assertFalse(declaredQueue.isAutoDelete());

        ArgumentCaptor<Binding> bindingCaptor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin, times(1)).declareBinding(bindingCaptor.capture());

        Binding declaredBinding = bindingCaptor.getValue();
        assertEquals("queue.user.user-123", declaredBinding.getDestination());
        assertEquals(Binding.DestinationType.QUEUE, declaredBinding.getDestinationType());
        assertEquals("user-exchange", declaredBinding.getExchange());
        assertEquals("user-123", declaredBinding.getRoutingKey());
    }

    @Test
    void createUserQueueShouldUseExactPrefixConcatenationForEmptyAndSpecialUserIds() {
        rabbitMQUserService.createUserQueue("");
        rabbitMQUserService.createUserQueue("user.A-1");

        ArgumentCaptor<Queue> queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(2)).declareQueue(queueCaptor.capture());

        assertEquals("queue.user.", queueCaptor.getAllValues().get(0).getName());
        assertEquals("queue.user.user.A-1", queueCaptor.getAllValues().get(1).getName());
    }

    @Test
    void createUserQueueShouldHandleNullUserIdUsingCurrentBehavior() {
        rabbitMQUserService.createUserQueue(null);

        ArgumentCaptor<Queue> queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(queueCaptor.capture());
        assertEquals("queue.user.null", queueCaptor.getValue().getName());

        ArgumentCaptor<Binding> bindingCaptor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(bindingCaptor.capture());
        assertEquals("queue.user.null", bindingCaptor.getValue().getDestination());
        assertEquals("user-exchange", bindingCaptor.getValue().getExchange());
        assertNull(bindingCaptor.getValue().getRoutingKey());
    }
}
