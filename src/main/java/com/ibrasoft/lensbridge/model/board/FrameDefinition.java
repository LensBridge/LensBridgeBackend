package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "frame_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrameDefinition {
    @Id
    private String id;
    private FrameType type;
    private String duration;
    private UUID posterId;
    private String instagramHandle;
    private BoardLocation boardLocation;
}
