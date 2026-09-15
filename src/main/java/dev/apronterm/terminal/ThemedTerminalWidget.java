package dev.apronterm.terminal;

import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    /**
     * Same as the stock starter, but with {@link ClipboardOscEmulator} so OSC 52 copies (e.g. from
     * Claude Code's own mouse selection) reach the system clipboard.
     */
    @Override
    protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
        TerminalTypeAheadManager typeAhead = getTypeAheadManager();
        return new TerminalStarter(terminal, connector,
                new TtyBasedArrayDataStream(connector, typeAhead::onTerminalStateChanged),
                typeAhead, getExecutorServiceManager()) {
            @Override
            protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal t) {
                return new ClipboardOscEmulator(dataStream, t);
            }
        };
    }

    /** Run {@code handler} on the EDT whenever this terminal rings the bell (BEL). (#12) */
    public void setBellHandler(Runnable handler) {
        ((FallbackTerminalPanel) getTerminalPanel()).bellHandler = handler;
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

        private final SettingsProvider settings;
        private List<Font> fallbackBases; // PLAIN bases of the fallback chain, resolved lazily
        // Per-codepoint resolution cache for the all-fonts last-resort scan (null = no font found).
        private final Map<Integer, Font> perCodepoint = new HashMap<>();
        // Notified on a real BEL (set by ThemedTerminalWidget.setBellHandler). (#12)
        private volatile Runnable bellHandler;
        // True after a left-drag was forwarded to an app that owns the mouse (e.g. Claude Code
        // fullscreen), until the next click, key press or mouse-mode change: the app then likely
        // holds a selection of its own, which the popup's Copy can ask it to copy.
        private boolean appSelectionPending;

        FallbackTerminalPanel(SettingsProvider settingsProvider, TerminalTextBuffer textBuffer,
                              StyleState styleState) {
            super(settingsProvider, textBuffer, styleState);
            this.settings = settingsProvider;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        appSelectionPending = false;
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && isRemoteMouseAction(e)) {
                        appSelectionPending = true;
                    }
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    appSelectionPending = false;
                }
            });
        }

        @Override
        public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
            super.terminalMouseModeSet(mouseMode);
            appSelectionPending = false;
        }

        /**
         * The popup's Copy only knows JediTerm's own selection, so it stays disabled for a selection
         * made inside a mouse-driven TUI. In that case (popup only, never the keyboard path) it sends
         * Ctrl+C to the app instead, which is how Claude Code copies its selection (via OSC 52).
         */
        @Override
        public List<TerminalAction> getActions() {
            String copyName = settings.getCopyActionPresentation().getName();
            List<TerminalAction> actions = new ArrayList<>(super.getActions());
            for (int i = 0; i < actions.size(); i++) {
                TerminalAction copy = actions.get(i);
                if (copy.getName().equals(copyName)) {
                    actions.set(i, appAwareCopy(copy));
                    break;
                }
            }
            return actions;
        }

        private TerminalAction appAwareCopy(TerminalAction copy) {
            TerminalAction action = new TerminalAction(copy.getPresentation(), e -> {
                if (copiesAppSelection(e)) {
                    appSelectionPending = false;
                    TerminalOutputStream out = getTerminalOutputStream();
                    if (out != null) {
                        out.sendBytes(new byte[]{3}, true); // Ctrl+C
                    }
                    return true;
                }
                return copy.actionPerformed(e);
            }) {
                @Override
                public boolean isEnabled(@Nullable KeyEvent e) {
                    return copy.isEnabled(e) || copiesAppSelection(e);
                }
            };
            return action.withMnemonicKey(copy.getMnemonicKeyCode());
        }

        private boolean copiesAppSelection(@Nullable KeyEvent e) {
            return e == null && appSelectionPending && getSelection() == null;
        }

        // Fires only on a genuine bell (not on the 0x07 that terminates an OSC sequence). (#12)
        // We play our own tone (AudibleBell) instead of super.beep(), whose Toolkit.beep() is
        // silent when the Windows "Default Beep" sound is set to None.
        @Override
        public void beep() {
            AudibleBell.play();
            Runnable h = bellHandler;
            if (h != null) {
                SwingUtilities.invokeLater(h);
            }
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
