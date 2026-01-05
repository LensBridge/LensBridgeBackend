package com.ibrasoft.lensbridge.model.board.frames;

public enum FrameType {
    WEEK_AT_A_GLANCE("week_at_a_glance"),
    DAILY_SCHEDULE("daily_schedule"),
    NEXT_PRAYER("next_prayer"),
    POSTER("poster"),
    ISLAMIC_QUOTES("islamic_quotes");

    private final String typeName;
    FrameType(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }
    

}
