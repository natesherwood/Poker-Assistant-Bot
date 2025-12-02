enum Position {
    UTG(1), UTG1(2), UTG2(3), MP1(4), MP2(5), MP3(6),
    HIJACK(7), CUTOFF(8), BUTTON(9), SMALL_BLIND(10), BIG_BLIND(11);

    private int value;
    Position(int value) { this.value = value; }
    public int getValue() { return value; }

    public boolean isEarlyPosition() { return value <= 3; }
    public boolean isMiddlePosition() { return value >= 4 && value <= 6; }
    public boolean isLatePosition() { return value >= 7 && value <= 9; }
    public boolean isBlinds() { return value >= 10; }

    public static Position getPositionForPlayer(int playerSeat, int dealerSeat, int numPlayers) {
        int relativePosition = (playerSeat - dealerSeat + numPlayers) % numPlayers;

        // Adjust for table size
        switch(numPlayers) {
            case 2:
                return relativePosition == 1 ? SMALL_BLIND : BIG_BLIND;
            case 3:
                switch(relativePosition) {
                    case 1: return SMALL_BLIND;
                    case 2: return BIG_BLIND;
                    default: return BUTTON;
                }
            case 6:
                switch(relativePosition) {
                    case 1: return UTG;
                    case 2: return MP1;
                    case 3: return CUTOFF;
                    case 4: return BUTTON;
                    case 5: return SMALL_BLIND;
                    default: return BIG_BLIND;
                }
            case 9:
                switch(relativePosition) {
                    case 1: return UTG;
                    case 2: return UTG1;
                    case 3: return MP1;
                    case 4: return MP2;
                    case 5: return HIJACK;
                    case 6: return CUTOFF;
                    case 7: return BUTTON;
                    case 8: return SMALL_BLIND;
                    default: return BIG_BLIND;
                }
            default:
                // Default 9-handed mapping
                return values()[relativePosition % 9];
        }
    }
}

