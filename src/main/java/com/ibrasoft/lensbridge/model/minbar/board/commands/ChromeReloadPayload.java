package com.ibrasoft.lensbridge.model.minbar.board.commands;

/** Force-reload the kiosk Chrome page via CDP {@code Page.reload(ignoreCache:true)}. */
public record ChromeReloadPayload() implements CommandPayload {
    @Override public String kind() { return "chrome.reload"; }
}
