package com.pokerassistant;

import com.pokerassistant.cli.PokerCli;
import com.pokerassistant.gui.PokerWindow;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Entry point: {@code java -jar poker-assistant.jar [--gui] [--no-color] [--echo]}. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        boolean color = System.getenv("NO_COLOR") == null;
        boolean echo = false;
        boolean gui = false;
        for (String arg : args) {
            switch (arg) {
                case "--gui" -> gui = true;
                case "--no-color" -> color = false;
                case "--echo" -> echo = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    System.err.println("Unknown option: " + arg);
                    printUsage();
                    System.exit(2);
                }
            }
        }
        // Amounts are typed with '.' decimals everywhere, so print them the same way on every OS locale.
        Locale.setDefault(Locale.Category.FORMAT, Locale.ROOT);
        if (gui) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("No display available for --gui; run without it for the command line.");
                System.exit(1);
            }
            PokerWindow.open();
            return;
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new PokerCli(input, System.out, color, echo).run();
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -jar poker-assistant.jar [options]
                  --gui        open the graphical table instead of the command line
                  --no-color   plain output (also honoured: the NO_COLOR environment variable)
                  --echo       print each input line (useful when piping a script in)
                  --help       this message""");
    }
}
