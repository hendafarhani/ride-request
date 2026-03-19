package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RiderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideRequestDriverAttemptServiceImplTest {

    @Mock
    private RideRequestDriverAttemptRepository attemptRepository;

    @Mock
    private RiderRepository riderRepository;

    @InjectMocks
    private RideRequestDriverAttemptServiceImpl service;

    @Test
    void shouldReturnEmptyWhenRideRequestIsNull() {
        List<Rider> riders = List.of(buildRider("r1"));

        List<Rider> result = service.createAttemptsForRound(null, riders, 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void shouldReturnEmptyWhenNoPersistedRidersFound() {
        RideRequestEntity rideRequest = buildRideRequest(5L);
        List<Rider> riders = List.of(buildRider("missing"));
        when(riderRepository.findByIdentifierIn(Set.of("missing"))).thenReturn(List.of());

        List<Rider> result = service.createAttemptsForRound(rideRequest, riders, 2);

        assertThat(result).isEmpty();
        verify(riderRepository).findByIdentifierIn(Set.of("missing"));
        verifyNoInteractions(attemptRepository);
    }

    @Test
    void shouldCreateAttemptsOnlyForPersistedRiders() {
        RideRequestEntity rideRequest = buildRideRequest(7L);
        Rider keptRider = buildRider("persisted");
        Rider skippedRider = buildRider("missing");
        List<Rider> riders = List.of(keptRider, skippedRider);
        RiderEntity persistedEntity = buildRiderEntity("persisted");
        when(riderRepository.findByIdentifierIn(Set.of("persisted", "missing")))
                .thenReturn(List.of(persistedEntity));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RideRequestDriverAttemptEntity>> attemptsCaptor = ArgumentCaptor.forClass(List.class);

        List<Rider> result = service.createAttemptsForRound(rideRequest, riders, 3);

        assertThat(result).containsExactly(keptRider);
        verify(riderRepository).findByIdentifierIn(Set.of("persisted", "missing"));
        verify(attemptRepository).saveAll(attemptsCaptor.capture());

        List<RideRequestDriverAttemptEntity> savedAttempts = attemptsCaptor.getValue();
        assertThat(savedAttempts).hasSize(1);
        RideRequestDriverAttemptEntity attempt = savedAttempts.get(0);
        assertThat(attempt.getRideRequest()).isEqualTo(rideRequest);
        assertThat(attempt.getRider()).isEqualTo(persistedEntity);
        assertThat(attempt.getNotificationRound()).isEqualTo(3);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED);
        assertThat(attempt.getNotifiedAt()).isNotNull();
    }

    private RideRequestEntity buildRideRequest(long id) {
        return RideRequestEntity.builder().id(id).build();
    }

    private Rider buildRider(String identifier) {
        return Rider.builder().identifier(identifier).build();
    }

    private RiderEntity buildRiderEntity(String identifier) {
        return RiderEntity.builder().identifier(identifier).build();
    }
}

