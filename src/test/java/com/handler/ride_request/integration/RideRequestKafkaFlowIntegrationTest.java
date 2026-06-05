package com.handler.ride_request.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.repository.UserRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class RideRequestKafkaFlowIntegrationTest {

    private static final String RIDE_REQUESTS_TOPIC = "ride.requests";
    private static final String VEHICLE_LOCATION = "vehicle_location";

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
    private RideRequestDriverAttemptRepository attemptRepository;

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
        attemptRepository.deleteAll();
        rideRequestRepository.deleteAll();
        riderRepository.deleteAll();
        userRepository.deleteAll();
        stringRedisTemplate.delete(VEHICLE_LOCATION);
    }

    @Test
    void consumesRideRequestFromKafkaPersistsAttemptsAndPublishesRabbitNotification() throws Exception {
        userRepository.save(UserEntity.builder()
                .identifier("user-flow")
                .name("Flow User")
                .build());
        riderRepository.save(rider("rider-flow-1"));
        riderRepository.save(rider("rider-flow-2"));
        stringRedisTemplate.opsForGeo().add(VEHICLE_LOCATION, new Point(2.3522, 48.8566), "rider-flow-1");
        stringRedisTemplate.opsForGeo().add(VEHICLE_LOCATION, new Point(2.3610, 48.8560), "rider-flow-2");

        RideRequest rideRequest = RideRequest.builder()
                .userIdentifier("user-flow")
                .location(Location.builder().latitude(48.8566).longitude(2.3522).build())
                .build();

        kafkaTemplate.send(RIDE_REQUESTS_TOPIC, "user-flow", objectMapper.writeValueAsBytes(rideRequest))
                .get(10, TimeUnit.SECONDS);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<RideRequestEntity> rideRequests = (List<RideRequestEntity>) rideRequestRepository.findAll();
            assertThat(rideRequests).hasSize(1);
            assertThat(rideRequests.getFirst().getStatus()).isEqualTo(StatusEnum.PENDING);

            assertThat(attemptRepository.findByRideRequestIdAndStatus(
                    rideRequests.getFirst().getId(), AttemptStatus.NOTIFIED)).hasSize(2);
        });

        Object message = rabbitTemplate.receiveAndConvert("queue.user.rider-flow-1", 10_000);

        assertThat(message).isInstanceOf(RideNotification.class);
        RideNotification notification = (RideNotification) message;
        assertThat(notification.status()).isEqualTo(StatusEnum.PENDING);
        assertThat(notification.riderIdentifier()).isEqualTo("rider-flow-1");
        assertThat(notification.userIdentifier()).startsWith("user-flow");
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
