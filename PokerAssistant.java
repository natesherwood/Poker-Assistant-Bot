import java.util.*;

public class PokerAssistant {
    private Scanner scanner;
    private Hand currentHand;
    private GameState gameState;

    public PokerAssistant() {
        scanner = new Scanner(System.in);
        currentHand = new Hand();
    }

    public void start() {
        System.out.println("=== Poker Assistant Bot ===");
        System.out.println("This bot will help you make optimal poker decisions!");

        while (true) {
            System.out.println("\nOptions:");
            System.out.println("1. Analyze new hand");
            System.out.println("2. Update community cards");
            System.out.println("3. Get recommendation");
            System.out.println("4. Record action and advance");
            System.out.println("5. Exit");

            int choice = getIntInput("Choose option: ");

            switch (choice) {
                case 1:
                    setupNewHand();
                    break;
                case 2:
                    updateCommunityCards();
                    break;
                case 3:
                    getRecommendation();
                    break;
                case 4:
                    recordActionAndAdvance();
                    break;
                case 5:
                    System.out.println("Good luck at the tables!");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void setupNewHand() {
        System.out.println("\n=== New Hand Setup ===");

        // Get hole cards
        System.out.println("Enter your hole cards:");
        System.out.println("Format: rank+suit (e.g., As for Ace of spades, Kh for King of hearts)");

        Card card1 = parseCard("First card: ");
        Card card2 = parseCard("Second card: ");

        currentHand.setHoleCards(card1, card2);

        // Get game state
        int numPlayers = getIntInput("Number of players: ");
        int dealerPos = getIntInput("Dealer position (1-" + numPlayers + "): ");
        int yourPos = getIntInput("Your position (1-" + numPlayers + "): ");

        System.out.print("Stage (preflop/flop/turn/river): ");
        String stage = scanner.nextLine();

        double potSize = getDoubleInput("Current pot size: ");
        double betToCall = getDoubleInput("Bet to call (0 if no bet): ");
        double yourStack = getDoubleInput("Your stack size: ");

        gameState = new GameState(numPlayers, dealerPos, yourPos, stage, potSize, betToCall, yourStack);

        System.out.println("Hand setup complete!");
        System.out.println("Your position: " + gameState.getPosition());
    }

    private void updateCommunityCards() {
        if (gameState == null || gameState.getStage().equals("preflop")) {
            System.out.println("No community cards in preflop or no hand setup yet.");
            return;
        }

        System.out.println("\n=== Update Community Cards ===");
        System.out.println("Current stage: " + gameState.getStage());

        List<Card> communityCards = new ArrayList<>();

        if (gameState.getStage().equals("flop") || gameState.getStage().equals("turn") || gameState.getStage().equals("river")) {
            System.out.println("Enter flop cards (3 cards):");
            for (int i = 1; i <= 3; i++) {
                Card card = parseCard("Flop card " + i + ": ");
                communityCards.add(card);
            }
        }

        if (gameState.getStage().equals("turn") || gameState.getStage().equals("river")) {
            Card turnCard = parseCard("Turn card: ");
            communityCards.add(turnCard);
        }

        if (gameState.getStage().equals("river")) {
            Card riverCard = parseCard("River card: ");
            communityCards.add(riverCard);
        }

        currentHand.setCommunityCards(communityCards);
        System.out.println("Community cards updated!");
    }

    private void recordActionAndAdvance() {
        if (gameState == null) {
            System.out.println("Please setup a hand first!");
            return;
        }

        System.out.println("\n=== Record Your Action ===");
        System.out.println("Current stage: " + gameState.getStage());

        System.out.println("What action did you take?");
        System.out.println("1. Fold");
        System.out.println("2. Check");
        System.out.println("3. Call");
        System.out.println("4. Raise");

        int actionChoice = getIntInput("Choose action: ");

        switch (actionChoice) {
            case 1: // Fold
                System.out.println("Hand folded. Starting new hand...");
                currentHand = new Hand();
                gameState = null;
                return;

            case 2: // Check
                System.out.println("You checked.");
                advanceToNextRound(0);
                break;

            case 3: // Call
                double callAmount = gameState.getBetToCall();
                System.out.println("You called " + callAmount);
                advanceToNextRound(callAmount);
                break;

            case 4: // Raise
                double raiseAmount = getDoubleInput("Enter your raise amount: ");
                System.out.println("You raised " + raiseAmount);
                advanceToNextRound(raiseAmount);
                break;

            default:
                System.out.println("Invalid action. Please try again.");
                return;
        }
    }

    private void advanceToNextRound(double actionAmount) {
        String currentStage = gameState.getStage();
        String nextStage;

        // Determine next stage
        switch (currentStage) {
            case "preflop":
                nextStage = "flop";
                System.out.println("\n--- Advancing to FLOP (UPDATE COMMUNITY CARDS ONCE YOU ENTER THE BET TO CALL) ---");

                break;
            case "flop":
                nextStage = "turn";
                System.out.println("\n--- Advancing to TURN (UPDATE COMMUNITY CARDS ONCE YOU ENTER THE BET TO CALL)---");

                break;
            case "turn":
                nextStage = "river";
                System.out.println("\n--- Advancing to RIVER (UPDATE COMMUNITY CARDS ONCE YOU ENTER THE BET TO CALL) ---");

                break;
            case "river":
                System.out.println("\n--- Hand Complete! ---");
                System.out.println("Hand finished. Starting new hand...");
                currentHand = new Hand();
                gameState = null;
                return;
            default:
                nextStage = currentStage;
        }

        // Update game state for next round
        double newPotSize = gameState.getPotSize() + actionAmount;
        double newBetToCall = getDoubleInput("Enter new bet to call (0 if no bet): ");

        // Create new game state for next round
        gameState = new GameState(
                gameState.getNumPlayers(),
                gameState.getDealerPosition(),
                gameState.getPlayerPosition(),
                nextStage,
                newPotSize,
                newBetToCall,
                gameState.getPlayerStack() - actionAmount
        );

        System.out.println("Advanced to " + nextStage.toUpperCase());
        System.out.println("New pot size: " + newPotSize);
        if (newBetToCall > 0) {
            System.out.println("Bet to call: " + newBetToCall);
        }
    }



    private Card parseCard(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.length() < 2 || input.length() > 3) {
                System.out.println("Invalid format. Use format like 'As', 'Kh', '10d', etc.");
                continue;
            }

            String rank, suit;

            if (input.length() == 2) {
                rank = input.substring(0, 1);
                suit = input.substring(1, 2);
            } else { // length == 3, must be "10"
                if (input.startsWith("10")) {
                    rank = "10";
                    suit = input.substring(2, 3);
                } else {
                    System.out.println("Invalid format. Use format like 'As', 'Kh', '10d', etc.");
                    continue;
                }
            }

            // Validate suit
            if (!suit.toLowerCase().matches("[hdcs]")) {
                System.out.println("Invalid suit. Use h(hearts), d(diamonds), c(clubs), or s(spades)");
                continue;
            }

            // Validate rank
            if (!rank.toLowerCase().matches("([2-9]|10|t|j|q|k|a)")) {
                System.out.println("Invalid rank. Use 2-9, T(10), J, Q, K, or A");
                continue;
            }

            return new Card(rank, suit);
        }
    }

    private void getRecommendation() {
        if (currentHand == null || gameState == null) {
            System.out.println("Please setup a hand first!");
            return;
        }

        System.out.println("\n=== Hand Analysis ===");

        // Display current situation
        Card[] hole = currentHand.getHoleCards();
        System.out.println("Your cards: " + hole[0] + " " + hole[1]);
        System.out.println("Position: " + gameState.getPosition());
        System.out.println("Stage: " + gameState.getStage());
        System.out.println("Pot size: " + gameState.getPotSize());
        System.out.println("Bet to call: " + gameState.getBetToCall());

        if (gameState.getBetToCall() > 0) {
            System.out.printf("Pot odds: %.1f%%\n", gameState.getPotOdds() * 100);
        }

        // Get recommendation
        DecisionEngine.Action recommendation = DecisionEngine.recommendAction(currentHand, gameState);
        double handStrength = currentHand.getHandStrength(gameState.getStage());

        System.out.printf("Hand strength: %.1f%%\n", handStrength * 100);
        System.out.println("RECOMMENDATION: " + recommendation);

        // Provide reasoning
        explainRecommendation(recommendation, handStrength, gameState);
    }

    private void explainRecommendation(DecisionEngine.Action action, double handStrength, GameState state) {
        System.out.println("\nReasoning:");

        if (handStrength >= 0.80) {
            System.out.println("- Very strong hand, should be aggressive");
        } else if (handStrength >= 0.60) {
            System.out.println("- Good hand, play for value");
        } else if (handStrength >= 0.40) {
            System.out.println("- Marginal hand, consider pot odds and position");
        } else {
            System.out.println("- Weak hand, be cautious");
        }

        if (state.getPosition().isLatePosition()) {
            System.out.println("- Late position allows for more aggressive play");
        } else if (state.getPosition().isEarlyPosition()) {
            System.out.println("- Early position requires stronger hands");
        }

        if (state.getBetToCall() > 0) {
            boolean profitable = PotOddsCalculator.isProfitableCall(handStrength, state.getPotOdds());
            System.out.println("- Pot odds " + (profitable ? "favor" : "don't favor") + " a call");
        }

        if (state.isShortStack()) {
            System.out.println("- Short stack situation - consider all-in with decent hands");
        }
    }

    private int getIntInput(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.print("Please enter a valid number: ");
            scanner.next();
        }
        int result = scanner.nextInt();
        scanner.nextLine(); // consume newline
        return result;
    }

    private double getDoubleInput(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextDouble()) {
            System.out.print("Please enter a valid number: ");
            scanner.next();
        }
        double result = scanner.nextDouble();
        scanner.nextLine(); // consume newline
        return result;
    }

    public static void main(String[] args) {
        PokerAssistant assistant = new PokerAssistant();
        assistant.start();
    }
}
