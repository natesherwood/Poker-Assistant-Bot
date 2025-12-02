import java.util.*;

class Hand {
    private Card[] holeCards = new Card[2];
    private List<Card> communityCards = new ArrayList<>();

    public void setHoleCards(Card card1, Card card2) {
        holeCards[0] = card1;
        holeCards[1] = card2;
    }

    public void setCommunityCards(List<Card> cards) {
        this.communityCards = new ArrayList<>(cards);
    }

    public Card[] getHoleCards() { return holeCards; }
    public List<Card> getCommunityCards() { return communityCards; }

    public double getHandStrength(String stage) {
        if (stage.equals("preflop")) {
            return getPreflopStrength();
        } else {
            return getPostflopStrength();
        }
    }

    private double getPreflopStrength() {
        Card c1 = holeCards[0];
        Card c2 = holeCards[1];

        if (c1 == null || c2 == null) return 0.0;

        int v1 = c1.getRankValue();
        int v2 = c2.getRankValue();

        // Pocket pairs
        if (v1 == v2) {
            if (v1 >= 13) return 0.95;  // AA, KK, QQ
            if (v1 >= 11) return 0.90;  // JJ
            if (v1 >= 9) return 0.80;   // 99, TT
            if (v1 >= 7) return 0.65;   // 77, 88
            return 0.50;                // Small pairs
        }

        // High cards
        boolean suited = c1.getSuit().equals(c2.getSuit());
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);

        // Premium hands
        if (high == 14 && low >= 10) return suited ? 0.85 : 0.80; // AK, AQ, AJ, AT
        if (high == 13 && low >= 10) return suited ? 0.75 : 0.70; // KQ, KJ, KT
        if (high == 12 && low >= 10) return suited ? 0.65 : 0.60; // QJ, QT

        // Connected cards
        if (Math.abs(v1 - v2) <= 1 && low >= 7) {
            return suited ? 0.60 : 0.50;
        }

        // Suited cards with high card
        if (suited && high >= 10) return 0.55;

        // Any ace
        if (high == 14) return suited ? 0.50 : 0.40;

        return 0.30; // Weak hands
    }

    private double getPostflopStrength() {
        // Simplified postflop evaluation
        List<Card> allCards = new ArrayList<>();
        allCards.add(holeCards[0]);
        allCards.add(holeCards[1]);
        allCards.addAll(communityCards);

        // Basic hand type detection
        double strength = getPreflopStrength();

        // Adjust based on board texture (simplified)
        if (communityCards.size() >= 3) {
            // Check for potential draws, pairs, etc.
            strength *= (0.6 + 0.4 * getBoardFit());
        }

        return Math.min(0.99, strength);
    }

    private double getBoardFit() {
        // Simplified board fitting calculation
        // In reality, this would check for pairs, draws, etc.
        return 0.5 + Math.random() * 0.5;
    }
}
