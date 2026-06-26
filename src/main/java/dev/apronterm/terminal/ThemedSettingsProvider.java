package dev.apronterm.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import dev.apronterm.app.ApronTermConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.Font;

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
}
