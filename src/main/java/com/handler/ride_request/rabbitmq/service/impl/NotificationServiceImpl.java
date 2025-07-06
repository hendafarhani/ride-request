package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.mapper.RideMapper;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import com.handler.ride_request.tools.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final RabbitTemplate rabbitTemplate;
    private final DirectExchange userExchange;
    private final QueueCheckerImpl queueChecker;
    private final RabbitMQUserService rabbitMQUserService;
    private static final String QUEUE_USER = "queue.user.";

    public void sendRabbitMqNotification(List<Rider> riders, RideRequestEntity rideRequestEntity) {
        if(isRidersListEmpty(riders)) {
            log.info("Riders list to be notified is empty.");
            return;
        }

        riders.forEach(rider ->{
            String queueName = getQueueName(rider);

            if (!queueChecker.doesQueueExist(queueName)) {
                rabbitMQUserService.createUserQueue(queueName);
            }

            sendNotification(rider.getIdentifier(), RideMapper.mapToRideNotification(rider, rideRequestEntity, StatusEnum.PENDING));
        });
    }

    private void sendNotification(String userId, RideNotification rideNotification) {
        rabbitTemplate.convertAndSend(userExchange.getName(), userId, rideNotification);
    }

    private boolean isRidersListEmpty(List<Rider> riders){
        return Objects.isNull(riders) || riders.isEmpty();
    }

    private String getQueueName(Rider rider){
        return QUEUE_USER.concat(rider.getIdentifier());
    }
}
