package com.ibrasoft.lensbridge.model.board;

public enum BoardLocation {
    BROTHERS_MUSALLAH("brothers"),
    SISTERS_MUSALLAH("sisters");

    private final String locationName;
    BoardLocation(String locationName) {
        this.locationName = locationName;
    }

    @Override
    public String toString() {
        return locationName;
    }
}
