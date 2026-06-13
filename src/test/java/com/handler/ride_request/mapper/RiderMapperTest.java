package com.handler.ride_request.mapper;

import com.handler.ride_request.model.Rider;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;

import static org.assertj.core.api.Assertions.assertThat;

class RiderMapperTest {

    @Test
    void mapToRiderShouldMapGeoResultIdentifierHashDistanceAndPoint() {
        Point point = new Point(10.5, 20.25);
        Distance distance = new Distance(3.75, Metrics.KILOMETERS);
        GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult = geoResult("redis-rider-name", point, distance);

        Rider rider = RiderMapper.mapToRider(geoResult, "rider-123", "hash-123");

        assertThat(rider.getIdentifier()).isEqualTo("rider-123");
        assertThat(rider.getHash()).isEqualTo("hash-123");
        assertThat(rider.getAverageDistance()).isEqualTo(distance.toString());
        assertThat(rider.getPoint()).isEqualTo(point);
        assertThat(rider.getUserName()).isNull();
    }

    @Test
    void mapToRiderShouldPreserveNullHash() {
        Point point = new Point(11.0, 21.0);
        Distance distance = new Distance(1.0, Metrics.KILOMETERS);

        Rider rider = RiderMapper.mapToRider(geoResult("rider-1", point, distance), "rider-1", null);

        assertThat(rider.getHash()).isNull();
        assertThat(rider.getIdentifier()).isEqualTo("rider-1");
        assertThat(rider.getAverageDistance()).isEqualTo(distance.toString());
        assertThat(rider.getPoint()).isEqualTo(point);
    }

    private GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult(String identifier, Point point, Distance distance) {
        RedisGeoCommands.GeoLocation<String> location = new RedisGeoCommands.GeoLocation<>(identifier, point);
        return new GeoResult<>(location, distance);
    }
}
