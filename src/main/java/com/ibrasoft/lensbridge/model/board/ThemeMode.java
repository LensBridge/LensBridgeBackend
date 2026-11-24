package com.ibrasoft.lensbridge.model.board;

/**
 * How a board picks between its light and dark appearance.
 * <p>
 * {@link #AUTO} leaves it to the board's clock: dark from Maghrib (sunset) until sunrise, light
 * the rest of the day. The board computes both times itself, so the switch happens on time even
 * on a board that has been offline for weeks.
 * <p>
 * This is the appearance, not the theme. A named theme (custom colourways) will sit on top of
 * it later; each theme then supplies its own light and dark variant, and this enum keeps
 * choosing between them.
 */
public enum ThemeMode {
    /** Always the light appearance. */
    LIGHT,
    /** Always the dark appearance. */
    DARK,
    /** Dark from Maghrib until sunrise, light otherwise. */
    AUTO
}
