package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.domain.Rider;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.repository.RiderRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideRequestDriverOfferServiceImplTest {

    @Mock
    private RideRequestDriverOfferRepository offerRepository;

    @Mock
    private RiderRepository riderRepository;

    @InjectMocks
    private RideRequestDriverOfferServiceImpl service;

    @Test
    void shouldReturnEmptyWhenRideRequestIsNull() {
        List<Rider> riders = List.of(buildRider("r1"));

        List<Rider> result = service.createOffersForRound(null, riders, 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, offerRepository);
    }

    @Test
    void shouldReturnEmptyWhenNoPersistedRidersFound() {
        RideRequestEntity rideRequest = buildRideRequest(5L);
        List<Rider> riders = List.of(buildRider("missing"));
        when(riderRepository.findByDriverIdentifierIn(Set.of("missing"))).thenReturn(List.of());

        List<Rider> result = service.createOffersForRound(rideRequest, riders, 2);

        assertThat(result).isEmpty();
        verify(riderRepository).findByDriverIdentifierIn(Set.of("missing"));
        verifyNoInteractions(offerRepository);
    }

    @Test
    void shouldCreateOffersOnlyForPersistedRiders() {
        RideRequestEntity rideRequest = buildRideRequest(7L);
        Rider keptRider = buildRider("persisted");
        Rider skippedRider = buildRider("missing");
        List<Rider> riders = List.of(keptRider, skippedRider);
        RiderEntity persistedEntity = buildRiderEntity("persisted");
        when(riderRepository.findByDriverIdentifierIn(Set.of("persisted", "missing")))
                .thenReturn(List.of(persistedEntity));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RideRequestDriverOfferEntity>> offersCaptor = ArgumentCaptor.forClass(List.class);

        List<Rider> result = service.createOffersForRound(rideRequest, riders, 3);

        assertThat(result).containsExactly(keptRider);
        verify(riderRepository).findByDriverIdentifierIn(Set.of("persisted", "missing"));
        verify(offerRepository).saveAll(offersCaptor.capture());

        List<RideRequestDriverOfferEntity> savedOffers = offersCaptor.getValue();
        assertThat(savedOffers).hasSize(1);
        RideRequestDriverOfferEntity offer = savedOffers.get(0);
        assertThat(offer.getRideRequest()).isEqualTo(rideRequest);
        assertThat(offer.getRider()).isEqualTo(persistedEntity);
        assertThat(offer.getNotificationRound()).isEqualTo(3);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.NOTIFIED);
        assertThat(offer.getNotifiedAt()).isNotNull();
    }

    @Test
    void shouldMarkNotifiedOfferAsDeclined() {
        RideRequestDriverOfferEntity offer = RideRequestDriverOfferEntity.builder()
                .status(OfferStatus.NOTIFIED)
                .build();
        when(offerRepository.findByRideRequestIdAndDriverIdentifier(7L, "rider-1"))
                .thenReturn(Optional.of(offer));

        service.markDeclined(7L, "rider-1", java.time.OffsetDateTime.now());

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.DECLINED);
        assertThat(offer.getRespondedAt()).isNotNull();
        verify(offerRepository).save(offer);
    }

    @Test
    void shouldRejectDeclineForUnknownOffer() {
        assertThatThrownBy(() -> service.markDeclined(7L, "missing", java.time.OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        verify(offerRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeclineWhenOfferIsNotNotified() {
        RideRequestDriverOfferEntity offer = RideRequestDriverOfferEntity.builder()
                .status(OfferStatus.TIMED_OUT)
                .build();
        when(offerRepository.findByRideRequestIdAndDriverIdentifier(7L, "rider-1"))
                .thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.markDeclined(7L, "rider-1", java.time.OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        verify(offerRepository, never()).save(any());
    }

    private RideRequestEntity buildRideRequest(long id) {
        return RideRequestEntity.builder().id(id).build();
    }

    private Rider buildRider(String identifier) {
        return Rider.builder()
                .identifier(identifier)
                .driverIdentifier(identifier)
                .build();
    }

    private RiderEntity buildRiderEntity(String identifier) {
        return RiderEntity.builder()
                .identifier(identifier)
                .driverIdentifier(identifier)
                .build();
    }
}
