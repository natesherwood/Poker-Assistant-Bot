package com.pokerassistant.cli;

import com.pokerassistant.cards.Card;
import com.pokerassistant.equity.Range;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.Street;

import java.util.List;

/** A parsed, syntactically validated user command. Game-state rules are checked when it runs. */
public sealed interface Command {

    /** One betting action; {@code amount} is the street total for bet and raise, null otherwise. */
    record ActionInput(ActionType type, Chips amount) {
    }

    /** {@code topic} is null for the general help page. */
    record Help(String topic) implements Command {
    }

    record Quit() implements Command {
    }

    record Status() implements Command {
    }

    /** Table settings; a null field keeps its current value. */
    record Setup(Integer players, Chips stack, Chips smallBlind, Chips bigBlind, Chips ante, Integer button, Integer hero)
            implements Command {
    }

    record SetStack(int seat, Chips stack) implements Command {
    }

    record MoveButton(int seat) implements Command {
    }

    /** {@code heroCards} is 0 when not given. */
    record NewHand(long heroCards) implements Command {
    }

    record SetHoleCards(long cards) implements Command {
    }

    record Act(List<ActionInput> actions) implements Command {
        public Act {
            actions = List.copyOf(actions);
        }
    }

    /** {@code street} null means "deal whatever streets are pending" (the 'board' command). */
    record DealBoard(Street street, List<Card> cards) implements Command {
        public DealBoard {
            cards = List.copyOf(cards);
        }
    }

    record Show(int seat, long cards) implements Command {
    }

    record Muck(int seat) implements Command {
    }

    record Winners(List<Integer> seats) implements Command {
        public Winners {
            seats = List.copyOf(seats);
        }
    }

    record Advise() implements Command {
    }

    /** {@code hero == 0} means "use the hand in progress". */
    record EquityQuery(long hero, List<Range> villains, long board) implements Command {
        public EquityQuery {
            villains = List.copyOf(villains);
        }
    }

    /** {@code equityPercent} is null when not given. */
    record Odds(Chips pot, Chips bet, Double equityPercent) implements Command {
    }

    /** {@code range} null means "back to automatic estimation". */
    record SetRange(int seat, Range range) implements Command {
    }

    record ShowRanges() implements Command {
    }

    record Undo() implements Command {
    }

    record History() implements Command {
    }

    record Abort() implements Command {
    }

    record ShowFsm() implements Command {
    }

    record AutoAdvice(boolean enabled) implements Command {
    }
}
