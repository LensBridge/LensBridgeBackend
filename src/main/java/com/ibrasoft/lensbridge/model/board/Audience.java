package com.ibrasoft.lensbridge.model.board;

public enum Audience {
    BROTHERS("brothers"),
    SISTERS("sisters"),
    BOTH("both");

    private final String audienceName;
    Audience(String audienceName) {
        this.audienceName = audienceName;
    }

    @Override
    public String toString() {
        return audienceName;
    }
}
