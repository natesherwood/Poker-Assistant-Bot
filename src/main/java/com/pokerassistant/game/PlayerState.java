package com.pokerassistant.game;

/**
 * One seated player's state within a hand. Getters are public; mutators are package-private so only
 * the hand engine ({@link BettingRound}, {@link HandContext}) can move chips.
 */
public final class PlayerState {

    private final int seat;
    private final String position;
    private final Chips startingStack;
    private Chips stack;
    private Chips streetBet = Chips.ZERO;
    private Chips totalBet = Chips.ZERO;
    private boolean folded;
    private boolean acted;
    private Chips levelAtLastAction = Chips.ZERO;
    private long holeCards;

    PlayerState(int seat, String position, Chips stack) {
        this.seat = seat;
        this.position = position;
        this.startingStack = stack;
        this.stack = stack;
    }

    public int seat() {
        return seat;
    }

    /** Position label for this hand: BTN, SB, BB, UTG, UTG+1, LJ, HJ or CO. */
    public String position() {
        return position;
    }

    public Chips startingStack() {
        return startingStack;
    }

    public Chips stack() {
        return stack;
    }

    /** Chips put in on the current street. */
    public Chips streetBet() {
        return streetBet;
    }

    /** Chips put in during the whole hand, antes included; this is what side pots are built from. */
    public Chips totalBet() {
        return totalBet;
    }

    public boolean hasFolded() {
        return folded;
    }

    /** Whether the player has voluntarily acted on the current street (posting a blind does not count). */
    public boolean hasActed() {
        return acted;
    }

    public boolean isInHand() {
        return !folded;
    }

    public boolean isAllIn() {
        return !folded && stack.isZero();
    }

    /** Still in the hand with chips behind, so able to take betting actions. */
    public boolean canAct() {
        return !folded && stack.isPositive();
    }

    /** Hole-card mask, or 0 when unknown. */
    public long holeCards() {
        return holeCards;
    }

    public boolean hasKnownCards() {
        return holeCards != 0;
    }

    /** The bet level this player last matched or set; decides whether an incomplete raise re-opens betting. */
    Chips levelAtLastAction() {
        return levelAtLastAction;
    }

    /** Moves up to {@code amount} into this street's bet; returns what was actually paid (short = all-in). */
    Chips commit(Chips amount) {
        Chips paid = amount.min(stack);
        stack = stack.minus(paid);
        streetBet = streetBet.plus(paid);
        totalBet = totalBet.plus(paid);
        return paid;
    }

    /** Dead money such as an ante: goes in the pot but does not count toward this street's bet. */
    Chips postDead(Chips amount) {
        Chips paid = amount.min(stack);
        stack = stack.minus(paid);
        totalBet = totalBet.plus(paid);
        return paid;
    }

    void refund(Chips amount) {
        streetBet = streetBet.minus(amount);
        totalBet = totalBet.minus(amount);
        stack = stack.plus(amount);
    }

    void win(Chips amount) {
        stack = stack.plus(amount);
    }

    void fold() {
        folded = true;
    }

    void markActed(Chips level) {
        acted = true;
        levelAtLastAction = level;
    }

    void startNewStreet() {
        streetBet = Chips.ZERO;
        acted = false;
        levelAtLastAction = Chips.ZERO;
    }

    void setHoleCards(long cards) {
        holeCards = cards;
    }
}
