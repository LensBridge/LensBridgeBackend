package com.ibrasoft.lensbridge.model.board.commands;

/** Capture the current kiosk frame via CDP {@code Page.captureScreenshot}. PNG, full viewport. */
public record ChromeScreenshotPayload() implements CommandPayload {
    @Override public String kind() { return "chrome.screenshot"; }
}
