package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import com.handler.ride_request.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverProjectionServiceImplTest {

    private RiderRepository riderRepository;
    private DriverProjectionServiceImpl service;

    @BeforeEach
    void setUp() {
        riderRepository = mock(RiderRepository.class);
        service = new DriverProjectionServiceImpl(riderRepository);
    }

    @Test
    void createsDispatchProjectionForNewDriver() {
        when(riderRepository.findByDriverIdentifier("driver-101")).thenReturn(Optional.empty());
        DriverGeneratedEvent event = DriverGeneratedEvent.builder()
                .driverId("driver-101")
                .driverDisplayId("DRV-LONDON-101")
                .scenario("AIRPORT_RUSH")
                .build();

        service.upsertDriver(event);

        verify(riderRepository).save(org.mockito.ArgumentMatchers.argThat(rider ->
                "driver-101".equals(rider.getIdentifier())
                        && "driver-101".equals(rider.getDriverIdentifier())
                        && "DRV-LONDON-101".equals(rider.getDriverDisplayId())));
    }

    @Test
    void updatesExistingProjectionWithoutCreatingDuplicate() {
        RiderEntity existing = RiderEntity.builder()
                .id(42L)
                .identifier("driver-101")
                .driverIdentifier("driver-101")
                .driverDisplayId("DRV-OLD")
                .build();
        when(riderRepository.findByDriverIdentifier("driver-101")).thenReturn(Optional.of(existing));

        service.upsertDriver(DriverGeneratedEvent.builder()
                .driverId("driver-101")
                .driverDisplayId("DRV-LONDON-101")
                .build());

        assertThat(existing.getId()).isEqualTo(42L);
        assertThat(existing.getDriverDisplayId()).isEqualTo("DRV-LONDON-101");
        verify(riderRepository).save(existing);
    }

    @Test
    void derivesDisplayIdWhenProducerDoesNotProvideOne() {
        when(riderRepository.findByDriverIdentifier("driver london 101")).thenReturn(Optional.empty());

        service.upsertDriver(DriverGeneratedEvent.builder()
                .driverId("driver london 101")
                .build());

        verify(riderRepository).save(org.mockito.ArgumentMatchers.argThat(rider ->
                "DRV-DRIVER-LONDON-101".equals(rider.getDriverDisplayId())));
    }

    @Test
    void preservesExistingDisplayIdWhenOlderProducerOmitsIt() {
        RiderEntity existing = RiderEntity.builder()
                .identifier("driver-101")
                .driverIdentifier("driver-101")
                .driverDisplayId("DRV-CUSTOM-101")
                .build();
        when(riderRepository.findByDriverIdentifier("driver-101")).thenReturn(Optional.of(existing));

        service.upsertDriver(DriverGeneratedEvent.builder()
                .driverId("driver-101")
                .build());

        assertThat(existing.getDriverDisplayId()).isEqualTo("DRV-CUSTOM-101");
        verify(riderRepository).save(existing);
    }

    @Test
    void rejectsBlankDriverIdentifier() {
        DriverGeneratedEvent event = DriverGeneratedEvent.builder().driverId(" ").build();

        assertThatThrownBy(() -> service.upsertDriver(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driverId");
    }
}
