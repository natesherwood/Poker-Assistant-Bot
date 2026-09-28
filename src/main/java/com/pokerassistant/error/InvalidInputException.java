package com.pokerassistant.error;

/** Malformed or out-of-range input: an unknown command, or a bad card, amount, seat or range. */
public final class InvalidInputException extends PokerAssistantException {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
