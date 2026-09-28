package com.pokerassistant.fsm;

/**
 * One state change taken by a {@link StateMachine}.
 *
 * @param cause the triggering event's type name, or the reason of an automatic completion transition
 */
public record StateChange<S extends Enum<S>>(S from, S to, String cause) {
}
