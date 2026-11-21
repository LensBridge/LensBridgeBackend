package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Location {
    private String city;
    private String country;
    /**
     * Boxed so "absent" is distinguishable from 0.0: a PATCH that sends only the timezone must
     * leave the stored coordinates alone, not zero them (which silently moves every prayer
     * time on the board to the Gulf of Guinea). The columns were always nullable.
     */
    private Double latitude;
    private Double longitude;
    private String timezone;
    @Enumerated(EnumType.STRING)
    private CalculationMethod method;
}
