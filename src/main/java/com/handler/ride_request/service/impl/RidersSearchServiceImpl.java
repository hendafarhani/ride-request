package com.handler.ride_request.service.impl;

import com.handler.ride_request.model.Rider;
import com.handler.ride_request.service.RidersSearchService;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RidersSearchServiceImpl implements RidersSearchService {

    private final GeoOperations<String, String> geoOperations;

    public static final String VEHICLE_LOCATION = "vehicle_location";
    public static final int MAX_NUMBER_RIDERS = 5;
    public static final int DISTANCE = 10;

    public List<Rider> findNearestVehicles(Point location) {

        RedisGeoCommands.GeoRadiusCommandArgs args = getGeoRaduisCommandArgs();
        Circle circle = getCircle(location);

        GeoResults<RedisGeoCommands.GeoLocation<String>> response = geoOperations.radius(VEHICLE_LOCATION, circle, args);

        if(isResponseEmpty(response)) return List.of();

        return response.getContent().stream()
                .map(this::getRider)
                .collect(Collectors.toList());
    }

    private RedisGeoCommands.GeoRadiusCommandArgs getGeoRaduisCommandArgs(){
        return RedisGeoCommands
                .GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending().limit(MAX_NUMBER_RIDERS);
    }

    private Circle getCircle(Point location){
        return new Circle(location, new Distance(RidersSearchServiceImpl.DISTANCE, Metrics.KILOMETERS));
    }


    private Rider getRider(GeoResult<RedisGeoCommands.GeoLocation<String>> data){
        String identifier = data.getContent().getName();
        String hash = Optional.ofNullable(geoOperations.hash(VEHICLE_LOCATION, identifier))
                .stream()
                .flatMap(List::stream)
                .findFirst()
                .orElse(StringUtil.EMPTY_STRING);

        return Rider.builder()
                .identifier(identifier)
                .averageDistance(data.getDistance().toString())
                .point(data.getContent().getPoint())
                .hash(hash)
                .build();
    }

    private boolean isResponseEmpty(GeoResults<RedisGeoCommands.GeoLocation<String>> response){
        return Objects.isNull(response) || response.getContent().isEmpty();
    }
}
