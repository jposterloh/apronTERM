package dev.apronterm.terminal;

import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;

/**
 * A {@link JediTermWidget} that captures its {@link StyleState} so the default colours can be
 * swapped at runtime — letting an already-open terminal pick up a theme change.
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

    /** Apply the current theme's default colours to this (possibly already running) terminal. */
    public void applyTheme(TerminalTheme theme) {
        if (capturedStyleState != null) {
            capturedStyleState.setDefaultStyle(theme.defaultStyle());
        }
        getTerminalPanel().repaint();
    }
}
