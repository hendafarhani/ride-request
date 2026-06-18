package com.handler.ride_request.rabbitmq.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.mapper.RideMapper;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.rabbitmq.service.QueueChecker;
import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.service.EventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final RabbitTemplate rabbitTemplate;
    @Qualifier("userExchange")
    private final DirectExchange userExchange;
    private final QueueChecker queueChecker;
    private final RabbitMQUserService rabbitMQUserService;
    private final RideRequestDriverOfferRepository offerRepository;
    private final EventOutboxService eventOutboxService;
    private static final String QUEUE_USER = "queue.user.";

    @Override
    public void sendRabbitMqNotification(List<Rider> riders, RideRequestEntity rideRequestEntity) {
        if (hasNoRidersToNotify(riders)) {
            log.info("Riders list to be notified is empty.");
            return;
        }

        notifyRidersAboutPendingRequest(riders, rideRequestEntity);
    }

    @Override
    public void notifyRideAccepted(RideRequestEntity rideRequestEntity, String acceptedRiderIdentifier) {
        if (isRideRequestMissing(rideRequestEntity, "acceptance")) {
            return;
        }

        notifyParticipantsAboutAcceptedRide(rideRequestEntity, acceptedRiderIdentifier);
    }

    @Override
    public void notifyRideTimedOut(RideRequestEntity rideRequestEntity) {
        if (isRideRequestMissing(rideRequestEntity, "timeout")) {
            return;
        }

        notifyRequesterAboutTimedOutRide(rideRequestEntity);
    }

    private boolean isRideRequestMissing(RideRequestEntity rideRequestEntity, String notificationType) {
        if (Objects.isNull(rideRequestEntity)) {
            log.warn("Cannot notify {} because rideRequestEntity is null", notificationType);
            return true;
        }
        return false;
    }

    private void notifyRidersAboutPendingRequest(List<Rider> riders, RideRequestEntity rideRequestEntity) {
        riders.forEach(rider -> notifyRiderAboutPendingRequest(rider, rideRequestEntity));
    }

    private void notifyParticipantsAboutAcceptedRide(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier
    ) {
        notifyRequesterAboutAcceptedRide(rideRequestEntity, acceptedRiderIdentifier);
        notifyOtherRidersAboutAcceptedRide(rideRequestEntity, acceptedRiderIdentifier);
    }

    private void notifyRiderAboutPendingRequest(Rider rider, RideRequestEntity rideRequestEntity) {
        sendNotificationToUser(
                rider.getIdentifier(),
                RideMapper.mapToRideNotification(rider, rideRequestEntity, StatusEnum.PENDING));
    }

    private void notifyRequesterAboutAcceptedRide(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier) {
        String requesterIdentifier = requesterIdentifier(rideRequestEntity);
        sendNotificationToUser(
                requesterIdentifier,
                RideMapper.mapToRideNotification(acceptedRiderIdentifier, rideRequestEntity, StatusEnum.ACCEPTED));
        log.info("Notified requester {} that ride {} was accepted by {}",
                requesterIdentifier, rideRequestEntity.getIdentifier(), acceptedRiderIdentifier);
    }

    private void notifyRequesterAboutTimedOutRide(RideRequestEntity rideRequestEntity) {
        String requesterIdentifier = requesterIdentifier(rideRequestEntity);
        sendNotificationToUser(
                requesterIdentifier,
                RideMapper.mapToRideNotification((String) null, rideRequestEntity, StatusEnum.TIMED_OUT));
        log.info("Notified requester {} that ride {} timed out",
                requesterIdentifier, rideRequestEntity.getIdentifier());
    }

    private void notifyOtherRidersAboutAcceptedRide(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier) {
        List<String> riderIdentifiersToNotify = findRiderIdentifiersToNotifyAboutAcceptedRide(
                rideRequestEntity,
                acceptedRiderIdentifier);

        if (riderIdentifiersToNotify.isEmpty()) {
            log.info("No additional riders to notify for ride {}", rideRequestEntity.getIdentifier());
            return;
        }

        riderIdentifiersToNotify.forEach(riderIdentifier ->
                notifyRiderAboutAcceptedRide(rideRequestEntity, acceptedRiderIdentifier, riderIdentifier));
        log.info("Notified {} riders that ride {} was accepted",
                riderIdentifiersToNotify.size(), rideRequestEntity.getIdentifier());
    }

    private List<String> findRiderIdentifiersToNotifyAboutAcceptedRide(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier
    ) {
        return findCanceledRiderIdentifiersForRide(rideRequestEntity)
                .stream()
                .filter(candidate -> !Objects.equals(candidate, acceptedRiderIdentifier))
                .distinct()
                .toList();
    }

    private List<String> findCanceledRiderIdentifiersForRide(RideRequestEntity rideRequestEntity) {
        return offerRepository.findByRideRequestIdAndStatus(rideRequestEntity.getId(), OfferStatus.CANCELED)
                .stream()
                .map(offer -> offer.getRider().getIdentifier())
                .toList();
    }

    private void notifyRiderAboutAcceptedRide(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier,
            String riderIdentifier
    ) {
        recordRideCanceledForRider(rideRequestEntity, riderIdentifier);
        sendAcceptedRideCancellationNotification(rideRequestEntity, acceptedRiderIdentifier, riderIdentifier);
    }

    private void recordRideCanceledForRider(RideRequestEntity rideRequestEntity, String riderIdentifier) {
        eventOutboxService.recordRiderEvent(RideRequestEventType.RIDER_CANCELED, rideRequestEntity, riderIdentifier);
    }

    private void sendAcceptedRideCancellationNotification(
            RideRequestEntity rideRequestEntity,
            String acceptedRiderIdentifier,
            String riderIdentifier
    ) {
        sendNotificationToUser(
                riderIdentifier,
                RideMapper.mapToRideNotification(acceptedRiderIdentifier, rideRequestEntity, StatusEnum.CANCELED));
    }

    private void sendNotificationToUser(String userIdentifier, RideNotification rideNotification) {
        ensureUserQueueExistsElseCreateIt(userIdentifier);
        publishNotification(userIdentifier, rideNotification);
    }

    private boolean hasNoRidersToNotify(List<Rider> riders) {
        return Objects.isNull(riders) || riders.isEmpty();
    }

    private void ensureUserQueueExistsElseCreateIt(String userIdentifier) {
        if (!queueChecker.doesQueueExist(queueName(userIdentifier))) {
            rabbitMQUserService.createUserQueue(userIdentifier);
        }
    }

    private String queueName(String userIdentifier) {
        return QUEUE_USER.concat(userIdentifier);
    }

    private void publishNotification(String userIdentifier, RideNotification rideNotification) {
        rabbitTemplate.convertAndSend(userExchange.getName(), userIdentifier, rideNotification);
    }

    private String requesterIdentifier(RideRequestEntity rideRequestEntity) {
        return rideRequestEntity.getUser().getIdentifier();
    }
}
