package com.pokerassistant.error;

/**
 * Base type for every expected, user-facing failure. The CLI prints the message of these exceptions
 * and keeps the session alive; any other exception is treated as a bug.
 */
public abstract class PokerAssistantException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected PokerAssistantException(String message) {
        super(message);
    }

    protected PokerAssistantException(String message, Throwable cause) {
        super(message, cause);
    }
}
