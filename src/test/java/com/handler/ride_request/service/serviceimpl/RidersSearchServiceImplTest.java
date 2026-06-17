package com.handler.ride_request.service.serviceimpl;

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
        Set<String> excludedIdentifiers = Set.of();

        assertThatThrownBy(() -> service.findNearestVehicles(null, excludedIdentifiers))
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
        verify(geoOperations).hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "keep-me");
        verify(geoOperations, never()).hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "skip-me");
    }

    @Test
    void shouldMapRedisResultsToRidersWithResolvedHashDistanceAndPoint() {
        Point riderPoint = new Point(11, 21);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("rider-1", riderPoint, 1.5)
        ));

        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(results);
        when(geoOperations.hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "rider-1"))
                .thenReturn(List.of("hash-1"));

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders).singleElement().satisfies(rider -> {
            assertThat(rider.getIdentifier()).isEqualTo("rider-1");
            assertThat(rider.getHash()).isEqualTo("hash-1");
            assertThat(rider.getAverageDistance()).isEqualTo(new Distance(1.5, Metrics.KILOMETERS).toString());
            assertThat(rider.getPoint()).isEqualTo(riderPoint);
        });
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
    void shouldFallbackToEmptyHashWhenRedisHashListIsEmpty() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                geoResult("empty-hash", new Point(11, 21), 1)
        ));

        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(Circle.class), any())).thenReturn(results);
        when(geoOperations.hash(RidersSearchServiceImpl.VEHICLE_LOCATION, "empty-hash"))
                .thenReturn(List.of());

        List<Rider> riders = service.findNearestVehicles(location, Set.of());

        assertThat(riders).singleElement().satisfies(rider -> {
            assertThat(rider.getIdentifier()).isEqualTo("empty-hash");
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
        RedisGeoCommands.GeoLocation<String> geoResult = new RedisGeoCommands.GeoLocation<>(identifier, point);
        return new GeoResult<>(geoResult, new Distance(distanceKm, Metrics.KILOMETERS));
    }
}
