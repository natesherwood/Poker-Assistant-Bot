package com.pokerassistant.error;

/**
 * Well-formed input that the rules of poker do not allow in the current spot, such as checking while
 * facing a bet or raising less than the minimum.
 */
public final class IllegalActionException extends PokerAssistantException {

    private static final long serialVersionUID = 1L;

    public IllegalActionException(String message) {
        super(message);
    }
}
