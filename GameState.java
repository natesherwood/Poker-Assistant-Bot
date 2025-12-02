class GameState {
    private int numPlayers;
    private int dealerPosition;
    private int playerPosition;
    private String stage; // preflop, flop, turn, river
    private double potSize;
    private double betToCall;
    private double playerStack;
    private Position position;

    public GameState(int numPlayers, int dealerPosition, int playerPosition,
                     String stage, double potSize, double betToCall, double playerStack) {
        this.numPlayers = numPlayers;
        this.dealerPosition = dealerPosition;
        this.playerPosition = playerPosition;
        this.stage = stage.toLowerCase();
        this.potSize = potSize;
        this.betToCall = betToCall;
        this.playerStack = playerStack;
        this.position = Position.getPositionForPlayer(playerPosition, dealerPosition, numPlayers);
    }

    // Getters
    public int getNumPlayers() { return numPlayers; }
    public int getDealerPosition() { return dealerPosition; }
    public int getPlayerPosition() { return playerPosition; }
    public String getStage() { return stage; }
    public double getPotSize() { return potSize; }
    public double getBetToCall() { return betToCall; }
    public double getPlayerStack() { return playerStack; }
    public Position getPosition() { return position; }

    public double getPotOdds() {
        if (betToCall == 0) return 0;
        return betToCall / (potSize + betToCall);
    }

    public double getStackToPotRatio() {
        return playerStack / potSize;
    }

    public boolean isShortStack() {
        return playerStack < potSize * 0.5;
    }
}
