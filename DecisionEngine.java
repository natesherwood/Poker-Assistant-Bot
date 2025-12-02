class DecisionEngine {

    public enum Action {
        FOLD, CHECK, CALL, RAISE_SMALL, RAISE_MEDIUM, RAISE_LARGE, ALL_IN
    }

    public static Action recommendAction(Hand hand, GameState gameState) {
        double handStrength = hand.getHandStrength(gameState.getStage());
        double potOdds = gameState.getPotOdds();
        Position position = gameState.getPosition();

        // Get base decision based on hand strength and position
        Action baseAction = getBaseAction(handStrength, position, gameState.getStage());

        // Adjust for pot odds
        if (gameState.getBetToCall() > 0) {
            boolean profitableCall = PotOddsCalculator.isProfitableCall(handStrength, potOdds);

            if (!profitableCall && (baseAction == Action.CALL || baseAction == Action.CHECK)) {
                return Action.FOLD;
            }
        }

        // Adjust for stack size
        if (gameState.isShortStack() && handStrength > 0.7) {
            return Action.ALL_IN;
        }

        // Adjust for position
        baseAction = adjustForPosition(baseAction, position, handStrength);

        return baseAction;
    }

    private static Action getBaseAction(double handStrength, Position position, String stage) {
        if (handStrength >= 0.85) {
            return Action.RAISE_LARGE;
        } else if (handStrength >= 0.70) {
            return Action.RAISE_MEDIUM;
        } else if (handStrength >= 0.55) {
            return Action.RAISE_SMALL;
        } else if (handStrength >= 0.40) {
            return Action.CALL;
        } else if (handStrength >= 0.35) {
            return Action.CHECK;
        } else {
            return Action.FOLD;
        }
    }

    private static Action adjustForPosition(Action baseAction, Position position, double handStrength) {
        // Be more aggressive in late position
        if (position.isLatePosition() && handStrength > 0.45) {
            if (baseAction == Action.CALL) return Action.RAISE_SMALL;
            if (baseAction == Action.CHECK) return Action.CALL;
        }

        // Be more conservative in early position
        if (position.isEarlyPosition() && handStrength < 0.60) {
            if (baseAction == Action.RAISE_SMALL) return Action.CALL;
            if (baseAction == Action.CALL) return Action.CHECK;
        }

        return baseAction;
    }
}
