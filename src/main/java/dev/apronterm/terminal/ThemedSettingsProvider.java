package dev.apronterm.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import org.jetbrains.annotations.NotNull;

/** A JediTerm settings provider that takes its colours from the shared {@link TerminalTheme}. */
public final class ThemedSettingsProvider extends DefaultSettingsProvider {

    private final TerminalTheme theme;

    public ThemedSettingsProvider(TerminalTheme theme) {
        this.theme = theme;
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
