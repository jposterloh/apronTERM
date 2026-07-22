package dev.apronterm.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;

/**
 * The terminal colour scheme, switchable at runtime. A single instance is shared by all terminals
 * (via {@link ThemedSettingsProvider}); flipping {@link #setDark(boolean)} and refreshing the
 * widgets re-themes open tabs live.
 */
public final class TerminalTheme {

    // ANSI 16-colour palettes (VS Code's terminal palettes). JediTerm's built-in WINDOWS_PALETTE
    // uses the legacy console colours, whose dark red/blue/magenta are unreadable on a dark
    // background — these are tuned for contrast on the respective background instead.
    private static final ColorPalette DARK_PALETTE = new FixedPalette(new int[]{
            0x000000, 0xCD3131, 0x0DBC79, 0xE5E510, 0x2472C8, 0xBC3FBC, 0x11A8CD, 0xE5E5E5,
            0x666666, 0xF14C4C, 0x23D18B, 0xF5F543, 0x3B8EEA, 0xD670D6, 0x29B8DB, 0xFFFFFF});

    private static final ColorPalette LIGHT_PALETTE = new FixedPalette(new int[]{
            0x000000, 0xCD3131, 0x00BC00, 0x949800, 0x0451A5, 0xBC05BC, 0x0598BC, 0x555555,
            0x666666, 0xCD3131, 0x14CE14, 0xB5BA00, 0x0451A5, 0xBC05BC, 0x0598BC, 0xA5A5A5});

    private static final class FixedPalette extends ColorPalette {
        private final Color[] colors;

        FixedPalette(int[] rgb) {
            colors = new Color[rgb.length];
            for (int i = 0; i < rgb.length; i++) {
                colors[i] = new Color(rgb[i]);
            }
        }

        @Override
        protected Color getForegroundByColorIndex(int index) {
            return colors[index];
        }

        @Override
        protected Color getBackgroundByColorIndex(int index) {
            return colors[index];
        }
    }

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

    /** The ANSI 16-colour palette matching the current mode. */
    public ColorPalette palette() {
        return dark ? DARK_PALETTE : LIGHT_PALETTE;
    }

    public TextStyle selectionStyle() {
        return dark
                ? new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(60, 90, 140))
                : new TextStyle(TerminalColor.BLACK, TerminalColor.rgb(180, 205, 255));
    }
}
