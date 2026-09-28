package com.pokerassistant.game;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.eval.HandEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everything that happens in one hand: seats, stacks, board, pot and history.
 *
 * <p>Package-private methods are the state machine's transition actions and guards (see
 * {@link HandStateMachine}); they are the only code that mutates a hand. Public methods are read-only
 * queries used by the CLI and the strategy layer.
 *
 * <p>Pot accounting: chips from finished streets sit in {@code collected}; chips bet on the current
 * street stay in each player's {@code streetBet} until the round closes, when an uncalled excess is
 * returned and the rest is collected. Side pots are derived on demand from total contributions, so
 * they can never drift out of sync.
 */
public final class HandContext {

    private TableConfig table;
    private final List<PlayerState> players = new ArrayList<>();
    private int buttonIndex = -1;
    private int smallBlindIndex = -1;
    private int bigBlindIndex = -1;
    private int heroIndex = -1;
    private final List<Card> board = new ArrayList<>();
    private long boardMask;
    private Street street = Street.PREFLOP;
    private BettingRound round;
    private Chips collected = Chips.ZERO;
    private Chips initialChips = Chips.ZERO;
    private Chips finalPot = Chips.ZERO;
    private final List<ActionRecord> actions = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final Map<Integer, Chips> winnings = new LinkedHashMap<>();

    HandContext() {
    }

    // ------------------------------------------------------------------ transition actions

    void startHand(HandEvent.StartHand event) {
        table = event.table();
        List<TableConfig.Seat> seated = table.seats().stream().filter(seat -> seat.stack().isPositive()).toList();
        if (seated.size() < 2) {
            throw new IllegalActionException("At least two seats need chips to deal a hand (use 'stack <seat> <amount>')");
        }
        if (table.stackOf(table.buttonSeat()).isZero()) {
            throw new IllegalActionException("The button (seat " + table.buttonSeat() + ") has no chips; move it with 'button <seat>'");
        }
        for (int i = 0; i < seated.size(); i++) {
            if (seated.get(i).number() == table.buttonSeat()) {
                buttonIndex = i;
            }
        }
        List<String> positions = positionLabels(seated.size());
        for (int i = 0; i < seated.size(); i++) {
            TableConfig.Seat seat = seated.get(i);
            players.add(new PlayerState(seat.number(), positions.get(Math.floorMod(i - buttonIndex, seated.size())), seat.stack()));
            initialChips = initialChips.plus(seat.stack());
            if (seat.number() == table.heroSeat()) {
                heroIndex = i;
            }
        }
        if (event.heroCards() != 0) {
            setHeroCards(event.heroCards());
        }
        smallBlindIndex = players.size() == 2 ? buttonIndex : next(buttonIndex);
        bigBlindIndex = next(smallBlindIndex);

        if (table.ante().isPositive()) {
            Chips antes = Chips.ZERO;
            for (PlayerState player : players) {
                antes = antes.plus(player.postDead(table.ante()));
            }
            collected = collected.plus(antes);
            log.add("Antes of " + table.ante() + " posted (" + antes + " total)");
        }
        postBlind(smallBlindIndex, table.smallBlind(), "small blind");
        postBlind(bigBlindIndex, table.bigBlind(), "big blind");
        round = BettingRound.preflop(players, bigBlindIndex, table.bigBlind());
    }

    void dealHeroCards(HandEvent.HoleCardsDealt event) {
        setHeroCards(event.cards());
    }

    void applyAction(HandEvent.PlayerActed event) {
        ActionRecord record = round.apply(event.type(), event.amount(), pot());
        actions.add(record);
        log.add(record.describe());
    }

    void dealBoard(HandEvent.BoardDealt event, Street next) {
        int expected = next.boardCards() - board.size();
        List<Card> cards = event.cards();
        if (cards.size() != expected) {
            throw new InvalidInputException("The %s needs exactly %d card%s, got %d".formatted(
                    next.label().toLowerCase(Locale.ROOT), expected, expected == 1 ? "" : "s", cards.size()));
        }
        long dealt = CardMask.of(cards);
        if (Long.bitCount(dealt) != cards.size()) {
            throw new InvalidInputException("Duplicate card in " + cards);
        }
        long clash = dealt & knownCards();
        if (clash != 0) {
            throw new InvalidInputException("Already in play: " + CardMask.format(clash));
        }
        board.addAll(cards);
        boardMask |= dealt;
        street = next;
        log.add("*** " + next.label().toUpperCase(Locale.ROOT) + " *** " + boardText());
        round = BettingRound.postflop(next, players, buttonIndex, table.bigBlind());
    }

    void closeBettingRound() {
        returnUncalledBet();
        for (PlayerState player : players) {
            collected = collected.plus(player.streetBet());
            player.startNewStreet();
        }
        round = null;
    }

    void awardUncontested() {
        closeBettingRound();
        PlayerState winner = livePlayers().get(0);
        finalPot = collected;
        pay(Map.of(winner.seat(), collected));
        log.add("Seat %d wins %s uncontested".formatted(winner.seat(), finalPot));
    }

    void showCards(HandEvent.CardsShown event) {
        PlayerState player = requireLivePlayer(event.seat());
        long otherKnown = knownCards() & ~player.holeCards();
        if ((event.cards() & otherKnown) != 0) {
            throw new InvalidInputException("Already in play: " + CardMask.format(event.cards() & otherKnown));
        }
        player.setHoleCards(event.cards());
        String made = board.size() >= 3 ? " (" + HandEvaluator.describe(HandEvaluator.evaluate(event.cards() | boardMask)) + ")" : "";
        log.add("Seat %d shows %s%s".formatted(player.seat(), CardMask.format(event.cards()), made));
    }

    void muck(HandEvent.Mucked event) {
        PlayerState player = requireLivePlayer(event.seat());
        for (Pot pot : PotCalculator.buildPots(players)) {
            if (pot.eligibleSeats().equals(List.of(player.seat()))) {
                throw new IllegalActionException("Seat %d is the only player left in a pot of %s and can't muck".formatted(player.seat(), pot.amount()));
            }
        }
        player.fold();
        log.add("Seat " + player.seat() + " mucks");
    }

    void settleShowdown() {
        List<Pot> pots = PotCalculator.buildPots(players);
        Map<Integer, Chips> won = new LinkedHashMap<>();
        for (int i = 0; i < pots.size(); i++) {
            Pot pot = pots.get(i);
            List<Integer> winners = bestHands(pot.eligibleSeats());
            PotCalculator.split(pot.amount(), inOddChipOrder(winners)).forEach((seat, share) -> won.merge(seat, share, Chips::plus));
            String how = pot.eligibleSeats().size() == 1 ? "uncontested" : "with " + describeHand(winners.get(0));
            log.add("%s %s %s (%s) %s".formatted(seatList(winners), winners.size() == 1 ? "wins" : "split", potName(i), pot.amount(), how));
        }
        finalPot = pots.stream().map(Pot::amount).reduce(Chips.ZERO, Chips::plus);
        pay(won);
    }

    void declareWinners(HandEvent.WinnersDeclared event) {
        for (int seat : event.seats()) {
            requireLivePlayer(seat);
        }
        List<Pot> pots = PotCalculator.buildPots(players);
        Map<Integer, Chips> won = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < pots.size(); i++) {
            Pot pot = pots.get(i);
            List<Integer> winners = pot.eligibleSeats().stream().filter(event.seats()::contains).toList();
            if (winners.isEmpty()) {
                throw new IllegalActionException("None of seats %s can win the %s (%s); eligible seats: %s".formatted(
                        event.seats(), potName(i), pot.amount(), pot.eligibleSeats()));
            }
            PotCalculator.split(pot.amount(), inOddChipOrder(winners)).forEach((seat, share) -> won.merge(seat, share, Chips::plus));
            lines.add("%s %s %s (%s)".formatted(seatList(winners), winners.size() == 1 ? "wins" : "split", potName(i), pot.amount()));
        }
        finalPot = pots.stream().map(Pot::amount).reduce(Chips.ZERO, Chips::plus);
        pay(won);
        log.addAll(lines);
    }

    // ------------------------------------------------------------------ guards

    boolean onlyOnePlayerLeft() {
        return livePlayers().size() == 1;
    }

    boolean bettingRoundClosed() {
        return round != null && round.isClosed();
    }

    boolean showdownDecided() {
        List<PlayerState> live = livePlayers();
        return live.size() == 1 || live.stream().allMatch(PlayerState::hasKnownCards);
    }

    // ------------------------------------------------------------------ queries

    public TableConfig table() {
        return table;
    }

    public List<PlayerState> players() {
        return Collections.unmodifiableList(players);
    }

    public Optional<PlayerState> player(int seat) {
        return players.stream().filter(player -> player.seat() == seat).findFirst();
    }

    public Optional<PlayerState> hero() {
        return heroIndex < 0 ? Optional.empty() : Optional.of(players.get(heroIndex));
    }

    public int heroSeat() {
        return table.heroSeat();
    }

    public List<PlayerState> livePlayers() {
        return players.stream().filter(PlayerState::isInHand).toList();
    }

    public List<Card> board() {
        return List.copyOf(board);
    }

    public long boardMask() {
        return boardMask;
    }

    public String boardText() {
        return board.isEmpty() ? "-" : String.join(" ", board.stream().map(Card::toString).toList());
    }

    public Street street() {
        return street;
    }

    public Chips bigBlind() {
        return table.bigBlind();
    }

    public int buttonSeat() {
        return players.get(buttonIndex).seat();
    }

    public int smallBlindSeat() {
        return players.get(smallBlindIndex).seat();
    }

    public int bigBlindSeat() {
        return players.get(bigBlindIndex).seat();
    }

    /** The whole pot: collected chips plus everything bet on the current street. */
    public Chips pot() {
        Chips pot = collected;
        for (PlayerState player : players) {
            pot = pot.plus(player.streetBet());
        }
        return pot;
    }

    /** The pot as it was when the hand was settled (0 before that). */
    public Chips finalPot() {
        return finalPot;
    }

    /** Main pot and side pots as they stand now. */
    public List<Pot> pots() {
        return PotCalculator.buildPots(players);
    }

    public boolean isBettingOpen() {
        return round != null && !round.isClosed();
    }

    public Optional<PlayerState> playerToAct() {
        return isBettingOpen() ? Optional.of(round.playerToAct()) : Optional.empty();
    }

    public Optional<LegalActions> legalActions() {
        return isBettingOpen() ? Optional.of(round.legalActions()) : Optional.empty();
    }

    public boolean isHeroToAct() {
        return playerToAct().map(player -> player.seat() == table.heroSeat()).orElse(false);
    }

    /** Highest bet on the current street (the big blind preflop until someone raises). */
    public Chips currentBet() {
        return round == null ? Chips.ZERO : round.currentBet();
    }

    /** Bets and raises made so far on the current street. */
    public int streetAggression() {
        return round == null ? 0 : round.aggression();
    }

    public List<ActionRecord> actions() {
        return Collections.unmodifiableList(actions);
    }

    /** Narrative of the hand so far, one line per event. */
    public List<String> log() {
        return Collections.unmodifiableList(log);
    }

    /** Chips won per seat once the hand is settled. */
    public Map<Integer, Chips> winnings() {
        return Collections.unmodifiableMap(winnings);
    }

    /** Every card whose location is known: the board plus all known hole cards. */
    public long knownCards() {
        long known = boardMask;
        for (PlayerState player : players) {
            known |= player.holeCards();
        }
        return known;
    }

    /** Opponents of {@code seat} that can still act and have not acted yet on this street. */
    public int opponentsYetToAct(int seat) {
        return (int) players.stream()
                .filter(player -> player.seat() != seat && player.canAct() && !player.hasActed())
                .count();
    }

    /** Whether {@code seat} acts last after the flop among the players still in the hand. */
    public boolean actsLastPostflop(int seat) {
        return livePlayers().stream()
                .max(Comparator.comparingInt(player -> postflopActingOrder(player.seat())))
                .map(player -> player.seat() == seat)
                .orElse(false);
    }

    /** Postflop acting order of {@code seat}: 0 acts first (left of the button), the button acts last. */
    public int postflopActingOrder(int seat) {
        return Math.floorMod(indexOf(seat) - buttonIndex - 1, players.size());
    }

    /** Whether {@code seat} has chosen to put or keep chips in this hand (anything but posting blinds or folding). */
    public boolean hasActedVoluntarily(int seat) {
        return actions.stream().anyMatch(action -> action.seat() == seat && action.type() != ActionType.FOLD);
    }

    /** Stacks plus the pot; equal to {@link #initialChips()} at every step (chips are conserved). */
    public Chips chipsInPlay() {
        Chips total = pot();
        for (PlayerState player : players) {
            total = total.plus(player.stack());
        }
        return total;
    }

    public Chips initialChips() {
        return initialChips;
    }

    // ------------------------------------------------------------------ internals

    private void setHeroCards(long cards) {
        if (heroIndex < 0) {
            throw new IllegalActionException("Hero (seat " + table.heroSeat() + ") is not dealt into this hand");
        }
        PlayerState hero = players.get(heroIndex);
        long otherKnown = knownCards() & ~hero.holeCards();
        if ((cards & otherKnown) != 0) {
            throw new InvalidInputException("Already in play: " + CardMask.format(cards & otherKnown));
        }
        hero.setHoleCards(cards);
    }

    private void postBlind(int index, Chips amount, String name) {
        PlayerState player = players.get(index);
        Chips paid = player.commit(amount);
        log.add("Seat %d (%s) posts %s %s%s".formatted(player.seat(), player.position(), name, paid, player.isAllIn() ? " (all-in)" : ""));
    }

    private void returnUncalledBet() {
        PlayerState top = null;
        Chips second = Chips.ZERO;
        for (PlayerState player : players) {
            if (top == null || player.streetBet().isGreaterThan(top.streetBet())) {
                if (top != null) {
                    second = second.max(top.streetBet());
                }
                top = player;
            } else {
                second = second.max(player.streetBet());
            }
        }
        if (top != null && top.streetBet().isGreaterThan(second)) {
            Chips excess = top.streetBet().minus(second);
            top.refund(excess);
            log.add("Uncalled bet of %s returned to seat %d".formatted(excess, top.seat()));
        }
    }

    private void pay(Map<Integer, Chips> amounts) {
        amounts.forEach((seat, amount) -> {
            player(seat).orElseThrow().win(amount);
            winnings.merge(seat, amount, Chips::plus);
        });
        collected = Chips.ZERO;
    }

    private List<Integer> bestHands(List<Integer> contenders) {
        if (contenders.size() == 1) {
            return contenders;
        }
        int best = Integer.MIN_VALUE;
        List<Integer> winners = new ArrayList<>();
        for (int seat : contenders) {
            int value = HandEvaluator.evaluate(player(seat).orElseThrow().holeCards() | boardMask);
            if (value > best) {
                best = value;
                winners.clear();
            }
            if (value == best) {
                winners.add(seat);
            }
        }
        return winners;
    }

    private String describeHand(int seat) {
        long cards = player(seat).orElseThrow().holeCards();
        return cards == 0 ? "an unshown hand" : HandEvaluator.describe(HandEvaluator.evaluate(cards | boardMask));
    }

    /** Winners sorted from the first seat left of the button: odd chips go to them first. */
    private List<Integer> inOddChipOrder(List<Integer> seats) {
        return seats.stream().sorted(Comparator.comparingInt(this::postflopActingOrder)).toList();
    }

    private PlayerState requireLivePlayer(int seat) {
        PlayerState player = player(seat)
                .orElseThrow(() -> new InvalidInputException("Seat " + seat + " is not dealt into this hand"));
        if (!player.isInHand()) {
            throw new IllegalActionException("Seat " + seat + " is no longer in the hand");
        }
        return player;
    }

    private int indexOf(int seat) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).seat() == seat) {
                return i;
            }
        }
        throw new IllegalArgumentException("No seat " + seat);
    }

    private int next(int index) {
        return (index + 1) % players.size();
    }

    private static String potName(int index) {
        return index == 0 ? "the main pot" : "side pot " + index;
    }

    private static String seatList(List<Integer> seats) {
        return (seats.size() == 1 ? "Seat " : "Seats ") + String.join(" & ", seats.stream().map(String::valueOf).toList());
    }

    /**
     * Position names indexed by distance from the button: BTN, SB, BB, then the early seats named from
     * the cutoff backwards (CO, HJ, LJ, UTG+n, UTG). Heads-up the button is also the small blind.
     */
    static List<String> positionLabels(int players) {
        if (players == 2) {
            return List.of("BTN", "BB");
        }
        List<String> labels = new ArrayList<>(List.of("BTN", "SB", "BB"));
        int early = players - 3;
        List<String> tail = List.of("LJ", "HJ", "CO");
        if (early <= 3) {
            List<String> named = new ArrayList<>(tail.subList(3 - early, 3));
            if (early == 3) {
                named.set(0, "UTG");
            }
            labels.addAll(named);
        } else {
            labels.add("UTG");
            for (int i = 1; i <= early - 4; i++) {
                labels.add("UTG+" + i);
            }
            labels.addAll(tail);
        }
        return labels;
    }
}
