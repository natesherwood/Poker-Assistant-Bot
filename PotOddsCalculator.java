class PotOddsCalculator {

    public static double calculatePotOdds(double betToCall, double potSize) {
        if (betToCall == 0) return 0;
        return betToCall / (potSize + betToCall);
    }

    public static double calculateImpliedOdds(double betToCall, double potSize,
                                              double opponentStack, double winRate) {
        double totalPotential = potSize + betToCall + Math.min(opponentStack, betToCall * 2);
        return betToCall / totalPotential * winRate;
    }

    public static boolean isProfitableCall(double winRate, double potOdds) {
        return winRate > potOdds;
    }
}
