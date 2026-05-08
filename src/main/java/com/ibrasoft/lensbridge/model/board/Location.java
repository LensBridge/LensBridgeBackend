package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private String city;
    private String country;
    private double latitude;
    private double longitude;
    private String timezone;
    @Enumerated(EnumType.STRING)
    private CalculationMethod method;
}
