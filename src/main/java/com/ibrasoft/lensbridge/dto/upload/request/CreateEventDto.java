package com.ibrasoft.lensbridge.dto.upload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateEventDto {
    @NotBlank(message = "Event name is required")
    private String name;

    @NotNull(message = "Event date is required")
    private Instant date;

}
