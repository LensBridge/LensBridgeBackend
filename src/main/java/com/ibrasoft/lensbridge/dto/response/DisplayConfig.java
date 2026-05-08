package com.ibrasoft.lensbridge.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DisplayConfig {
    private int posterCycleIntervalMs;
    private int refreshAfterIshaMinutes;
    private boolean darkModeAfterIsha;
    private int darkModeAfterIshaMinutes;
    private boolean enableScrollingMessage;
    private java.util.List<String> scrollingMessages;
}
