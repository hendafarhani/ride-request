package com.handler.ride_request.integration;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.service.serviceimpl.RideRequestDriverOfferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.geo.Point;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class RideRequestDriverOfferServiceImplIntegrationTest {

    @Autowired
    private RideRequestDriverOfferServiceImpl service;

    @Autowired
    private RideRequestDriverOfferRepository offerRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        offerRepository.deleteAll();
        riderRepository.deleteAll();
        rideRequestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createOffersForRoundPersistsOnlyExistingRiders() {
        RideRequestEntity rideRequest = persistRideRequest("ride-create-test");
        RiderEntity persistedOne = persistRider("persisted-1");
        RiderEntity persistedTwo = persistRider("persisted-2");

        List<Rider> requestedRiders = List.of(
                toModel(persistedOne),
                toModel(persistedTwo),
                Rider.builder().identifier("ghost-rider").point(new Point(0, 0)).build()
        );

        List<Rider> returnedRiders = service.createOffersForRound(rideRequest, requestedRiders, 2);

        assertThat(returnedRiders)
                .extracting(Rider::getIdentifier)
                .containsExactlyInAnyOrder(persistedOne.getIdentifier(), persistedTwo.getIdentifier());

        List<RideRequestDriverOfferEntity> offers = offerRepository
                .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId());

        assertThat(offers)
                .allSatisfy(offer -> {
                    assertThat(offer.getNotificationRound()).isEqualTo(2);
                    assertThat(offer.getStatus()).isEqualTo(OfferStatus.NOTIFIED);
                    assertThat(offer.getNotifiedAt()).isNotNull();
                    assertThat(offers).hasSize(2);
                    assertThat(Set.of(persistedOne.getId(), persistedTwo.getId()))
                            .contains(offer.getRider().getId());
                });
    }

    @Test
    void markAcceptedAndCancelOthersUpdatesStatusesAndTimestamps() {
        RideRequestEntity rideRequest = persistRideRequest("ride-acceptance-test");
        RiderEntity acceptedRider = persistRider("accepted-rider");
        RiderEntity waitingRider = persistRider("waiting-rider");

        service.createOffersForRound(rideRequest,
                List.of(toModel(acceptedRider), toModel(waitingRider)), 1);

        OffsetDateTime respondedAt = OffsetDateTime.now();
        service.markAccepted(rideRequest.getId(), acceptedRider.getIdentifier(), respondedAt);
        service.markOtherOpenOffersAsCanceled(rideRequest.getId(), acceptedRider.getIdentifier(), respondedAt);

        List<RideRequestDriverOfferEntity> offers = offerRepository
                .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId());

        assertThat(offers).hasSize(2);
        var accepted = offers.stream()
                .filter(a -> acceptedRider.getIdentifier().equals(a.getRider().getIdentifier()))
                .findFirst()
                .orElseThrow();
        var canceled = offers.stream()
                .filter(a -> waitingRider.getIdentifier().equals(a.getRider().getIdentifier()))
                .findFirst()
                .orElseThrow();

        assertThat(accepted.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(accepted.getRespondedAt()).isEqualTo(respondedAt);
        assertThat(canceled.getStatus()).isEqualTo(OfferStatus.CANCELED);
        assertThat(canceled.getRespondedAt()).isEqualTo(respondedAt);
    }

    private RideRequestEntity persistRideRequest(String identifier) {
        UserEntity user = userRepository.save(UserEntity.builder()
                .name("Requester")
                .identifier("user-" + identifier)
                .build());

        RideRequestEntity entity = RideRequestEntity.builder()
                .user(user)
                .identifier(identifier)
                .status(StatusEnum.PENDING)
                .location(new Point(1.0, 2.0))
                .build();
        return rideRequestRepository.save(entity);
    }

    private RiderEntity persistRider(String identifier) {
        RiderEntity rider = RiderEntity.builder()
                .name("Rider " + identifier)
                .identifier(identifier)
                .licenseNumber("LICENSE-" + identifier)
                .build();
        return riderRepository.save(rider);
    }

    private Rider toModel(RiderEntity entity) {
        return Rider.builder()
                .identifier(entity.getIdentifier())
                .userName(entity.getName())
                .point(new Point(10.0, 20.0))
                .build();
    }
}
