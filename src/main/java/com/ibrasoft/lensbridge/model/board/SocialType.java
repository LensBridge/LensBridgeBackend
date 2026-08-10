package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

public enum SocialType {
    INSTAGRAM("instagram"),
    YOUTUBE("youtube"),
    TIKTOK("tiktok"),
    WHATSAPP("whatsapp"),;

    private final String label;

    SocialType(String label) {
        this.label = label;
    }
}