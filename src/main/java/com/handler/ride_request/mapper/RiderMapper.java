package com.handler.ride_request.mapper;

import com.handler.ride_request.domain.Rider;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.redis.connection.RedisGeoCommands;

public class RiderMapper {

    private RiderMapper(){

    }

    public static Rider mapToRider(GeoResult<RedisGeoCommands.GeoLocation<String>> data,
                                   String identifier, String hash){
        return Rider.builder()
                .identifier(identifier)
                .driverIdentifier(identifier)
                .driverDisplayId("DRV-" + identifier.toUpperCase().replaceAll("[^A-Z0-9]+", "-"))
                .averageDistance(data.getDistance().toString())
                .point(data.getContent().getPoint())
                .hash(hash)
                .build();
    }
}
