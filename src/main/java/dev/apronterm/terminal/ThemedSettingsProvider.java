package dev.apronterm.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import dev.apronterm.app.ApronTermConfig;
import org.jetbrains.annotations.NotNull;

import javax.swing.KeyStroke;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/** A JediTerm settings provider that takes its colours from the shared {@link TerminalTheme}. */
public final class ThemedSettingsProvider extends DefaultSettingsProvider {

    private final TerminalTheme theme;
    private final ApronTermConfig config;

    public ThemedSettingsProvider(TerminalTheme theme, ApronTermConfig config) {
        this.theme = theme;
        this.config = config;
    }

    /** Primary terminal font: the configured family/size, resolved against installed fonts. (#13) */
    @Override
    public @NotNull Font getTerminalFont() {
        String family = TerminalFonts.resolvePrimaryFamily(config.terminalFont);
        return new Font(family, Font.PLAIN, TerminalFonts.resolveSize(config.terminalFontSize));
    }

    @Override
    public float getTerminalFontSize() {
        return TerminalFonts.resolveSize(config.terminalFontSize);
    }

    @SuppressWarnings("deprecation") // overriding covers getDefaultStyle() and the fg/bg getters
    @Override
    public @NotNull TextStyle getDefaultStyle() {
        return theme.defaultStyle();
    }

    @Override
    public @NotNull TextStyle getSelectionColor() {
        return theme.selectionStyle();
    }

    // ---- Copy / paste (#17) -----------------------------------------------

    /** Selecting text with the mouse copies it immediately. (#17) */
    @Override
    public boolean copyOnSelect() {
        return true;
    }

    /** Middle mouse button pastes (X11 style); the right-click context menu is kept. (#17) */
    @Override
    public boolean pasteOnMiddleMouseClick() {
        return true;
    }

    /**
     * Copy on Ctrl+C (plus Ctrl+Shift+C). The copy action is selection-gated, so with no selection
     * Ctrl+C falls through to the shell as SIGINT. (#17)
     */
    @Override
    public TerminalActionPresentation getCopyActionPresentation() {
        return new TerminalActionPresentation("Copy", List.of(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
    }

    /** Paste on Ctrl+V, Shift+Insert (plus Ctrl+Shift+V). (#17) */
    @Override
    public TerminalActionPresentation getPasteActionPresentation() {
        return new TerminalActionPresentation("Paste", List.of(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
    }
}
