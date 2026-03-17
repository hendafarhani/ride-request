package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RiderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {

    Optional<RiderEntity> findByIdentifier(String identifier);

    List<RiderEntity> findByIdentifierIn(Collection<String> identifiers);
}
