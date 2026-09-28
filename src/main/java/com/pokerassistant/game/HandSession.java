package com.pokerassistant.game;

import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.fsm.StateChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A playing session: the table carried between hands plus the hand in progress, stored as an event
 * log. Replaying the log rebuilds a hand exactly, which gives two guarantees:
 * <ul>
 *   <li><b>atomic input</b>: a batch of events (say "f f r 2.5 c") applies completely or not at all;</li>
 *   <li><b>undo</b>: drop the last event and replay.</li>
 * </ul>
 * A finished hand's stacks and button move are applied to the table lazily, when the next hand starts
 * or the table is edited, so a mistake spotted right after the hand ends can still be undone.
 */
public final class HandSession {

    private TableConfig table;
    private HandStateMachine hand;
    private final List<HandEvent> events = new ArrayList<>();
    private int handNumber;
    private boolean resultApplied;

    public HandSession(TableConfig table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public Optional<HandStateMachine> hand() {
        return Optional.ofNullable(hand);
    }

    public int handNumber() {
        return handNumber;
    }

    public boolean isHandInProgress() {
        return hand != null && !hand.isComplete();
    }

    /** The table the next hand will be dealt with (a finished hand's results already applied). */
    public TableConfig upcomingTable() {
        if (hand == null || !hand.isComplete() || resultApplied) {
            return table;
        }
        Map<Integer, Chips> stacks = hand.context().players().stream()
                .collect(Collectors.toMap(PlayerState::seat, PlayerState::stack));
        return table.afterHand(stacks);
    }

    /** Edits the table between hands; not allowed while a hand is running. */
    public void updateTable(UnaryOperator<TableConfig> change) {
        requireNoHandInProgress("change the table");
        TableConfig updated = change.apply(upcomingTable());
        table = updated;
        resultApplied = true;
    }

    public List<StateChange<HandState>> startHand(long heroCards) {
        requireNoHandInProgress("start a new hand");
        TableConfig next = upcomingTable();
        HandStateMachine fresh = new HandStateMachine();
        HandEvent start = new HandEvent.StartHand(next, heroCards);
        List<StateChange<HandState>> changes = fresh.fire(start);
        table = next;
        hand = fresh;
        resultApplied = false;
        events.clear();
        events.add(start);
        handNumber++;
        return changes;
    }

    public List<StateChange<HandState>> apply(HandEvent event) {
        return applyAll(List.of(event));
    }

    /** Applies the events in order; if any is rejected, the hand is restored to where it was before the batch. */
    public List<StateChange<HandState>> applyAll(List<? extends HandEvent> batch) {
        HandStateMachine current = requireHand();
        int checkpoint = events.size();
        List<StateChange<HandState>> changes = new ArrayList<>();
        try {
            for (HandEvent event : batch) {
                changes.addAll(current.fire(event));
                events.add(event);
            }
            return changes;
        } catch (RuntimeException rejected) {
            events.subList(checkpoint, events.size()).clear();
            hand = replay(events);
            throw rejected;
        }
    }

    /** Removes the last event of the current hand; returns it. */
    public HandEvent undo() {
        requireHand();
        if (resultApplied) {
            throw new IllegalActionException("Hand #" + handNumber + " is closed and its result is applied to the table");
        }
        if (events.size() <= 1) {
            throw new IllegalActionException("Nothing left to undo in this hand; use 'abort' to discard it");
        }
        HandEvent removed = events.remove(events.size() - 1);
        hand = replay(events);
        return removed;
    }

    /** Discards the hand in progress; stacks and button stay as they were before it. */
    public void abort() {
        if (!isHandInProgress()) {
            throw new IllegalActionException("There is no hand in progress to abort");
        }
        hand = null;
        events.clear();
    }

    public List<HandEvent> events() {
        return List.copyOf(events);
    }

    private HandStateMachine requireHand() {
        if (hand == null) {
            throw new IllegalActionException("No hand in progress; start one with 'new [hole cards]'");
        }
        return hand;
    }

    private void requireNoHandInProgress(String what) {
        if (isHandInProgress()) {
            throw new IllegalActionException("Can't %s: hand #%d is still in progress (%s). Finish it or use 'abort'."
                    .formatted(what, handNumber, hand.state().description().toLowerCase(Locale.ROOT)));
        }
    }

    private static HandStateMachine replay(List<HandEvent> events) {
        HandStateMachine machine = new HandStateMachine();
        for (HandEvent event : events) {
            machine.fire(event);
        }
        return machine;
    }
}
