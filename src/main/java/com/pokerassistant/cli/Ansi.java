package com.pokerassistant.cli;

/** Minimal ANSI styling that degrades to plain text when colors are off. */
final class Ansi {

    private final boolean enabled;

    Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    String bold(String text) {
        return style("1", text);
    }

    String dim(String text) {
        return style("2", text);
    }

    String red(String text) {
        return style("31", text);
    }

    String green(String text) {
        return style("32", text);
    }

    String yellow(String text) {
        return style("33", text);
    }

    String cyan(String text) {
        return style("36", text);
    }

    private String style(String code, String text) {
        return enabled ? "\u001B[" + code + "m" + text + "\u001B[0m" : text;
    }
}
