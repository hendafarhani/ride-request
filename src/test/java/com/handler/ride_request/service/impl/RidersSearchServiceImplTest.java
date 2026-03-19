package com.handler.ride_request.service.impl;

import com.handler.ride_request.model.Rider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RidersSearchServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @InjectMocks
    private RidersSearchServiceImpl service;

    private Point riderLocation;

    @BeforeEach
    void setUp() {
        riderLocation = new Point(10.0, 20.0);
        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
    }

    @Test
    void shouldReturnEmptyListWhenRedisRadiusReturnsNull() {
        stubRadiusResponse(null);

        List<Rider> result = service.findNearestVehicles(riderLocation, Set.of());

        assertThat(result).isEmpty();
        verify(geoOperations, never()).hash(anyString(), any());
    }

    @Test
    void shouldReturnEmptyListWhenRedisRadiusReturnsNoResults() {
        stubRadiusResponse(new GeoResults<>(List.of()));

        List<Rider> result = service.findNearestVehicles(riderLocation, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExcludeIdentifiersPresentInBlacklist() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("keep-me", 1),
                geoResult("skip-me", 2)
        ));
        stubRadiusResponse(results);
        List<Rider> riders = service.findNearestVehicles(riderLocation, Set.of("skip-me"));

        assertThat(riders).extracting(Rider::getIdentifier).containsExactly("keep-me");
    }

    @Test
    void shouldKeepAllRidersWhenBlacklistIsNull() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("rider-1", 1),
                geoResult("rider-2", 2)
        ));
        stubRadiusResponse(results);
        when(geoOperations.hash(anyString(), any())).thenReturn(List.of("hash"));

        List<Rider> riders = service.findNearestVehicles(riderLocation, null);

        assertThat(riders).extracting(Rider::getIdentifier).containsExactly("rider-1", "rider-2");
    }

    @Test
    void shouldFallbackToEmptyHashWhenRedisHashIsMissing() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(geoResult("no-hash", 1)));
        stubRadiusResponse(results);
        when(geoOperations.hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "no-hash"))
                .thenReturn(null);

        List<Rider> riders = service.findNearestVehicles(riderLocation, Set.of());

        assertThat(riders).singleElement().satisfies(rider -> {
            assertThat(rider.getIdentifier()).isEqualTo("no-hash");
            assertThat(rider.getHash()).isEmpty();
        });
    }

    @Test
    void shouldLimitResultToMaximumConfiguredRiders() {
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String riderId = "rider-" + i;
            geoResults.add(geoResult(riderId, i + 1));
        }
        stubRadiusResponse(new GeoResults<>(geoResults));

        List<Rider> riders = service.findNearestVehicles(riderLocation, Set.of());

        assertThat(riders)
                .hasSize(RidersSearchServiceImpl.MAX_NUMBER_RIDERS)
                .extracting(Rider::getIdentifier)
                .containsExactly("rider-0", "rider-1", "rider-2", "rider-3", "rider-4");
    }

    private void stubRadiusResponse(GeoResults<RedisGeoCommands.GeoLocation<String>> results) {
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(results);
    }

    private GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult(String identifier, double distanceInKm) {
        RedisGeoCommands.GeoLocation<String> location = new RedisGeoCommands.GeoLocation<>(identifier,
                new Point(10.0 + distanceInKm, 20.0 + distanceInKm));
        return new GeoResult<>(location, new Distance(distanceInKm, Metrics.KILOMETERS));
    }
}
