package com.ibrasoft.lensbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class MusallahBoardConfig {

    /**
     * Zone used for a board whose device config has no timezone, or an unparseable one.
     * Designed to be decoupled from the JVM's default timezone to avoid issues with container images 
     * that don't set it.
     */
    @Bean
    public ZoneId boardDefaultZone(
            @Value("${musallahboard.defaultTimezone:America/Toronto}") String timezone) {
        return ZoneId.of(timezone);
    }
}
