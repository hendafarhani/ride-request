package com.handler.ride_request.rabbitmq.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.domain.Rider;
import com.handler.ride_request.rabbitmq.mapper.RideMapper;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.rabbitmq.service.QueueChecker;
import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.service.EventOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private DirectExchange userExchange;

    @Mock
    private QueueChecker queueChecker;

    @Mock
    private RabbitMQUserService rabbitMQUserService;

    @Mock
    private RideRequestDriverOfferRepository offerRepository;

    @Mock
    private EventOutboxService eventOutboxService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private RideRequestEntity rideRequest;

    @BeforeEach
    void setUp() {
        rideRequest = RideRequestEntity.builder()
                .identifier("ride-123")
                .user(UserEntity.builder().identifier("requester-1").build())
                .build();
    }

    @Test
    void sendRabbitMqNotification_returnsWhenRiderListIsNull() {
        // Guard clause should also skip work for an empty list.
        notificationService.sendRabbitMqNotification(null, rideRequest);

        verifyNoInteractions(queueChecker, rabbitMQUserService, rabbitTemplate);
    }

    @Test
    void sendRabbitMqNotification_returnsWhenRiderListIsEmpty() {
        // Guard clause should also skip work for an empty list.
        notificationService.sendRabbitMqNotification(List.of(), rideRequest);

        verifyNoInteractions(queueChecker, rabbitMQUserService, rabbitTemplate);
    }

    @Test
    void sendRabbitMqNotification_createsQueueBeforePublishingWhenMissing() {
        stubUserExchange();
        Rider rider = Rider.builder().identifier("missing-queue").build();
        when(queueChecker.doesQueueExist("queue.user.missing-queue")).thenReturn(false);

        notificationService.sendRabbitMqNotification(List.of(rider), rideRequest);

        RideNotification expected = RideMapper.mapToRideNotification(rider, rideRequest, StatusEnum.PENDING);
        verify(rabbitMQUserService).createUserQueue("missing-queue");
        verify(rabbitTemplate).convertAndSend("user-exchange", "missing-queue", expected);
    }

    @Test
    void sendRabbitMqNotification_skipsQueueCreationWhenAlreadyProvisioned() {
        stubUserExchange();
        Rider rider = Rider.builder().identifier("existing-queue").build();
        when(queueChecker.doesQueueExist("queue.user.existing-queue")).thenReturn(true);

        notificationService.sendRabbitMqNotification(List.of(rider), rideRequest);

        RideNotification expected = RideMapper.mapToRideNotification(rider, rideRequest, StatusEnum.PENDING);
        verify(rabbitMQUserService, never()).createUserQueue(any());
        verify(rabbitTemplate).convertAndSend("user-exchange", "existing-queue", expected);
    }

    @Test
    void notifyRideAccepted_returnsImmediatelyWhenRideRequestIsNull() {
        notificationService.notifyRideAccepted(null, "accepted-rider");

        verifyNoInteractions(queueChecker, rabbitMQUserService, rabbitTemplate, offerRepository);
    }

    @Test
    void notifyRideAccepted_notifiesRequesterWhenRideIsAccepted() {
        stubUserExchange();
        when(queueChecker.doesQueueExist("queue.user.requester-1")).thenReturn(false);

        notificationService.notifyRideAccepted(rideRequest, "accepted-rider");

        RideNotification expected = RideMapper.mapToRideNotification("accepted-rider", rideRequest, StatusEnum.ACCEPTED);
        verify(queueChecker).doesQueueExist("queue.user.requester-1");
        verify(rabbitMQUserService).createUserQueue("requester-1");
        verify(rabbitTemplate).convertAndSend("user-exchange", "requester-1", expected);
    }

    @Test
    void notifyRideTimedOut_returnsImmediatelyWhenRideRequestIsNull() {
        notificationService.notifyRideTimedOut(null);

        verifyNoInteractions(queueChecker, rabbitMQUserService, rabbitTemplate, offerRepository);
    }

    @Test
    void notifyRideTimedOut_notifiesRequesterWithTimedOutStatus() {
        stubUserExchange();
        when(queueChecker.doesQueueExist("queue.user.requester-1")).thenReturn(false);

        notificationService.notifyRideTimedOut(rideRequest);

        RideNotification expected = RideMapper.mapToRideNotification((String) null, rideRequest, StatusEnum.TIMED_OUT);
        verify(queueChecker).doesQueueExist("queue.user.requester-1");
        verify(rabbitMQUserService).createUserQueue("requester-1");
        verify(rabbitTemplate).convertAndSend("user-exchange", "requester-1", expected);
        verifyNoInteractions(offerRepository);
    }

    @Test
    void notifyRideAccepted_notifiesOtherRidersExceptAcceptedAndDeduplicates() {
        stubUserExchange();
        when(queueChecker.doesQueueExist(anyString())).thenReturn(true);
        when(offerRepository.findByRideRequestIdAndStatus(rideRequest.getId(), OfferStatus.CANCELED))
                .thenReturn(List.of(
                        offer("rider-A"),
                        offer("rider-accepted"),
                        offer("rider-A"),
                        offer("rider-B")
                ));

        notificationService.notifyRideAccepted(rideRequest, "rider-accepted");

        RideNotification acceptedNotification =
                RideMapper.mapToRideNotification("rider-accepted", rideRequest, StatusEnum.ACCEPTED);
        RideNotification canceledNotification =
                RideMapper.mapToRideNotification("rider-accepted", rideRequest, StatusEnum.CANCELED);
        verify(rabbitTemplate).convertAndSend("user-exchange", "requester-1", acceptedNotification);
        verify(rabbitTemplate).convertAndSend("user-exchange", "rider-A", canceledNotification);
        verify(rabbitTemplate).convertAndSend("user-exchange", "rider-B", canceledNotification);
        verify(rabbitTemplate, times(3)).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(eventOutboxService).recordRiderEvent(RideRequestEventType.RIDER_CANCELED, rideRequest, "rider-A");
        verify(eventOutboxService).recordRiderEvent(RideRequestEventType.RIDER_CANCELED, rideRequest, "rider-B");
        verify(rabbitMQUserService, never()).createUserQueue(any());
    }

    private void stubUserExchange() {
        when(userExchange.getName()).thenReturn("user-exchange");
    }

    private RideRequestDriverOfferEntity offer(String riderIdentifier) {
        return RideRequestDriverOfferEntity.builder()
                .rider(RiderEntity.builder().identifier(riderIdentifier).build())
                .status(OfferStatus.CANCELED)
                .build();
    }
}
