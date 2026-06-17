package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.mapper.RiderMapper;
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

@RequiredArgsConstructor
@Service
public class RidersSearchServiceImpl implements RidersSearchService {

    private final StringRedisTemplate stringRedisTemplate;

    public static final String VEHICLE_LOCATION = "vehicle_location";
    public static final int MAX_NUMBER_RIDERS = 5;
    public static final int DISTANCE = 10;

    public List<Rider> findNearestVehicles(Point location, Set<String> excludedIdentifiers) {
        validateLocation(location);

        GeoResults<RedisGeoCommands.GeoLocation<String>> nearbyVehicles = findNearbyVehicleLocations(location);
        if (hasNoNearbyVehicles(nearbyVehicles)) {
            return List.of();
        }

        return mapAllowedVehicleLocationsToRiders(nearbyVehicles, excludedIdentifiers);
    }

    private void validateLocation(Point location) {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
    }

    private GeoResults<RedisGeoCommands.GeoLocation<String>> findNearbyVehicleLocations(Point location) {
        return stringRedisTemplate.opsForGeo()
                .radius(VEHICLE_LOCATION, radiusAround(location), geoRadiusCommandArgs());
    }

    private Circle radiusAround(Point location) {
        return new Circle(location, searchDistance());
    }

    private Distance searchDistance() {
        return new Distance(DISTANCE, Metrics.KILOMETERS);
    }

    private RedisGeoCommands.GeoRadiusCommandArgs geoRadiusCommandArgs() {
        return RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending();
    }

    private boolean hasNoNearbyVehicles(GeoResults<RedisGeoCommands.GeoLocation<String>> nearbyVehicles) {
        return Objects.isNull(nearbyVehicles) || nearbyVehicles.getContent().isEmpty();
    }

    private List<Rider> mapAllowedVehicleLocationsToRiders(
            GeoResults<RedisGeoCommands.GeoLocation<String>> nearbyVehicles,
            Set<String> excludedIdentifiers) {
        return nearbyVehicles.getContent().stream()
                .filter(vehicleLocation -> isAllowedCandidate(vehicleLocation, excludedIdentifiers))
                .limit(MAX_NUMBER_RIDERS)
                .map(this::mapToRider)
                .toList();
    }

    private boolean isAllowedCandidate(
            GeoResult<RedisGeoCommands.GeoLocation<String>> vehicleLocation,
            Set<String> excludedIdentifiers) {
        return hasNoExcludedIdentifiers(excludedIdentifiers) || isNotExcluded(vehicleLocation, excludedIdentifiers);
    }

    private boolean hasNoExcludedIdentifiers(Set<String> excludedIdentifiers) {
        return excludedIdentifiers == null || excludedIdentifiers.isEmpty();
    }

    private boolean isNotExcluded(
            GeoResult<RedisGeoCommands.GeoLocation<String>> vehicleLocation,
            Set<String> excludedIdentifiers) {
        return !excludedIdentifiers.contains(extractIdentifier(vehicleLocation));
    }

    private Rider mapToRider(GeoResult<RedisGeoCommands.GeoLocation<String>> data) {
        String identifier = extractIdentifier(data);
        String hash = resolveHash(identifier);
        return RiderMapper.mapToRider(data, identifier, hash);
    }

    private String extractIdentifier(GeoResult<RedisGeoCommands.GeoLocation<String>> vehicleLocation) {
        return vehicleLocation.getContent().getName();
    }

    private String resolveHash(String identifier) {
        return Optional.ofNullable(stringRedisTemplate.opsForGeo().hash(VEHICLE_LOCATION, identifier))
                .stream()
                .flatMap(List::stream)
                .findFirst()
                .orElse(StringUtil.EMPTY_STRING);
    }
}
