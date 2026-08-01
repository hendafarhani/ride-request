package com.handler.ride_request.integration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.domain.Location;
import com.handler.ride_request.domain.RideRequest;
import com.handler.ride_request.repository.EventOutboxRepository;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
class RideRequestKafkaFlowIT {

    // Dispatch matching reads positions from vehicle_location and intersects with the
    // simulation-owned available_drivers SET, so seed both.
    private static final String VEHICLE_LOCATION = "vehicle_location";
    private static final String AVAILABLE_DRIVERS = "available_drivers";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management"))
            .withExposedPorts(5672);

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private RideRequestDriverOfferRepository offerRepository;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @Value("${kafka.topics.ride-requests}")
    private String rideRequestsTopic;

    @Value("${kafka.topics.driver-generated}")
    private String driverGeneratedTopic;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @BeforeEach
    void cleanState() {
        eventOutboxRepository.deleteAll();
        offerRepository.deleteAll();
        rideRequestRepository.deleteAll();
        riderRepository.deleteAll();
        userRepository.deleteAll();
        stringRedisTemplate.delete(java.util.List.of(VEHICLE_LOCATION, AVAILABLE_DRIVERS));
    }

    @Test
    void consumesRideRequestFromKafkaPersistsOffersAndPublishesRabbitNotification() throws Exception {
        userRepository.save(UserEntity.builder()
                .identifier("user-flow")
                .name("Flow User")
                .build());
        riderRepository.save(rider("rider-flow-1"));
        riderRepository.save(rider("rider-flow-2"));
        stringRedisTemplate.opsForGeo().add(VEHICLE_LOCATION, new Point(2.3522, 48.8566), "rider-flow-1");
        stringRedisTemplate.opsForGeo().add(VEHICLE_LOCATION, new Point(2.3610, 48.8560), "rider-flow-2");
        stringRedisTemplate.opsForSet().add(AVAILABLE_DRIVERS, "rider-flow-1", "rider-flow-2");

        RideRequest rideRequest = RideRequest.builder()
                .userIdentifier("user-flow")
                .location(Location.builder().latitude(48.8566).longitude(2.3522).build())
                .build();

        kafkaTemplate.send(rideRequestsTopic, "user-flow", objectMapper.writeValueAsBytes(rideRequest))
                .get(10, TimeUnit.SECONDS);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<RideRequestEntity> rideRequests = (List<RideRequestEntity>) rideRequestRepository.findAll();
            assertThat(rideRequests).hasSize(1);
            assertThat(rideRequests.getFirst().getStatus()).isEqualTo(StatusEnum.PENDING);

            assertThat(offerRepository.findByRideRequestIdAndStatus(
                    rideRequests.getFirst().getId(), OfferStatus.NOTIFIED)).hasSize(2);

            assertThat(eventOutboxRepository.findByRideRequestIdAndStatusOrderByCreatedAtAsc(
                    rideRequests.getFirst().getId(), OutboxEventStatus.PENDING))
                    .extracting(EventOutboxEntity::getEventType)
                    .contains(
                            RideRequestEventType.REQUEST_CREATED,
                            RideRequestEventType.REQUEST_STATUS_PENDING,
                            RideRequestEventType.RIDER_NOTIFIED,
                            RideRequestEventType.RIDER_NOTIFIED
                    );
        });

        Message message = rabbitTemplate.receive("queue.user.rider-flow-1", 10_000);

        assertThat(message).isNotNull();
        JsonNode notification = objectMapper.readTree(message.getBody());
        assertThat(notification.get("status").asText()).isEqualTo(StatusEnum.PENDING.name());
        assertThat(notification.get("riderIdentifier").asText()).isEqualTo("rider-flow-1");
        assertThat(notification.get("userIdentifier").asText()).startsWith("user-flow");
    }

    @Test
    void projectsGeneratedDriverIntoMysqlIdempotently() throws Exception {
        byte[] firstEvent = objectMapper.writeValueAsBytes(Map.of(
                "driverId", "driver-generated-101",
                "driverDisplayId", "DRV-GENERATED-101",
                "scenario", "AIRPORT_RUSH"
        ));

        kafkaTemplate.send(driverGeneratedTopic, "driver-generated-101", firstEvent)
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(driverGeneratedTopic, "driver-generated-101", firstEvent)
                .get(10, TimeUnit.SECONDS);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(riderRepository.findAll())
                    .singleElement()
                    .satisfies(rider -> {
                        assertThat(rider.getIdentifier()).isEqualTo("driver-generated-101");
                        assertThat(rider.getDriverIdentifier()).isEqualTo("driver-generated-101");
                        assertThat(rider.getDriverDisplayId()).isEqualTo("DRV-GENERATED-101");
                    });
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

    @TestConfiguration
    static class KafkaProducerTestConfiguration {

        @Bean
        ProducerFactory<String, byte[]> testProducerFactory(
                @Value("${kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class
            ));
        }

        @Bean
        KafkaTemplate<String, byte[]> testKafkaTemplate(ProducerFactory<String, byte[]> testProducerFactory) {
            return new KafkaTemplate<>(testProducerFactory);
        }
    }
}
