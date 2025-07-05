package com.ibrasoft.lensbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalleryResponseDto {
    private List<GalleryItemDto> items = new ArrayList<>();
}
