package com.handler.ride_request.integration;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.rabbitmq.model.RideAcceptanceMessage;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "kafka.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
class RideAcceptanceRabbitMqIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management"))
            .withExposedPorts(5672);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private RideRequestDriverAttemptRepository attemptRepository;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @BeforeEach
    void cleanState() {
        attemptRepository.deleteAll();
        rideRequestRepository.deleteAll();
        riderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void consumesRideAcceptanceAndUpdatesRequestAndAttempts() {
        UserEntity user = userRepository.save(UserEntity.builder()
                .identifier("requester-acceptance")
                .name("Requester")
                .build());
        RideRequestEntity rideRequest = rideRequestRepository.save(RideRequestEntity.builder()
                .identifier("ride-acceptance")
                .user(user)
                .status(StatusEnum.PENDING)
                .location(new Point(2.3522, 48.8566))
                .build());
        RiderEntity acceptedRider = riderRepository.save(rider("accepted-rider"));
        RiderEntity waitingRider = riderRepository.save(rider("waiting-rider"));
        attemptRepository.save(attempt(rideRequest, acceptedRider));
        attemptRepository.save(attempt(rideRequest, waitingRider));

        rabbitTemplate.convertAndSend(
                "ride.acceptance.exchange",
                "ride.acceptance",
                new RideAcceptanceMessage("ride-acceptance", "accepted-rider"));

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            RideRequestEntity updatedRequest = rideRequestRepository.findByIdentifier("ride-acceptance").orElseThrow();
            assertThat(updatedRequest.getStatus()).isEqualTo(StatusEnum.ACCEPTED);
            assertThat(updatedRequest.getAcceptedRiderIdentifier()).isEqualTo("accepted-rider");
            assertThat(updatedRequest.getAcceptedAt()).isNotNull();

            assertThat(attemptRepository.findByRideRequestIdAndRiderIdentifier(updatedRequest.getId(), "accepted-rider"))
                    .hasValueSatisfying(attempt -> assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.ACCEPTED));
            assertThat(attemptRepository.findByRideRequestIdAndRiderIdentifier(updatedRequest.getId(), "waiting-rider"))
                    .hasValueSatisfying(attempt -> assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.CANCELED));
        });
    }

    private RiderEntity rider(String identifier) {
        return RiderEntity.builder()
                .identifier(identifier)
                .name("Rider " + identifier)
                .licenseNumber("LICENSE-" + identifier)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();
    }

    private RideRequestDriverAttemptEntity attempt(RideRequestEntity rideRequest, RiderEntity rider) {
        return RideRequestDriverAttemptEntity.builder()
                .rideRequest(rideRequest)
                .rider(rider)
                .notificationRound(1)
                .notifiedAt(OffsetDateTime.now())
                .status(AttemptStatus.NOTIFIED)
                .build();
    }
}
