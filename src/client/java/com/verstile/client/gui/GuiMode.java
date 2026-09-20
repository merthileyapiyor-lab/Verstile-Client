package com.verstile.client.gui;

/** Selects whether Right Shift opens the in-game or native overlay GUI. */
public enum GuiMode {
    CLIENT,
    INJECTION;

    private static volatile GuiMode selected = CLIENT;

    public static GuiMode selected() {
        return selected;
    }

    public static void select(GuiMode mode) {
        selected = mode == null ? CLIENT : mode;
    }

    public static void select(String value) {
        try {
            select(GuiMode.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (RuntimeException ignored) {
            select(CLIENT);
        }
    }
}
