package com.handler.ride_request.service.impl;

import com.handler.ride_request.model.Rider;
import com.handler.ride_request.service.RidersSearchService;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RidersSearchServiceImpl implements RidersSearchService {

    private final StringRedisTemplate stringRedisTemplate;

    public static final String VEHICLE_LOCATION = "vehicle_location";
    public static final int MAX_NUMBER_RIDERS = 5;
    public static final int DISTANCE = 10;

    public List<Rider> findNearestVehicles(Point location, Set<String> excludedIdentifiers) {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }

        GeoResults<RedisGeoCommands.GeoLocation<String>> response = queryNearbyVehicles(location);
        if (isResponseEmpty(response)) {
            return List.of();
        }

        return response.getContent().stream()
                .filter(result -> isAllowed(result, excludedIdentifiers))
                .map(this::mapToRider)
                .limit(MAX_NUMBER_RIDERS)
                .collect(Collectors.toList());
    }

    private GeoResults<RedisGeoCommands.GeoLocation<String>> queryNearbyVehicles(Point location) {
        return stringRedisTemplate.opsForGeo()
                .radius(VEHICLE_LOCATION, new Circle(location, new Distance(DISTANCE, Metrics.KILOMETERS)), geoRadiusCommandArgs());
    }

    private RedisGeoCommands.GeoRadiusCommandArgs geoRadiusCommandArgs() {
        return RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending();
    }

    private Rider mapToRider(GeoResult<RedisGeoCommands.GeoLocation<String>> data) {
        String identifier = data.getContent().getName();
        String hash = resolveHash(identifier);

        return Rider.builder()
                .identifier(identifier)
                .averageDistance(data.getDistance().toString())
                .point(data.getContent().getPoint())
                .hash(hash)
                .build();
    }

    private String resolveHash(String identifier) {
        return Optional.ofNullable(stringRedisTemplate.opsForGeo().hash(VEHICLE_LOCATION, identifier))
                .stream()
                .flatMap(List::stream)
                .findFirst()
                .orElse(StringUtil.EMPTY_STRING);
    }

    private boolean isResponseEmpty(GeoResults<RedisGeoCommands.GeoLocation<String>> response) {
        return Objects.isNull(response) || response.getContent().isEmpty();
    }

    private boolean isAllowed(GeoResult<RedisGeoCommands.GeoLocation<String>> data, Set<String> excludedIdentifiers) {
        if (excludedIdentifiers == null || excludedIdentifiers.isEmpty()) {
            return true;
        }
        return !excludedIdentifiers.contains(data.getContent().getName());
    }
}
