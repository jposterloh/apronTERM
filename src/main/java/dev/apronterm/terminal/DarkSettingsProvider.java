package dev.apronterm.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import org.jetbrains.annotations.NotNull;

/** A dark colour scheme for JediTerm terminals (light text on a near-black background). */
public final class DarkSettingsProvider extends DefaultSettingsProvider {

    private static final TerminalColor FOREGROUND = TerminalColor.rgb(204, 204, 204);
    private static final TerminalColor BACKGROUND = TerminalColor.rgb(30, 30, 30);

    @SuppressWarnings("deprecation") // overriding covers both getDefaultStyle() and the fg/bg getters
    @Override
    public @NotNull TextStyle getDefaultStyle() {
        return new TextStyle(FOREGROUND, BACKGROUND);
    }

    @Override
    public @NotNull TextStyle getSelectionColor() {
        return new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(60, 90, 140));
    }
}
