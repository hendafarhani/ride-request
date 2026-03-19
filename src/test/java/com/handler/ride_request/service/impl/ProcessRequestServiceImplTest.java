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
import java.util.Set;

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
    private RideRequestDriverAttemptService attemptService;

    @InjectMocks
    private ProcessRequestServiceImpl service;

    private RideRequest rideRequest;
    private UserEntity userEntity;
    private RideRequestEntity persistedRequest;

    @BeforeEach
    void setUp() {
        rideRequest = RideRequest.builder()
                .userIdentifier("user-123")
                .location(Location.builder().latitude(1.0).longitude(2.0).build())
                .build();

        userEntity = UserEntity.builder()
                .id(5L)
                .identifier("user-123")
                .name("John Doe")
                .build();

        persistedRequest = RideRequestEntity.builder()
                .id(42L)
                .identifier("ride-42")
                .user(userEntity)
                .status(StatusEnum.PENDING)
                .location(new Point(2.0, 1.0))
                .build();
    }

    @Test
    void shouldReturnImmediatelyWhenRideRequestIsNull() {
        service.processRideRequest(null);

        verifyNoInteractions(userRepository, rideRequestRepository, ridersSearchService,
                attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldNotProcessWhenUserIsUnknown() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.empty());

        service.processRideRequest(rideRequest);

        verify(userRepository).findByIdentifier("user-123");
        verifyNoInteractions(rideRequestRepository, ridersSearchService,
                attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldSkipProcessingWhenNoNearbyRidersAreFound() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.of(userEntity));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(persistedRequest);
        when(ridersSearchService.findNearestVehicles(persistedRequest.getLocation(), Set.of()))
                .thenReturn(List.of());

        service.processRideRequest(rideRequest);

        verify(ridersSearchService).findNearestVehicles(persistedRequest.getLocation(), Set.of());
        verifyNoInteractions(attemptService, notificationService, riderSearchScheduler);
    }

    @Test
    void shouldCreateAttemptsNotifyRidersAndScheduleFollowUp() {
        when(userRepository.findByIdentifier("user-123")).thenReturn(Optional.of(userEntity));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(persistedRequest);
        List<Rider> nearbyRiders = List.of(Rider.builder().identifier("candidate-1").build());
        List<Rider> persistedCandidates = List.of(Rider.builder().identifier("persisted-1").build());
        when(ridersSearchService.findNearestVehicles(persistedRequest.getLocation(), Set.of()))
                .thenReturn(nearbyRiders);
        when(attemptService.createAttemptsForRound(persistedRequest, nearbyRiders, 1))
                .thenReturn(persistedCandidates);

        service.processRideRequest(rideRequest);

        verify(attemptService).createAttemptsForRound(persistedRequest, nearbyRiders, 1);
        verify(notificationService).sendRabbitMqNotification(persistedCandidates, persistedRequest);
        verify(riderSearchScheduler).scheduleRidersSearch(persistedRequest.getId());
    }
}

