package com.handler.ride_request.mapper;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideRequestMapperTest {

    @Test
    void mapToRideRequestEntityShouldMapUserStatusAndLocationAndGenerateIdentifier() {
        UserEntity user = UserEntity.builder().id(1L).identifier("user-1").name("John").build();
        RideRequest rideRequest = RideRequest.builder()
                .userIdentifier("request-user-")
                .location(Location.builder().latitude(33.3).longitude(44.4).build())
                .build();

        RideRequestEntity mapped = RideRequestMapper.mapToRideRequestEntity(user, rideRequest, StatusEnum.PENDING);

        assertSame(user, mapped.getUser());
        assertEquals(StatusEnum.PENDING, mapped.getStatus());
        assertEquals(44.4, mapped.getLocation().getX());
        assertEquals(33.3, mapped.getLocation().getY());

        String prefix = rideRequest.userIdentifier();
        String generatedIdentifier = mapped.getIdentifier();
        assertTrue(generatedIdentifier.startsWith(prefix));
        UUID parsedUuid = UUID.fromString(generatedIdentifier.substring(prefix.length()));
        assertNotNull(parsedUuid);
    }

    @Test
    void mapToRideRequestEntityShouldGenerateDifferentIdentifierEachTime() {
        UserEntity user = UserEntity.builder().id(1L).identifier("user-1").name("John").build();
        RideRequest rideRequest = RideRequest.builder()
                .userIdentifier("request-user-")
                .location(Location.builder().latitude(1.0).longitude(2.0).build())
                .build();

        RideRequestEntity first = RideRequestMapper.mapToRideRequestEntity(user, rideRequest, StatusEnum.PENDING);
        RideRequestEntity second = RideRequestMapper.mapToRideRequestEntity(user, rideRequest, StatusEnum.PENDING);

        assertNotEquals(first.getIdentifier(), second.getIdentifier());
    }

    @Test
    void mapToRideRequestEntityShouldThrowWhenRideRequestIsNull() throws NoSuchMethodException {
        UserEntity user = UserEntity.builder().id(1L).identifier("user-1").name("John").build();
        Method mapperMethod = RideRequestMapper.class.getMethod(
                "mapToRideRequestEntity",
                UserEntity.class,
                RideRequest.class,
                StatusEnum.class
        );

        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> mapperMethod.invoke(null, user, null, StatusEnum.PENDING)
        );

        assertInstanceOf(NullPointerException.class, thrown.getCause());
    }

    @Test
    void mapToRideRequestEntityShouldThrowWhenRideRequestLocationIsNull() {
        UserEntity user = UserEntity.builder().id(1L).identifier("user-1").name("John").build();
        RideRequest rideRequest = RideRequest.builder().userIdentifier("request-user-").location(null).build();

        assertThrows(NullPointerException.class,
                () -> RideRequestMapper.mapToRideRequestEntity(user, rideRequest, StatusEnum.PENDING));
    }

    @Test
    void mapToRideRequestEntityShouldAllowNullUserAndStatus() {
        RideRequest rideRequest = RideRequest.builder()
                .userIdentifier("request-user-")
                .location(Location.builder().latitude(10.0).longitude(20.0).build())
                .build();

        RideRequestEntity mapped = RideRequestMapper.mapToRideRequestEntity(null, rideRequest, null);

        assertNull(mapped.getUser());
        assertNull(mapped.getStatus());
        assertEquals(20.0, mapped.getLocation().getX());
        assertEquals(10.0, mapped.getLocation().getY());
    }
}
