package com.handler.ride_request.service.impl;

import com.handler.ride_request.model.Rider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RidersSearchServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @InjectMocks
    private RidersSearchServiceImpl service;

    private Point location;

    @BeforeEach
    void setUp() {
        location = new Point(10.0, 20.0);
    }

    @Test
    void shouldThrowWhenLocationIsNull() {
        assertThatThrownBy(() -> service.findNearestVehicles(null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(geoOperations);
    }

    @Test
    void shouldReturnEmptyListWhenRedisResponseIsNull() {
        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(null);

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders).isEmpty();
        verify(geoOperations, never()).hash(anyString(), anyString());
    }

    @Test
    void shouldReturnEmptyListWhenRedisResponseHasNoEntries() {
        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);

        when(geoOperations.radius(anyString(), any(Circle.class), any()))
                .thenReturn(new GeoResults<>(List.of()));

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders).isEmpty();
    }

    @Test
    void shouldFilterOutExcludedIdentifiers() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("keep-me", new Point(11, 21), 1),
                geoResult("skip-me", new Point(12, 22), 2)
        ));

        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(results);

        List<Rider> riders = service.findNearestVehicles(location, Set.of("skip-me"));

        assertThat(riders).hasSize(1);
        assertThat(riders.getFirst().getIdentifier()).isEqualTo("keep-me");
    }

    @Test
    void shouldFallbackToEmptyHashWhenRedisHashIsMissing() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("no-hash", new Point(11, 21), 1)
        ));

        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(results);
        when(geoOperations.hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "no-hash"))
                .thenReturn(null);

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders).singleElement().satisfies(rider -> {
            assertThat(rider.getIdentifier()).isEqualTo("no-hash");
            assertThat(rider.getHash()).isEmpty();
        });
    }

    @Test
    void shouldLimitResultsToMaximumConfiguredRiders() {
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String identifier = "rider-" + i;
            geoResults.add(geoResult(identifier, new Point(10 + i, 20 + i), i + 1));
        }

        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any()))
                .thenReturn(new GeoResults<>(geoResults));

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders)
                .hasSize(RidersSearchServiceImpl.MAX_NUMBER_RIDERS)
                .extracting(Rider::getIdentifier)
                .containsExactly("rider-0", "rider-1", "rider-2", "rider-3", "rider-4");
    }

    private GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult(String identifier, Point point, double distanceKm) {
        RedisGeoCommands.GeoLocation<String> location = new RedisGeoCommands.GeoLocation<>(identifier, point);
        return new GeoResult<>(location, new Distance(distanceKm, Metrics.KILOMETERS));
    }
}

