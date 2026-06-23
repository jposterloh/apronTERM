package dev.apronterm.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;

/**
 * The terminal colour scheme, switchable at runtime. A single instance is shared by all terminals
 * (via {@link ThemedSettingsProvider}); flipping {@link #setDark(boolean)} and refreshing the
 * widgets re-themes open tabs live.
 */
public final class TerminalTheme {

    private volatile boolean dark;

    public TerminalTheme(boolean dark) {
        this.dark = dark;
    }

    public boolean isDark() {
        return dark;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    public TextStyle defaultStyle() {
        return dark
                ? new TextStyle(TerminalColor.rgb(204, 204, 204), TerminalColor.rgb(30, 30, 30))
                : new TextStyle(TerminalColor.rgb(40, 40, 40), TerminalColor.rgb(250, 250, 250));
    }

    public TextStyle selectionStyle() {
        return dark
                ? new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(60, 90, 140))
                : new TextStyle(TerminalColor.BLACK, TerminalColor.rgb(180, 205, 255));
    }
}
