package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.mapper.RideMapper;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.rabbitmq.service.QueueChecker;
import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    private RideRequestDriverAttemptRepository attemptRepository;

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
        RideNotification notification = RideNotification.builder().status(StatusEnum.PENDING).build();
        when(queueChecker.doesQueueExist("queue.user.missing-queue")).thenReturn(false);

        try (MockedStatic<RideMapper> mapper = mockStatic(RideMapper.class)) {
            mapper.when(() -> RideMapper.mapToRideNotification(rider, rideRequest, StatusEnum.PENDING))
                    .thenReturn(notification);

            notificationService.sendRabbitMqNotification(List.of(rider), rideRequest);
        }

        verify(rabbitMQUserService).createUserQueue("missing-queue");
        verify(rabbitTemplate).convertAndSend("user-exchange", "missing-queue", notification);
    }

    @Test
    void sendRabbitMqNotification_skipsQueueCreationWhenAlreadyProvisioned() {
        stubUserExchange();
        Rider rider = Rider.builder().identifier("existing-queue").build();
        RideNotification notification = RideNotification.builder().status(StatusEnum.PENDING).build();
        when(queueChecker.doesQueueExist("queue.user.existing-queue")).thenReturn(true);

        try (MockedStatic<RideMapper> mapper = mockStatic(RideMapper.class)) {
            mapper.when(() -> RideMapper.mapToRideNotification(rider, rideRequest, StatusEnum.PENDING))
                    .thenReturn(notification);

            notificationService.sendRabbitMqNotification(List.of(rider), rideRequest);
        }

        verify(rabbitMQUserService, never()).createUserQueue(any());
        verify(rabbitTemplate).convertAndSend("user-exchange", "existing-queue", notification);
    }

    @Test
    void notifyRideAccepted_returnsImmediatelyWhenRideRequestIsNull() {
        notificationService.notifyRideAccepted(null, "accepted-rider");

        verifyNoInteractions(queueChecker, rabbitMQUserService, rabbitTemplate, attemptRepository);
    }

    @Test
    void notifyRideAccepted_notifiesRequesterWhenRideIsAccepted() {
        stubUserExchange();
        when(queueChecker.doesQueueExist("queue.user.requester-1")).thenReturn(false);
        RideNotification acceptedNotification = RideNotification.builder().status(StatusEnum.ACCEPTED).build();

        try (MockedStatic<RideMapper> mapper = mockStatic(RideMapper.class)) {
            mapper.when(() -> RideMapper.mapToRideNotification("accepted-rider", rideRequest, StatusEnum.ACCEPTED))
                    .thenReturn(acceptedNotification);

            notificationService.notifyRideAccepted(rideRequest, "accepted-rider");
        }

        verify(queueChecker).doesQueueExist("queue.user.requester-1");
        verify(rabbitMQUserService).createUserQueue("requester-1");
        verify(rabbitTemplate).convertAndSend("user-exchange", "requester-1", acceptedNotification);
    }

    @Test
    void notifyRideAccepted_notifiesOtherRidersExceptAcceptedAndDeduplicates() {
        stubUserExchange();
        when(queueChecker.doesQueueExist(anyString())).thenReturn(true);
        RideNotification acceptedNotification = RideNotification.builder().status(StatusEnum.ACCEPTED).build();
        RideNotification canceledNotification = RideNotification.builder().status(StatusEnum.CANCELED).build();
        when(attemptRepository.findByRideRequestIdAndStatus(rideRequest.getId(), AttemptStatus.CANCELED))
                .thenReturn(List.of(
                        attempt("rider-A"),
                        attempt("rider-accepted"),
                        attempt("rider-A"),
                        attempt("rider-B")
                ));

        try (MockedStatic<RideMapper> mapper = mockStatic(RideMapper.class)) {
            mapper.when(() -> RideMapper.mapToRideNotification("rider-accepted", rideRequest, StatusEnum.ACCEPTED))
                    .thenReturn(acceptedNotification);
            mapper.when(() -> RideMapper.mapToRideNotification("rider-accepted", rideRequest, StatusEnum.CANCELED))
                    .thenReturn(canceledNotification);

            notificationService.notifyRideAccepted(rideRequest, "rider-accepted");
        }

        verify(rabbitTemplate).convertAndSend("user-exchange", "requester-1", acceptedNotification);
        verify(rabbitTemplate).convertAndSend("user-exchange", "rider-A", canceledNotification);
        verify(rabbitTemplate).convertAndSend("user-exchange", "rider-B", canceledNotification);
        verify(rabbitTemplate, times(3)).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(rabbitMQUserService, never()).createUserQueue(any());
    }

    private void stubUserExchange() {
        when(userExchange.getName()).thenReturn("user-exchange");
    }

    private RideRequestDriverAttemptEntity attempt(String riderIdentifier) {
        return RideRequestDriverAttemptEntity.builder()
                .rider(RiderEntity.builder().identifier(riderIdentifier).build())
                .status(AttemptStatus.CANCELED)
                .build();
    }
}
