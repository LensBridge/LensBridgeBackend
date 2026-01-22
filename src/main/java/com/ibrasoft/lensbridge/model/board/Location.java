package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class Location {
    private String city;
    private String country;
    private double latitude;
    private double longitude;
    private String timezone;
    private int method;
}
