package com.handler.ride_request.rabbitmq.mapper;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.domain.Rider;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RideMapperTest {

    @Test
    void mapToRideNotificationWithRiderShouldMapAllFields() {
        Rider rider = Rider.builder().identifier("rider-1").build();
        RideRequestEntity rideRequest = rideRequest("ride-1", "Alice", new Point(10.0, 20.0));

        RideNotification mapped = RideMapper.mapToRideNotification(rider, rideRequest, StatusEnum.PENDING);

        assertEquals("ride-1", mapped.userIdentifier());
        assertEquals("rider-1", mapped.riderIdentifier());
        assertEquals("Alice", mapped.userName());
        assertSame(rideRequest.getLocation(), mapped.userLocation());
        assertEquals(BigDecimal.ZERO, mapped.price());
        assertEquals(StatusEnum.PENDING, mapped.status());
    }

    @Test
    void mapToRideNotificationWithIdentifierShouldMapAllFields() {
        RideRequestEntity rideRequest = rideRequest("ride-2", "Bob", new Point(15.0, 25.0));

        RideNotification mapped = RideMapper.mapToRideNotification("rider-2", rideRequest, StatusEnum.ACCEPTED);

        assertEquals("ride-2", mapped.userIdentifier());
        assertEquals("rider-2", mapped.riderIdentifier());
        assertEquals("Bob", mapped.userName());
        assertSame(rideRequest.getLocation(), mapped.userLocation());
        assertEquals(BigDecimal.ZERO, mapped.price());
        assertEquals(StatusEnum.ACCEPTED, mapped.status());
    }

    @Test
    void mapToRideNotificationWithRiderShouldThrowWhenRiderIsNull() {
        RideRequestEntity rideRequest = rideRequest("ride-3", "Carol", new Point(1.0, 2.0));

        assertThrows(NullPointerException.class,
                () -> RideMapper.mapToRideNotification((Rider) null, rideRequest, StatusEnum.PENDING));
    }

    @Test
    void mapToRideNotificationShouldThrowWhenRideRequestIsNull() {
        assertThrows(NullPointerException.class,
                () -> RideMapper.mapToRideNotification("rider-3", null, StatusEnum.PENDING));
    }

    @Test
    void mapToRideNotificationShouldThrowWhenUserIsNull() {
        RideRequestEntity rideRequest = RideRequestEntity.builder()
                .identifier("ride-4")
                .user(null)
                .location(new Point(3.0, 4.0))
                .build();

        assertThrows(NullPointerException.class,
                () -> RideMapper.mapToRideNotification("rider-4", rideRequest, StatusEnum.PENDING));
    }

    @Test
    void mapToRideNotificationShouldAllowNullStatusAndLocation() {
        RideRequestEntity rideRequest = rideRequest("ride-5", "Dina", null);

        RideNotification mapped = RideMapper.mapToRideNotification("rider-5", rideRequest, null);

        assertNull(mapped.status());
        assertNull(mapped.userLocation());
        assertEquals(BigDecimal.ZERO, mapped.price());
    }

    @Test
    void mapToRideNotificationShouldAllowNullRiderIdentifierInStringOverload() {
        RideRequestEntity rideRequest = rideRequest("ride-6", "Eve", new Point(7.0, 8.0));

        RideNotification mapped = RideMapper.mapToRideNotification((String) null, rideRequest, StatusEnum.CANCELED);

        assertNull(mapped.riderIdentifier());
        assertEquals("ride-6", mapped.userIdentifier());
        assertEquals("Eve", mapped.userName());
    }

    private RideRequestEntity rideRequest(String rideIdentifier, String userName, Point location) {
        return RideRequestEntity.builder()
                .identifier(rideIdentifier)
                .user(UserEntity.builder().name(userName).identifier("user-id").build())
                .location(location)
                .build();
    }
}

