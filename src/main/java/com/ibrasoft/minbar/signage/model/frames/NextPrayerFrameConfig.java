package com.ibrasoft.minbar.signage.model.frames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NextPrayerFrameConfig extends FrameConfig {

    private String locationCity;
    private String timezone;
    private String calculationMethod;
}
