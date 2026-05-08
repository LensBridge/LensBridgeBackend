package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayConfig {
    private String city;
    private String timezone;
    private int posterCycleIntervalMs;
    private boolean darkModeAfterIsha;
    private int darkModeAfterIshaMinutes;
    private boolean enableScrollingMessage;
    private List<String> scrollingMessages;
}
