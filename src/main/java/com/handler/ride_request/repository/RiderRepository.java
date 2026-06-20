package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RiderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {

    @Query("""
            select rider
            from RiderEntity rider
            where coalesce(rider.driverIdentifier, rider.identifier) = :driverIdentifier
            """)
    Optional<RiderEntity> findByDriverIdentifier(@Param("driverIdentifier") String driverIdentifier);

    Optional<RiderEntity> findByIdentifier(String identifier);

    @Query("""
            select rider
            from RiderEntity rider
            where coalesce(rider.driverIdentifier, rider.identifier) in :driverIdentifiers
            """)
    List<RiderEntity> findByDriverIdentifierIn(@Param("driverIdentifiers") Collection<String> driverIdentifiers);

    List<RiderEntity> findByIdentifierIn(Collection<String> identifiers);
}
