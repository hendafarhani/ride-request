package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RideRequestEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideRequestRepository extends CrudRepository<RideRequestEntity, Long> {

    Optional<RideRequestEntity> findByIdentifier(String identifier);
}
