package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.scheduler.RiderSearchScheduler;
import com.handler.ride_request.service.RidersSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessRequestServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RidersSearchService ridersSearchService;

    @Mock
    private RiderSearchScheduler riderSearchScheduler;

    @Mock
    private RideRequestDriverAttemptServiceImpl attemptService;

    @InjectMocks
    private ProcessRequestServiceImpl service;

    private RideRequest rideRequest;
    private UserEntity user;
    private RideRequestEntity persistedRequest;

    @BeforeEach
    void setUp() {
        rideRequest = RideRequest.builder()
                .userIdentifier("user-123")
                .location(Location.builder().latitude(1).longitude(2).build())
                .build();

        user = UserEntity.builder().id(10L).identifier("user-123").name("Jane").build();

        persistedRequest = RideRequestEntity.builder()
                .id(55L)
                .identifier("ride-55")
                .user(user)
                .status(StatusEnum.PENDING)
                .location(new Point(2, 1))
                .build();
    }

    @Test
    void shouldReturnWhenRideRequestIsNull() {
        service.processRideRequest(null);

        verifyNoInteractions(userRepository, rideRequestRepository, ridersSearchService,
                attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldThrowWhenRideRequestCannotBePersisted() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processRideRequest(rideRequest))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository).findByIdentifier("user-123");
        verifyNoInteractions(rideRequestRepository, ridersSearchService,
                attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldSkipWhenNoNearbyRidersFound() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.of(user));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(persistedRequest);
        when(ridersSearchService.findNearestVehicles(persistedRequest.getLocation(), java.util.Collections.emptySet()))
                .thenReturn(List.of());

        service.processRideRequest(rideRequest);

        verify(ridersSearchService).findNearestVehicles(persistedRequest.getLocation(), java.util.Collections.emptySet());
        verifyNoInteractions(attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldSkipNotificationWhenNoAttemptsPersisted() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.of(user));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(persistedRequest);
        List<Rider> nearby = List.of(Rider.builder().identifier("candidate-1").build());
        when(ridersSearchService.findNearestVehicles(persistedRequest.getLocation(), java.util.Collections.emptySet()))
                .thenReturn(nearby);
        when(attemptService.createAttemptsForRound(persistedRequest, nearby, 1)).thenReturn(List.of());

        service.processRideRequest(rideRequest);

        verify(attemptService).createAttemptsForRound(persistedRequest, nearby, 1);
        verifyNoInteractions(notificationService, riderSearchScheduler);
    }

    @Test
    void shouldNotifyRidersAndScheduleFollowUpWhenAttemptsExist() {
        List<Rider> nearby = List.of(Rider.builder().identifier("persisted-1").build());
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.of(user));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(persistedRequest);
        when(ridersSearchService.findNearestVehicles(persistedRequest.getLocation(), java.util.Collections.emptySet()))
                .thenReturn(nearby);
        when(attemptService.createAttemptsForRound(persistedRequest, nearby, 1)).thenReturn(nearby);

        service.processRideRequest(rideRequest);

        verify(notificationService).sendRabbitMqNotification(nearby, persistedRequest);
        verify(riderSearchScheduler).scheduleRidersSearch(persistedRequest.getId());
    }
}
