package com.handler.ride_request.mapper;

import com.handler.ride_request.model.Rider;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.redis.connection.RedisGeoCommands;

public class RiderMapper {

    private RiderMapper(){

    }

    public static Rider mapToRider(GeoResult<RedisGeoCommands.GeoLocation<String>> data,
                                   String identifier, String hash){
        return Rider.builder()
                .identifier(identifier)
                .averageDistance(data.getDistance().toString())
                .point(data.getContent().getPoint())
                .hash(hash)
                .build();
    }
}
