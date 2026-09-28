package com.pokerassistant.cli;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.equity.EquityRequest;
import com.pokerassistant.equity.Range;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.Street;
import com.pokerassistant.game.TableConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an input line into validated {@link Command}s. Everything that can be checked without game
 * state is checked here (command names, argument counts, amounts, seats, cards, ranges) and reported
 * with a usage hint. Rules that depend on the hand, such as whose turn it is or the minimum raise, are
 * enforced by the game engine when the command runs.
 *
 * <p>Syntax: commands are separated by {@code ;}, text after {@code #} is a comment, and a line that
 * starts with a betting action is a sequence of actions: {@code f f r 2.5 c}.
 */
public final class CommandParser {

    static final int MAX_LINE_LENGTH = 500;
    private static final int MAX_ACTIONS_PER_LINE = 30;
    private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
    private static final Pattern COMPACT_SIZED_ACTION = Pattern.compile("([br])(\\d{1,13}(?:\\.\\d{1,2})?)");
    private static final Pattern PERCENTAGE = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,2})?)%?");

    private static final Map<String, ActionType> ACTION_WORDS = Map.ofEntries(
            Map.entry("f", ActionType.FOLD), Map.entry("fold", ActionType.FOLD),
            Map.entry("x", ActionType.CHECK), Map.entry("k", ActionType.CHECK), Map.entry("check", ActionType.CHECK),
            Map.entry("c", ActionType.CALL), Map.entry("call", ActionType.CALL),
            Map.entry("b", ActionType.BET), Map.entry("bet", ActionType.BET),
            Map.entry("r", ActionType.RAISE), Map.entry("raise", ActionType.RAISE),
            Map.entry("a", ActionType.ALL_IN), Map.entry("allin", ActionType.ALL_IN), Map.entry("all-in", ActionType.ALL_IN),
            Map.entry("shove", ActionType.ALL_IN), Map.entry("jam", ActionType.ALL_IN));

    /** Command words offered as "did you mean" suggestions. */
    private static final List<String> COMMAND_WORDS = List.of(
            "help", "quit", "exit", "status", "setup", "stack", "button", "new", "deal", "hole", "cards", "flop", "turn",
            "river", "board", "show", "muck", "winner", "advise", "equity", "odds", "range", "ranges", "undo", "history",
            "abort", "fsm", "auto", "fold", "check", "call", "bet", "raise", "allin", "shove");

    public List<Command> parse(String line) {
        if (line == null) {
            return List.of();
        }
        if (line.length() > MAX_LINE_LENGTH) {
            throw new InvalidInputException("Input is too long (max " + MAX_LINE_LENGTH + " characters)");
        }
        // Byte-order marks sneak in from Windows editors and PowerShell pipes; they are never meaningful.
        String cleaned = line.replace("﻿", "");
        int comment = cleaned.indexOf('#');
        String content = comment >= 0 ? cleaned.substring(0, comment) : cleaned;
        List<Command> commands = new ArrayList<>();
        for (String segment : content.split(";")) {
            String trimmed = segment.strip();
            if (!trimmed.isEmpty()) {
                commands.add(parseCommand(trimmed));
            }
        }
        return commands;
    }

    private Command parseCommand(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                throw new InvalidInputException("Input contains control characters");
            }
        }
        List<String> tokens = List.of(text.split("\\s+"));
        String head = tokens.get(0).toLowerCase(Locale.ROOT);
        List<String> args = tokens.subList(1, tokens.size());
        if (ACTION_WORDS.containsKey(head) || COMPACT_SIZED_ACTION.matcher(head).matches()) {
            return parseActions(tokens);
        }
        return switch (head) {
            case "help", "h" -> parseHelp(args);
            case "quit", "exit", "q" -> noArgs(head, args, new Command.Quit());
            case "status", "s" -> noArgs(head, args, new Command.Status());
            case "setup" -> parseSetup(args);
            case "stack" -> parseStack(args);
            case "button", "btn" -> new Command.MoveButton(parseSeat(single(head, args, "<seat>")));
            case "new", "deal", "n" -> new Command.NewHand(args.isEmpty() ? 0L : CardMask.parseExactly(String.join("", args), 2, "Hero's hand"));
            case "hole", "cards" -> new Command.SetHoleCards(CardMask.parseExactly(joined(head, args, "<cards>, e.g. 'hole AhKd'"), 2, "Hero's hand"));
            case "flop" -> parseBoard(head, args, Street.FLOP);
            case "turn" -> parseBoard(head, args, Street.TURN);
            case "river" -> parseBoard(head, args, Street.RIVER);
            case "board" -> parseBoard(head, args, null);
            case "show" -> parseShow(args);
            case "muck" -> new Command.Muck(parseSeat(single(head, args, "<seat>")));
            case "winner", "winners" -> parseWinners(args);
            case "advise", "advice", "?" -> noArgs(head, args, new Command.Advise());
            case "equity", "eq" -> parseEquity(args);
            case "odds" -> parseOdds(args);
            case "range" -> parseRange(args);
            case "ranges" -> noArgs(head, args, new Command.ShowRanges());
            case "undo", "u" -> noArgs(head, args, new Command.Undo());
            case "history", "log" -> noArgs(head, args, new Command.History());
            case "abort" -> noArgs(head, args, new Command.Abort());
            case "fsm" -> noArgs(head, args, new Command.ShowFsm());
            case "auto" -> parseAuto(args);
            default -> throw unknownCommand(tokens.get(0));
        };
    }

    /** A line that starts with a betting action: {@code f}, {@code x}, {@code c 2}, {@code r 7.5}, {@code b3}, {@code a}. */
    private static Command parseActions(List<String> tokens) {
        List<Command.ActionInput> actions = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).toLowerCase(Locale.ROOT);
            ActionType type = ACTION_WORDS.get(token);
            Matcher compact = COMPACT_SIZED_ACTION.matcher(token);
            if (type == null && compact.matches()) {
                ActionType sized = compact.group(1).equals("b") ? ActionType.BET : ActionType.RAISE;
                actions.add(new Command.ActionInput(sized, Chips.parse(compact.group(2))));
            } else if (type == null) {
                throw new InvalidInputException("'" + tokens.get(i) + "' is not a betting action (use f, x, c, b <amount>, r <amount> or a)");
            } else if (type.isSized()) {
                if (i + 1 >= tokens.size()) {
                    throw new InvalidInputException("'%s' needs an amount, e.g. '%s 6'%s".formatted(
                            token, token, type == ActionType.RAISE ? " (raise TO 6: the street total)" : ""));
                }
                actions.add(new Command.ActionInput(type, Chips.parse(tokens.get(++i))));
            } else {
                actions.add(new Command.ActionInput(type, null));
            }
            if (actions.size() > MAX_ACTIONS_PER_LINE) {
                throw new InvalidInputException("Too many actions on one line (max " + MAX_ACTIONS_PER_LINE + ")");
            }
        }
        return new Command.Act(actions);
    }

    private static Command parseHelp(List<String> args) {
        if (args.size() > 1) {
            throw new InvalidInputException("Usage: help [topic]");
        }
        return new Command.Help(args.isEmpty() ? null : args.get(0).toLowerCase(Locale.ROOT));
    }

    private static Command parseSetup(List<String> args) {
        if (args.isEmpty()) {
            throw new InvalidInputException("Usage: setup key=value ... with keys players, stack, sb, bb, ante, button, hero"
                    + " (e.g. 'setup players=6 stack=100 sb=0.5 bb=1 hero=3 button=1')");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (equals <= 0 || equals == arg.length() - 1) {
                throw new InvalidInputException("Expected key=value, got '" + arg + "'");
            }
            String key = setupKey(arg.substring(0, equals).toLowerCase(Locale.ROOT));
            if (values.put(key, arg.substring(equals + 1)) != null) {
                throw new InvalidInputException("'" + key + "' is given twice");
            }
        }
        Integer players = values.containsKey("players") ? parseCount(values.get("players")) : null;
        Chips stack = values.containsKey("stack") ? positive(values.get("stack"), "stack") : null;
        Chips smallBlind = values.containsKey("sb") ? positive(values.get("sb"), "sb") : null;
        Chips bigBlind = values.containsKey("bb") ? positive(values.get("bb"), "bb") : null;
        Chips ante = values.containsKey("ante") ? Chips.parse(values.get("ante")) : null;
        Integer button = values.containsKey("button") ? parseSeat(values.get("button")) : null;
        Integer hero = values.containsKey("hero") ? parseSeat(values.get("hero")) : null;
        return new Command.Setup(players, stack, smallBlind, bigBlind, ante, button, hero);
    }

    private static String setupKey(String key) {
        return switch (key) {
            case "players", "seats", "n" -> "players";
            case "stack", "stacks" -> "stack";
            case "sb" -> "sb";
            case "bb" -> "bb";
            case "ante" -> "ante";
            case "button", "btn" -> "button";
            case "hero" -> "hero";
            default -> throw new InvalidInputException("Unknown setup key '" + key + "' (use players, stack, sb, bb, ante, button, hero)");
        };
    }

    private static Command parseStack(List<String> args) {
        if (args.size() != 2) {
            throw new InvalidInputException("Usage: stack <seat> <amount>, e.g. 'stack 3 150' (0 sits the seat out)");
        }
        return new Command.SetStack(parseSeat(args.get(0)), Chips.parse(args.get(1)));
    }

    private static Command parseBoard(String head, List<String> args, Street street) {
        String usage = street == Street.FLOP ? "<c1> <c2> <c3>, e.g. 'flop Kh 7d 2c'"
                : street == null ? "<cards>, e.g. 'board Kh 7d 2c 5s 9h'"
                : "<card>, e.g. '" + head + " 5s'";
        List<Card> cards = CardMask.parseList(joined(head, args, usage));
        int expected = street == Street.FLOP ? 3 : street == null ? -1 : 1;
        if (expected > 0 && cards.size() != expected) {
            throw new InvalidInputException("The %s needs exactly %d card%s, got %d".formatted(head, expected, expected == 1 ? "" : "s", cards.size()));
        }
        if (cards.size() > 5) {
            throw new InvalidInputException("A board has at most 5 cards, got " + cards.size());
        }
        return new Command.DealBoard(street, cards);
    }

    private static Command parseShow(List<String> args) {
        if (args.size() < 2) {
            throw new InvalidInputException("Usage: show <seat> <cards>, e.g. 'show 4 QsQd'");
        }
        int seat = parseSeat(args.get(0));
        return new Command.Show(seat, CardMask.parseExactly(String.join("", args.subList(1, args.size())), 2, "Seat " + seat + "'s hand"));
    }

    private static Command parseWinners(List<String> args) {
        if (args.isEmpty()) {
            throw new InvalidInputException("Usage: winner <seat> [<seat> ...], e.g. 'winner 3' or 'winner 3 5' to split");
        }
        List<Integer> seats = new ArrayList<>();
        for (String arg : args) {
            int seat = parseSeat(arg);
            if (seats.contains(seat)) {
                throw new InvalidInputException("Seat " + seat + " is listed twice");
            }
            seats.add(seat);
        }
        return new Command.Winners(seats);
    }

    /** {@code equity} alone, or {@code equity <hero> [vs <range>]... [board <cards>]}. */
    private static Command parseEquity(List<String> args) {
        if (args.isEmpty()) {
            return new Command.EquityQuery(0L, List.of(), 0L);
        }
        int i = 0;
        StringBuilder heroText = new StringBuilder();
        while (i < args.size() && !isEquityKeyword(args.get(i))) {
            heroText.append(args.get(i++));
        }
        if (heroText.isEmpty()) {
            throw new InvalidInputException("Give hero's cards first, e.g. 'equity AhKd vs QQ+,AKs board Kh7d2c'");
        }
        long hero = CardMask.parseExactly(heroText.toString(), 2, "Hero's hand");
        List<Range> villains = new ArrayList<>();
        long board = 0;
        boolean boardGiven = false;
        while (i < args.size()) {
            String keyword = args.get(i++).toLowerCase(Locale.ROOT);
            List<String> value = new ArrayList<>();
            while (i < args.size() && !isEquityKeyword(args.get(i))) {
                value.add(args.get(i++));
            }
            if (value.isEmpty()) {
                throw new InvalidInputException("'" + keyword + "' needs a value");
            }
            if (keyword.equals("vs")) {
                villains.add(Range.parse(String.join(",", value)));
            } else {
                if (boardGiven) {
                    throw new InvalidInputException("'board' is given twice");
                }
                List<Card> boardCards = CardMask.parseList(String.join("", value));
                if (boardCards.size() < 3 || boardCards.size() > 5) {
                    throw new InvalidInputException("The board must have 3, 4 or 5 cards, got " + boardCards.size());
                }
                board = CardMask.of(boardCards);
                boardGiven = true;
            }
        }
        if (villains.size() > EquityRequest.MAX_OPPONENTS) {
            throw new InvalidInputException("At most " + EquityRequest.MAX_OPPONENTS + " opponents");
        }
        return new Command.EquityQuery(hero, villains.isEmpty() ? List.of(Range.all()) : villains, board);
    }

    private static boolean isEquityKeyword(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.equals("vs") || lower.equals("board");
    }

    private static Command parseOdds(List<String> args) {
        if (args.size() < 2 || args.size() > 3) {
            throw new InvalidInputException("Usage: odds <pot before the bet> <bet> [your equity %], e.g. 'odds 10 5 30'");
        }
        Chips pot = positive(args.get(0), "The pot");
        Chips bet = positive(args.get(1), "The bet");
        Double equity = null;
        if (args.size() == 3) {
            Matcher matcher = PERCENTAGE.matcher(args.get(2));
            if (!matcher.matches() || Double.parseDouble(matcher.group(1)) > 100) {
                throw new InvalidInputException("Equity must be a percentage from 0 to 100, e.g. 35 or 35%");
            }
            equity = Double.parseDouble(matcher.group(1));
        }
        return new Command.Odds(pot, bet, equity);
    }

    private static Command parseRange(List<String> args) {
        if (args.size() < 2) {
            throw new InvalidInputException("Usage: range <seat> <range|auto>, e.g. 'range 4 22+,ATs+,KQs,AJo+'");
        }
        int seat = parseSeat(args.get(0));
        String notation = String.join(" ", args.subList(1, args.size()));
        return new Command.SetRange(seat, notation.equalsIgnoreCase("auto") ? null : Range.parse(notation));
    }

    private static Command parseAuto(List<String> args) {
        return switch (single("auto", args, "on|off").toLowerCase(Locale.ROOT)) {
            case "on" -> new Command.AutoAdvice(true);
            case "off" -> new Command.AutoAdvice(false);
            default -> throw new InvalidInputException("Usage: auto on|off");
        };
    }

    static int parseSeat(String text) {
        if (!NUMBER.matcher(text).matches()) {
            throw new InvalidInputException("'" + text + "' is not a seat number (1-" + TableConfig.MAX_SEATS + ")");
        }
        int seat = Integer.parseInt(text);
        if (seat < 1 || seat > TableConfig.MAX_SEATS) {
            throw new InvalidInputException("Seat numbers run from 1 to " + TableConfig.MAX_SEATS + ", got " + seat);
        }
        return seat;
    }

    private static int parseCount(String text) {
        if (!NUMBER.matcher(text).matches()) {
            throw new InvalidInputException("'" + text + "' is not a number of players");
        }
        int players = Integer.parseInt(text);
        if (players < TableConfig.MIN_SEATS || players > TableConfig.MAX_SEATS) {
            throw new InvalidInputException("Players must be between %d and %d, got %d".formatted(TableConfig.MIN_SEATS, TableConfig.MAX_SEATS, players));
        }
        return players;
    }

    private static Chips positive(String text, String what) {
        Chips amount = Chips.parse(text);
        if (!amount.isPositive()) {
            throw new InvalidInputException(what + " must be greater than zero");
        }
        return amount;
    }

    private static Command noArgs(String head, List<String> args, Command command) {
        if (!args.isEmpty()) {
            throw new InvalidInputException("'" + head + "' takes no arguments");
        }
        return command;
    }

    private static String single(String head, List<String> args, String usage) {
        if (args.size() != 1) {
            throw new InvalidInputException("Usage: " + head + " " + usage);
        }
        return args.get(0);
    }

    private static String joined(String head, List<String> args, String usage) {
        if (args.isEmpty()) {
            throw new InvalidInputException("Usage: " + head + " " + usage);
        }
        return String.join(" ", args);
    }

    private static InvalidInputException unknownCommand(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : COMMAND_WORDS) {
            int distance = editDistance(lower, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        String suggestion = bestDistance <= 2 && lower.length() >= 3 ? " Did you mean '" + best + "'?" : "";
        return new InvalidInputException("Unknown command '" + word + "'." + suggestion + " Type 'help' for the list.");
    }

    /** Levenshtein distance. */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
