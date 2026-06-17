package com.handler.ride_request.integration;

import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.repository.EventOutboxRepository;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RideRequestRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RideRequestDriverOfferRepository offerRepository;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @Test
    void userRepositoryFindsByIdentifier() {
        UserEntity user = userRepository.save(UserEntity.builder()
                .name("Requester")
                .identifier("user-1")
                .build());

        assertThat(userRepository.findByIdentifier("user-1"))
                .contains(user);
        assertThat(userRepository.findByIdentifier("missing-user"))
                .isEmpty();
    }

    @Test
    void riderRepositoryFindsByIdentifierAndIdentifierIn() {
        RiderEntity riderOne = riderRepository.save(rider("rider-1"));
        RiderEntity riderTwo = riderRepository.save(rider("rider-2"));
        riderRepository.save(rider("rider-3"));

        assertThat(riderRepository.findByIdentifier("rider-1"))
                .contains(riderOne);

        assertThat(riderRepository.findByIdentifierIn(List.of("rider-1", "rider-2", "missing-rider")))
                .extracting(RiderEntity::getIdentifier)
                .containsExactlyInAnyOrder(riderOne.getIdentifier(), riderTwo.getIdentifier());
    }

    @Test
    void rideRequestRepositoryFindsByIdentifier() {
        RideRequestEntity request = rideRequestRepository.save(rideRequest("ride-1"));

        assertThat(rideRequestRepository.findByIdentifier("ride-1"))
                .contains(request);
        assertThat(rideRequestRepository.findByIdentifier("missing-ride"))
                .isEmpty();
    }

    @Test
    void rideRequestRepositoryPersistsTimedOutStatusWithStringEnumMapping() {
        RideRequestEntity request = rideRequestRepository.save(rideRequest("ride-timed-out"));
        request.setStatus(StatusEnum.TIMED_OUT);
        rideRequestRepository.save(request);
        entityManager.flush();
        entityManager.clear();

        assertThat(rideRequestRepository.findById(request.getId()).map(RideRequestEntity::getStatus))
                .as("TIMED_OUT must persist without ordinal check-constraint drift")
                .hasValue(StatusEnum.TIMED_OUT);

        String storedStatus = jdbcTemplate.queryForObject(
                "select status from ride_request where id = ?",
                String.class,
                request.getId());
        assertThat(storedStatus).isEqualTo(StatusEnum.TIMED_OUT.name());
    }

    @Test
    void offerRepositoryQueriesOffersByRideRequest() {
        RideRequestEntity request = rideRequestRepository.save(rideRequest("ride-offers"));
        RiderEntity firstRider = riderRepository.save(rider("offer-rider-1"));
        RiderEntity secondRider = riderRepository.save(rider("offer-rider-2"));
        RiderEntity thirdRider = riderRepository.save(rider("offer-rider-3"));

        OffsetDateTime baseTime = OffsetDateTime.parse("2026-05-30T10:00:00Z");
        RideRequestDriverOfferEntity secondRound = offerRepository.save(offer(
                request, thirdRider, 2, baseTime.plusMinutes(1), OfferStatus.TIMED_OUT));
        RideRequestDriverOfferEntity firstRoundSecond = offerRepository.save(offer(
                request, secondRider, 1, baseTime.plusMinutes(2), OfferStatus.ACCEPTED));
        RideRequestDriverOfferEntity firstRoundFirst = offerRepository.save(offer(
                request, firstRider, 1, baseTime, OfferStatus.NOTIFIED));

        assertThat(offerRepository.findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(request.getId()))
                .extracting(RideRequestDriverOfferEntity::getId)
                .containsExactly(firstRoundFirst.getId(), firstRoundSecond.getId(), secondRound.getId());

        assertThat(offerRepository.findByRideRequestIdAndStatus(request.getId(), OfferStatus.ACCEPTED))
                .singleElement()
                .extracting(RideRequestDriverOfferEntity::getId)
                .isEqualTo(firstRoundSecond.getId());

        assertThat(offerRepository.findByRideRequestIdAndRiderIdentifier(request.getId(), firstRider.getIdentifier()))
                .contains(firstRoundFirst);

        assertThat(offerRepository.findMaxNotificationRound(request.getId()))
                .isEqualTo(2);
    }

    @Test
    void eventOutboxRepositoryFindsPendingEventsByAudienceAndMarksProcessed() {
        RideRequestEntity request = rideRequestRepository.save(rideRequest("ride-outbox"));
        EventOutboxEntity first = eventOutboxRepository.save(outboxEvent(
                request, RideRequestEventType.REQUEST_CREATED, null, OffsetDateTime.parse("2026-05-30T10:00:00Z")));
        EventOutboxEntity second = eventOutboxRepository.save(outboxEvent(
                request, RideRequestEventType.RIDER_NOTIFIED, "rider-outbox", OffsetDateTime.parse("2026-05-30T10:01:00Z")));
        EventOutboxEntity processed = outboxEvent(
                request, RideRequestEventType.REQUEST_TIMED_OUT, null, OffsetDateTime.parse("2026-05-30T10:02:00Z"));
        processed.setStatus(OutboxEventStatus.PROCESSED);
        eventOutboxRepository.save(processed);

        assertThat(eventOutboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .extracting(EventOutboxEntity::getId)
                .contains(first.getId(), second.getId());

        assertThat(eventOutboxRepository.findByRideRequestIdAndStatusOrderByCreatedAtAsc(request.getId(), OutboxEventStatus.PENDING))
                .extracting(EventOutboxEntity::getEventType)
                .containsExactly(RideRequestEventType.REQUEST_CREATED, RideRequestEventType.RIDER_NOTIFIED);

        assertThat(eventOutboxRepository.findByRequesterIdAndStatusOrderByCreatedAtAsc(
                request.getUser().getIdentifier(), OutboxEventStatus.PENDING))
                .extracting(EventOutboxEntity::getId)
                .containsExactly(first.getId(), second.getId());

        assertThat(eventOutboxRepository.findByRiderIdAndStatusOrderByCreatedAtAsc("rider-outbox", OutboxEventStatus.PENDING))
                .singleElement()
                .extracting(EventOutboxEntity::getId)
                .isEqualTo(second.getId());
    }

    private RideRequestEntity rideRequest(String identifier) {
        UserEntity user = userRepository.save(UserEntity.builder()
                .name("User " + identifier)
                .identifier("user-" + identifier)
                .build());

        return RideRequestEntity.builder()
                .user(user)
                .identifier(identifier)
                .status(StatusEnum.PENDING)
                .location(new Point(2.3522, 48.8566))
                .build();
    }

    private RiderEntity rider(String identifier) {
        return RiderEntity.builder()
                .name("Rider " + identifier)
                .identifier(identifier)
                .licenseNumber("LICENSE-" + identifier)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();
    }

    private RideRequestDriverOfferEntity offer(RideRequestEntity request,
                                                   RiderEntity rider,
                                                   int notificationRound,
                                                   OffsetDateTime notifiedAt,
                                                   OfferStatus status) {
        return RideRequestDriverOfferEntity.builder()
                .rideRequest(request)
                .rider(rider)
                .notificationRound(notificationRound)
                .notifiedAt(notifiedAt)
                .status(status)
                .build();
    }

    private EventOutboxEntity outboxEvent(RideRequestEntity request,
                                          RideRequestEventType eventType,
                                          String riderIdentifier,
                                          OffsetDateTime createdAt) {
        return EventOutboxEntity.builder()
                .eventType(eventType)
                .status(OutboxEventStatus.PENDING)
                .rideRequestId(request.getId())
                .rideRequestIdentifier(request.getIdentifier())
                .requesterId(request.getUser().getIdentifier())
                .riderId(riderIdentifier)
                .payload("{}")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
