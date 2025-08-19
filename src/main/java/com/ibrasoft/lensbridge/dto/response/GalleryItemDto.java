package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalleryItemDto {
    private String id;
    private String type;
    private String src;
    private String thumbnail;
    private String title;
    private String author;
    private String date;
    private String event;
    private boolean featured;
}
