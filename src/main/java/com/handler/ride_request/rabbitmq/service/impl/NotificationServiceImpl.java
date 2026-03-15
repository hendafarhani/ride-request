package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.mapper.RideMapper;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.rabbitmq.service.QueueChecker;
import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import com.handler.ride_request.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final RabbitTemplate rabbitTemplate;
    private final DirectExchange userExchange;
    private final QueueChecker queueChecker;
    private final RabbitMQUserService rabbitMQUserService;
    private static final String QUEUE_USER = "queue.user.";

    @Override
    public void sendRabbitMqNotification(List<Rider> riders, RideRequestEntity rideRequestEntity) {
        if(isRidersListEmpty(riders)) {
            log.info("Riders list to be notified is empty.");
            return;
        }

        riders.forEach(rider ->{
            String queueName = getQueueName(rider.getIdentifier());
            ensureQueue(queueName, rider.getIdentifier());
            sendNotification(rider.getIdentifier(), RideMapper.mapToRideNotification(rider, rideRequestEntity, StatusEnum.PENDING));
        });
    }

    @Override
    public void notifyRideAccepted(RideRequestEntity rideRequestEntity, String acceptedRiderIdentifier) {
        if (Objects.isNull(rideRequestEntity)) {
            log.warn("Cannot notify acceptance because rideRequestEntity is null");
            return;
        }

        notifyRequester(rideRequestEntity, acceptedRiderIdentifier);
        notifyOtherRiders(rideRequestEntity, acceptedRiderIdentifier);
    }

    private void notifyRequester(RideRequestEntity rideRequestEntity, String acceptedRiderIdentifier) {
        String requesterIdentifier = rideRequestEntity.getUser().getIdentifier();
        ensureQueue(getQueueName(requesterIdentifier), requesterIdentifier);
        sendNotification(requesterIdentifier,
                RideMapper.mapToRideNotification(acceptedRiderIdentifier, rideRequestEntity, StatusEnum.ACCEPTED));
        log.info("Notified requester {} that ride {} was accepted by {}",
                requesterIdentifier, rideRequestEntity.getIdentifier(), acceptedRiderIdentifier);
    }

    private void notifyOtherRiders(RideRequestEntity rideRequestEntity, String acceptedRiderIdentifier) {
        Set<String> candidates = Objects.requireNonNullElse(rideRequestEntity.getCandidateRiderIdentifiers(), Set.of());
        List<String> ridersToNotify = candidates.stream()
                .filter(candidate -> !Objects.equals(candidate, acceptedRiderIdentifier))
                .collect(Collectors.toList());

        if (ridersToNotify.isEmpty()) {
            log.info("No additional riders to notify for ride {}", rideRequestEntity.getIdentifier());
            return;
        }

        ridersToNotify.forEach(riderId -> {
            ensureQueue(getQueueName(riderId), riderId);
            sendNotification(riderId,
                    RideMapper.mapToRideNotification(acceptedRiderIdentifier, rideRequestEntity, StatusEnum.CANCELED));
        });
        log.info("Notified {} riders that ride {} was accepted", ridersToNotify.size(), rideRequestEntity.getIdentifier());
    }

    private void sendNotification(String userId, RideNotification rideNotification) {
        rabbitTemplate.convertAndSend(userExchange.getName(), userId, rideNotification);
    }

    private boolean isRidersListEmpty(List<Rider> riders){
        return Objects.isNull(riders) || riders.isEmpty();
    }

    private String getQueueName(String identifier){
        return QUEUE_USER.concat(identifier);
    }

    private void ensureQueue(String queueName, String identifier) {
        if (!queueChecker.doesQueueExist(queueName)) {
            rabbitMQUserService.createUserQueue(identifier);
        }
    }
}
