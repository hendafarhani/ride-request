package com.handler.ride_request.integration;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.service.impl.RideRequestDriverAttemptServiceImpl;
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
class RideRequestDriverAttemptServiceImplIntegrationTest {

    @Autowired
    private RideRequestDriverAttemptServiceImpl service;

    @Autowired
    private RideRequestDriverAttemptRepository attemptRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        attemptRepository.deleteAll();
        riderRepository.deleteAll();
        rideRequestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createAttemptsForRoundPersistsOnlyExistingRiders() {
        RideRequestEntity rideRequest = persistRideRequest("ride-create-test");
        RiderEntity persistedOne = persistRider("persisted-1");
        RiderEntity persistedTwo = persistRider("persisted-2");

        List<Rider> requestedRiders = List.of(
                toModel(persistedOne),
                toModel(persistedTwo),
                Rider.builder().identifier("ghost-rider").point(new Point(0, 0)).build()
        );

        List<Rider> returnedRiders = service.createAttemptsForRound(rideRequest, requestedRiders, 2);

        assertThat(returnedRiders)
                .extracting(Rider::getIdentifier)
                .containsExactlyInAnyOrder(persistedOne.getIdentifier(), persistedTwo.getIdentifier());

        List<RideRequestDriverAttemptEntity> attempts = attemptRepository
                .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId());

        assertThat(attempts).hasSize(2);
        assertThat(attempts)
                .allSatisfy(attempt -> {
                    assertThat(attempt.getNotificationRound()).isEqualTo(2);
                    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED);
                    assertThat(attempt.getNotifiedAt()).isNotNull();
                    assertThat(Set.of(persistedOne.getId(), persistedTwo.getId()))
                            .contains(attempt.getRider().getId());
                });
    }

    @Test
    void markAcceptedAndCancelOthersUpdatesStatusesAndTimestamps() {
        RideRequestEntity rideRequest = persistRideRequest("ride-acceptance-test");
        RiderEntity acceptedRider = persistRider("accepted-rider");
        RiderEntity waitingRider = persistRider("waiting-rider");

        service.createAttemptsForRound(rideRequest,
                List.of(toModel(acceptedRider), toModel(waitingRider)), 1);

        OffsetDateTime respondedAt = OffsetDateTime.now();
        service.markAccepted(rideRequest.getId(), acceptedRider.getIdentifier(), respondedAt);
        service.markOtherOpenAttemptsAsCanceled(rideRequest.getId(), acceptedRider.getIdentifier(), respondedAt);

        List<RideRequestDriverAttemptEntity> attempts = attemptRepository
                .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId());

        assertThat(attempts).hasSize(2);
        var accepted = attempts.stream()
                .filter(a -> acceptedRider.getIdentifier().equals(a.getRider().getIdentifier()))
                .findFirst()
                .orElseThrow();
        var canceled = attempts.stream()
                .filter(a -> waitingRider.getIdentifier().equals(a.getRider().getIdentifier()))
                .findFirst()
                .orElseThrow();

        assertThat(accepted.getStatus()).isEqualTo(AttemptStatus.ACCEPTED);
        assertThat(accepted.getRespondedAt()).isEqualTo(respondedAt);
        assertThat(canceled.getStatus()).isEqualTo(AttemptStatus.CANCELED);
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
