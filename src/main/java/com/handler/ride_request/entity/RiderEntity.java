package com.handler.ride_request.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "driver")
public class RiderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "identifier", unique = true)
    private String identifier;

    @Column(name = "driver_identifier", unique = true)
    private String driverIdentifier;

    @Column(name = "driver_display_id", unique = true)
    private String driverDisplayId;

    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @PrePersist
    @PreUpdate
    void ensureDriverIdentityFields() {
        if (driverIdentifier == null || driverIdentifier.isBlank()) {
            driverIdentifier = identifier;
        }
        if ((driverDisplayId == null || driverDisplayId.isBlank()) && driverIdentifier != null) {
            driverDisplayId = "DRV-" + driverIdentifier.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        }
    }

    public String effectiveDriverIdentifier() {
        return driverIdentifier != null ? driverIdentifier : identifier;
    }

}
