package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.enums.StatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideAcceptanceServiceImplTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RiderRepository riderRepository;

    @Mock
    private RideRequestDriverAttemptService attemptService;

    private RideAcceptanceServiceImpl rideAcceptanceService;

    @BeforeEach
    void setUp() {
        rideAcceptanceService = new RideAcceptanceServiceImpl(rideRequestRepository, riderRepository, notificationService, attemptService);
    }

    @Test
    void acceptRide_updatesEntityAndNotifies() {
        RideRequestEntity entity = RideRequestEntity.builder()
                .identifier("ride-123")
                .status(StatusEnum.PENDING)
                .build();
        RiderEntity riderEntity = RiderEntity.builder()
                .identifier("rider-42")
                .build();
        when(rideRequestRepository.findByIdentifier("ride-123")).thenReturn(Optional.of(entity));
        when(riderRepository.findByIdentifier("rider-42")).thenReturn(Optional.of(riderEntity));
        when(rideRequestRepository.save(any(RideRequestEntity.class))).thenReturn(entity);

        rideAcceptanceService.acceptRide("ride-123", "rider-42");

        assertEquals(StatusEnum.ACCEPTED, entity.getStatus());
        assertEquals("rider-42", entity.getAcceptedRiderIdentifier());
        verify(notificationService).notifyRideAccepted(entity, "rider-42");
        verify(rideRequestRepository).save(entity);
    }

    @Test
    void acceptRide_whenNotPending_throwsIllegalState() {
        RideRequestEntity entity = RideRequestEntity.builder()
                .identifier("ride-123")
                .status(StatusEnum.ACCEPTED)
                .acceptedAt(OffsetDateTime.now())
                .build();
        when(rideRequestRepository.findByIdentifier("ride-123")).thenReturn(Optional.of(entity));
        when(riderRepository.findByIdentifier("rider-42")).thenReturn(Optional.of(RiderEntity.builder().identifier("rider-42").build()));

        assertThrows(IllegalStateException.class, () -> rideAcceptanceService.acceptRide("ride-123", "rider-42"));
        verify(notificationService, never()).notifyRideAccepted(any(), any());
    }

    @Test
    void acceptRide_whenRideMissing_throwsEntityNotFound() {
        when(rideRequestRepository.findByIdentifier("missing")).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> rideAcceptanceService.acceptRide("missing", "rider-42"));
    }
}
