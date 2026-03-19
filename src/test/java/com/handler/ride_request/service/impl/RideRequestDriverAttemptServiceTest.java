package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideRequestDriverAttemptServiceTest {

    @Mock
    private RideRequestDriverAttemptRepository attemptRepository;

    @Mock
    private RiderRepository riderRepository;

    @InjectMocks
    private RideRequestDriverAttemptService service;

    private RideRequestEntity rideRequest;
    private Rider persistedRider;
    private Rider missingRider;
    private RiderEntity persistedRiderEntity;

    @BeforeEach
    void setUp() {
        rideRequest = RideRequestEntity.builder().id(42L).build();
        persistedRider = Rider.builder().identifier("persisted-id").build();
        missingRider = Rider.builder().identifier("missing-id").build();
        persistedRiderEntity = RiderEntity.builder().identifier("persisted-id").build();
    }

    @Test
    void shouldReturnEmptyWhenRideRequestIsNull() {
        List<Rider> result = service.createAttemptsForRound(null, List.of(persistedRider), 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void shouldReturnEmptyWhenRidersListIsNull() {
        List<Rider> result = service.createAttemptsForRound(rideRequest, null, 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void shouldReturnEmptyWhenNoPersistedRidersFound() {
        when(riderRepository.findByIdentifierIn(anySet())).thenReturn(List.of());

        List<Rider> result = service.createAttemptsForRound(rideRequest, List.of(persistedRider, missingRider), 1);

        assertThat(result).isEmpty();
        verify(attemptRepository, never()).saveAll(any());
    }

    @Test
    void shouldPersistAttemptsOnlyForExistingRiders() {
        when(riderRepository.findByIdentifierIn(Set.of("persisted-id", "missing-id")))
                .thenReturn(List.of(persistedRiderEntity));

        List<Rider> result = service.createAttemptsForRound(rideRequest, List.of(persistedRider, missingRider), 2);

        assertThat(result).containsExactly(persistedRider);
        ArgumentCaptor<List<RideRequestDriverAttemptEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(attemptRepository).saveAll(captor.capture());
        List<RideRequestDriverAttemptEntity> savedAttempts = captor.getValue();
        assertThat(savedAttempts).hasSize(1);
        RideRequestDriverAttemptEntity attempt = savedAttempts.getFirst();
        assertThat(attempt.getRideRequest()).isEqualTo(rideRequest);
        assertThat(attempt.getRider()).isEqualTo(persistedRiderEntity);
        assertThat(attempt.getNotificationRound()).isEqualTo(2);
    }
}

