package com.ibrasoft.lensbridge.model.board.commands;

import com.ibrasoft.lensbridge.model.auth.Permission;

/**
 * Blast radius of a device command, and the permission required to issue one.
 * <p>
 * These are separately grantable because they are not comparable risks: a reload is a
 * page refresh, a reboot takes a display in a prayer space down for minutes, and a
 * screenshot is a camera pointed at whatever is on that display. Holding one implies
 * nothing about the others
 */
public enum CommandRisk {

    /* Harmless commands, typically configuration-related */
    BENIGN(Permission.BOARD_COMMAND_BENIGN),

    /* Commands which may result in brief downtime */
    DISRUPTIVE(Permission.BOARD_COMMAND_DISRUPTIVE),

    /* Commands - typically harmless - which fetch information about the board state and thus require appropriate auditing 
    */
    INSPECT(Permission.BOARD_COMMAND_INSPECT);

    private final Permission requiredPermission;

    CommandRisk(Permission requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
