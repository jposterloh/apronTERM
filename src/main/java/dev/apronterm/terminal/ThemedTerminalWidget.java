package dev.apronterm.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link JediTermWidget} that captures its {@link StyleState} so the default colours can be
 * swapped at runtime — letting an already-open terminal pick up a theme change.
 *
 * <p>It also adds per-glyph font fallback (#13): JediTerm draws each cell with the single primary
 * font and shows a rectangle for any glyph that font lacks. We override {@code getFontToDisplay} so
 * characters the primary font can't render fall back to a well-covered font (a Nerd Font if
 * installed), mirroring Windows Terminal's behaviour.
 */
public final class ThemedTerminalWidget extends JediTermWidget {

    // NOTE: no initializer — createDefaultStyle() runs inside super() and assigns this; an
    // initializer would run afterwards and overwrite the captured reference with null.
    private StyleState capturedStyleState;

    public ThemedTerminalWidget(int columns, int lines, SettingsProvider settingsProvider) {
        super(columns, lines, settingsProvider);
    }

    @Override
    protected StyleState createDefaultStyle() {
        capturedStyleState = super.createDefaultStyle();
        return capturedStyleState;
    }

    @Override
    protected TerminalPanel createTerminalPanel(SettingsProvider settingsProvider, StyleState styleState,
                                                TerminalTextBuffer textBuffer) {
        return new FallbackTerminalPanel(settingsProvider, textBuffer, styleState);
    }

    /** Apply the current theme's default colours to this (possibly already running) terminal. */
    public void applyTheme(TerminalTheme theme) {
        if (capturedStyleState != null) {
            capturedStyleState.setDefaultStyle(theme.defaultStyle());
        }
        getTerminalPanel().repaint();
    }

    /** Re-read the (possibly changed) terminal font and resize the grid to match. (#13) */
    public void applyFont() {
        ((FallbackTerminalPanel) getTerminalPanel()).applyFontChange();
    }

    /** A {@link TerminalPanel} that falls back to glyph-rich fonts for characters the primary lacks. */
    private static final class FallbackTerminalPanel extends TerminalPanel {

        private List<Font> fallbackBases; // PLAIN bases of the fallback chain, resolved lazily
        // Per-codepoint resolution cache for the all-fonts last-resort scan (null = no font found).
        private final Map<Integer, Font> perCodepoint = new HashMap<>();

        FallbackTerminalPanel(SettingsProvider settingsProvider, TerminalTextBuffer textBuffer,
                              StyleState styleState) {
            super(settingsProvider, textBuffer, styleState);
        }

        void applyFontChange() {
            reinitFontAndResize();
            repaint();
        }

        private List<Font> fallbackBases() {
            if (fallbackBases == null) {
                fallbackBases = new ArrayList<>();
                for (String family : TerminalFonts.fallbackFamilies()) {
                    fallbackBases.add(new Font(family, Font.PLAIN, 1));
                }
            }
            return fallbackBases;
        }

        // NOTE: JediTerm passes (buf, start, end) — the third arg is an exclusive END index, not a
        // length. Treat it as such (and clamp) to avoid reading past the buffer.
        @Override
        protected Font getFontToDisplay(char[] text, int start, int end, TextStyle style) {
            Font base = super.getFontToDisplay(text, start, end, style);
            int from = Math.max(0, start);
            int to = Math.min(end, text.length);
            if (from >= to || base.canDisplayUpTo(text, from, to) == -1) {
                return base; // primary covers the run (or nothing to check)
            }
            // Try the fallback chain: first font that covers the whole run wins.
            for (Font fb : fallbackBases()) {
                if (fb.canDisplayUpTo(text, from, to) == -1) {
                    return fb.deriveFont(base.getStyle(), base.getSize2D());
                }
            }
            // Last resort: resolve by the first offending codepoint, scanning all installed fonts.
            Font fb = resolveForFirstMissing(base, text, from, to);
            return (fb != null) ? fb.deriveFont(base.getStyle(), base.getSize2D()) : base;
        }

        private Font resolveForFirstMissing(Font base, char[] text, int from, int to) {
            int i = from;
            while (i < to) {
                int cp = Character.codePointAt(text, i, to);
                if (!base.canDisplay(cp)) {
                    if (!perCodepoint.containsKey(cp)) {
                        String fam = TerminalFonts.familyDisplaying(cp);
                        perCodepoint.put(cp, fam != null ? new Font(fam, Font.PLAIN, 1) : null);
                    }
                    return perCodepoint.get(cp);
                }
                i += Character.charCount(cp);
            }
            return null;
        }
    }
}
