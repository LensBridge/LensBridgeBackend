package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Location {
    private String city;
    private String country;
    private double latitude;
    private double longitude;
    private String timezone;
    @Enumerated(EnumType.STRING)
    private CalculationMethod method;
}
