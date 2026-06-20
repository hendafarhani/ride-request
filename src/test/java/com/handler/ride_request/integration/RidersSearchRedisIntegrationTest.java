package com.handler.ride_request.integration;

import com.handler.ride_request.domain.Rider;
import com.handler.ride_request.service.serviceimpl.RidersSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class RidersSearchRedisIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RidersSearchServiceImpl service;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void cleanRedis() {
        stringRedisTemplate.delete(RidersSearchServiceImpl.VEHICLE_LOCATION);
    }

    @Test
    void returnsNearestRiderFromRedis() {
        addRider("nearest-rider", 2.3522, 48.8566);
        addRider("farther-rider", 2.2950, 48.8738);

        List<Rider> riders = service.findNearestVehicles(new Point(2.3520, 48.8560), Set.of());

        assertThat(riders)
                .extracting(Rider::getIdentifier)
                .containsExactly("nearest-rider", "farther-rider");
        assertThat(riders.getFirst().getPoint().getX()).isCloseTo(2.3522, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(riders.getFirst().getPoint().getY()).isCloseTo(48.8566, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(riders.getFirst().getHash()).isNotBlank();
    }

    @Test
    void returnsEmptyListWhenRedisHasNoNearbyRiders() {
        addRider("too-far-rider", 2.3522, 48.8566);

        List<Rider> riders = service.findNearestVehicles(new Point(13.4050, 52.5200), Set.of());

        assertThat(riders).isEmpty();
    }

    @Test
    void excludesAlreadyOfferedRiderIdentifiers() {
        addRider("offered-rider", 2.3522, 48.8566);
        addRider("available-rider", 2.3600, 48.8600);

        List<Rider> riders = service.findNearestVehicles(
                new Point(2.3520, 48.8560),
                Set.of("offered-rider"));

        assertThat(riders)
                .extracting(Rider::getIdentifier)
                .containsExactly("available-rider");
    }

    @Test
    void rejectsNullLocation() {
        assertThatThrownBy(() -> service.findNearestVehicles(null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("location must not be null");
    }

    private void addRider(String identifier, double longitude, double latitude) {
        stringRedisTemplate.opsForGeo()
                .add(RidersSearchServiceImpl.VEHICLE_LOCATION, new Point(longitude, latitude), identifier);
    }
}
