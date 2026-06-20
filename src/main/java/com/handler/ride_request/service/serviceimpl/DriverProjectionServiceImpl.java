package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.service.DriverProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverProjectionServiceImpl implements DriverProjectionService {

    private final RiderRepository riderRepository;

    @Override
    @Transactional
    public void upsertDriver(DriverGeneratedEvent event) {
        String driverIdentifier = requireDriverIdentifier(event);
        RiderEntity rider = riderRepository.findByDriverIdentifier(driverIdentifier)
                .orElseGet(() -> RiderEntity.builder()
                        .identifier(driverIdentifier)
                        .driverIdentifier(driverIdentifier)
                        .build());

        rider.setDriverIdentifier(driverIdentifier);
        rider.setDriverDisplayId(resolveDisplayId(
                driverIdentifier,
                event.getDriverDisplayId(),
                rider.getDriverDisplayId()));
        riderRepository.save(rider);
    }

    private static String requireDriverIdentifier(DriverGeneratedEvent event) {
        if (event == null || event.getDriverId() == null || event.getDriverId().isBlank()) {
            throw new IllegalArgumentException("DriverGeneratedEvent.driverId must not be blank");
        }
        return event.getDriverId();
    }

    private static String resolveDisplayId(
            String driverIdentifier,
            String requestedDisplayId,
            String existingDisplayId) {
        if (requestedDisplayId != null && !requestedDisplayId.isBlank()) {
            return requestedDisplayId;
        }
        if (existingDisplayId != null && !existingDisplayId.isBlank()) {
            return existingDisplayId;
        }
        return "DRV-" + driverIdentifier.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
    }
}
