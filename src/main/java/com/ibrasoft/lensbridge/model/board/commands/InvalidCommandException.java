package com.ibrasoft.lensbridge.model.board.commands;

/**
 * A command request its kind does not accept: a payload of the wrong shape, or a time-to-live
 * longer than the kind allows. The message is written for the admin who issued it, and is
 * returned as the 400's message.
 */
public class InvalidCommandException extends IllegalArgumentException {

    public InvalidCommandException(String message) {
        super(message);
    }
}
