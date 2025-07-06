package com.rider.tracker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiderData implements Serializable {
    private String identifier;
    private String name;
    private double latitude;
    private double longitude;
}


