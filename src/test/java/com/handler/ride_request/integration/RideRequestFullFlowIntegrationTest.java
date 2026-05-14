package com.handler.ride_request.integration;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.rabbitmq.model.RideAcceptanceMessage;
import org.springframework.amqp.core.Message;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.service.ProcessRequestService;
import com.handler.ride_request.service.impl.RidersSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "kafka.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "ride.acceptance.exchange=ride.acceptance.exchange",
        "ride.acceptance.queue=ride.acceptance.queue",
        "ride.acceptance.routing-key=ride.acceptance"
})
@Testcontainers(disabledWithoutDocker = true)
class RideRequestFullFlowIntegrationTest {

    private static final String REQUESTER_IDENTIFIER = "requester-full-flow";
    private static final String ACCEPTED_RIDER_IDENTIFIER = "rider-full-flow-1";
    private static final String CANCELED_RIDER_IDENTIFIER = "rider-full-flow-2";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("ride_requests_db")
            .withUsername("microgo_user")
            .withPassword("password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Autowired
    private ProcessRequestService processRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private RideRequestDriverAttemptRepository attemptRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @BeforeEach
    void cleanState() {
        attemptRepository.deleteAll();
        rideRequestRepository.deleteAll();
        riderRepository.deleteAll();
        userRepository.deleteAll();
        stringRedisTemplate.delete(RidersSearchServiceImpl.VEHICLE_LOCATION);
    }

    @Test
    void processesRideRequestNotifiesRidersAndAcceptsRide() {
        seedRequester();
        seedRider(ACCEPTED_RIDER_IDENTIFIER, "LICENSE-FLOW-1");
        seedRider(CANCELED_RIDER_IDENTIFIER, "LICENSE-FLOW-2");
        seedNearbyRiderLocations();

        processRequestService.processRideRequest(RideRequest.builder()
                .userIdentifier(REQUESTER_IDENTIFIER)
                .location(Location.builder()
                        .latitude(48.8584)
                        .longitude(2.2945)
                        .build())
                .build());

        RideRequestEntity rideRequest = findOnlyRideRequest();
        assertThat(rideRequest.getStatus()).isEqualTo(StatusEnum.PENDING);
        assertThat(rideRequest.getIdentifier()).startsWith(REQUESTER_IDENTIFIER);

        List<RideRequestDriverAttemptEntity> attempts = attemptRepository
                .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId());
        assertThat(attempts)
                .hasSize(2)
                .allSatisfy(attempt -> assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED));

        String acceptedRiderNotification = receiveNotification("queue.user." + ACCEPTED_RIDER_IDENTIFIER);
        String canceledRiderInitialNotification = receiveNotification("queue.user." + CANCELED_RIDER_IDENTIFIER);
        assertThat(acceptedRiderNotification).contains("\"status\":\"PENDING\"");
        assertThat(canceledRiderInitialNotification).contains("\"status\":\"PENDING\"");

        rabbitTemplate.convertAndSend(
                "ride.acceptance.exchange",
                "ride.acceptance",
                new RideAcceptanceMessage(rideRequest.getIdentifier(), ACCEPTED_RIDER_IDENTIFIER)
        );

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            RideRequestEntity acceptedRequest = rideRequestRepository.findByIdentifier(rideRequest.getIdentifier()).orElseThrow();
            assertThat(acceptedRequest.getStatus()).isEqualTo(StatusEnum.ACCEPTED);
            assertThat(acceptedRequest.getAcceptedRiderIdentifier()).isEqualTo(ACCEPTED_RIDER_IDENTIFIER);
            assertThat(acceptedRequest.getAcceptedAt()).isNotNull();

            List<RideRequestDriverAttemptEntity> updatedAttempts = attemptRepository
                    .findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(acceptedRequest.getId());
            assertThat(statusFor(updatedAttempts, ACCEPTED_RIDER_IDENTIFIER)).isEqualTo(AttemptStatus.ACCEPTED);
            assertThat(statusFor(updatedAttempts, CANCELED_RIDER_IDENTIFIER)).isEqualTo(AttemptStatus.CANCELED);
        });

        String requesterNotification = receiveNotification("queue.user." + REQUESTER_IDENTIFIER);
        String canceledRiderNotification = receiveNotification("queue.user." + CANCELED_RIDER_IDENTIFIER);
        assertThat(requesterNotification)
                .contains("\"status\":\"ACCEPTED\"")
                .contains("\"riderIdentifier\":\"" + ACCEPTED_RIDER_IDENTIFIER + "\"");
        assertThat(canceledRiderNotification)
                .contains("\"status\":\"CANCELED\"")
                .contains("\"riderIdentifier\":\"" + ACCEPTED_RIDER_IDENTIFIER + "\"");
    }

    private void seedRequester() {
        userRepository.save(UserEntity.builder()
                .name("Requester")
                .identifier(REQUESTER_IDENTIFIER)
                .build());
    }

    private void seedRider(String identifier, String licenseNumber) {
        riderRepository.save(RiderEntity.builder()
                .name("Rider " + identifier)
                .identifier(identifier)
                .licenseNumber(licenseNumber)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }

    private void seedNearbyRiderLocations() {
        stringRedisTemplate.opsForGeo().add(
                RidersSearchServiceImpl.VEHICLE_LOCATION,
                new Point(2.2945, 48.8584),
                ACCEPTED_RIDER_IDENTIFIER
        );
        stringRedisTemplate.opsForGeo().add(
                RidersSearchServiceImpl.VEHICLE_LOCATION,
                new Point(2.2950, 48.8587),
                CANCELED_RIDER_IDENTIFIER
        );
    }

    private RideRequestEntity findOnlyRideRequest() {
        List<RideRequestEntity> rideRequests = StreamSupport
                .stream(rideRequestRepository.findAll().spliterator(), false)
                .toList();
        assertThat(rideRequests).hasSize(1);
        return rideRequests.getFirst();
    }

    private String receiveNotification(String queueName) {
        Message message = rabbitTemplate.receive(queueName, 5_000);
        assertThat(message).isNotNull();
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    private AttemptStatus statusFor(List<RideRequestDriverAttemptEntity> attempts, String riderIdentifier) {
        return attempts.stream()
                .filter(attempt -> riderIdentifier.equals(attempt.getRider().getIdentifier()))
                .map(RideRequestDriverAttemptEntity::getStatus)
                .findFirst()
                .orElseThrow();
    }
}
