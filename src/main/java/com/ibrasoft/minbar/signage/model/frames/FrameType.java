package com.ibrasoft.minbar.signage.model.frames;

public enum FrameType {
    POSTER("poster"),
    EVENT_LIST("event_list"),
    DAILY_SCHEDULE("daily_schedule"),
    NEXT_PRAYER("next_prayer"),
    JUMMAH("jummah"),
    ISLAMIC_QUOTE("islamic_quote");

    private final String typeName;
    FrameType(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }
}
