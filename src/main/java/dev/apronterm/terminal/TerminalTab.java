package dev.apronterm.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.pty4j.PtyProcess;
import dev.apronterm.project.TabSpec;

import javax.swing.SwingUtilities;

/** A live terminal: the JediTerm widget, its pty process, and the spec it was created from. */
public final class TerminalTab {

    private final TabSpec spec;
    private final ThemedTerminalWidget widget;
    private final PtyProcess process;
    /** True when this tab rang the bell and the user hasn't looked at it yet. (#12) */
    private boolean notified;

    public TerminalTab(TabSpec spec, ThemedTerminalWidget widget, PtyProcess process) {
        this.spec = spec;
        this.widget = widget;
        this.process = process;
    }

    public TabSpec spec() {
        return spec;
    }

    public JediTermWidget widget() {
        return widget;
    }

    /** Move keyboard focus into this terminal so the user can type without clicking first. */
    public void focus() {
        SwingUtilities.invokeLater(() -> widget.getTerminalPanel().requestFocusInWindow());
    }

    /** Re-apply the (possibly changed) theme to this running terminal. */
    public void applyTheme(TerminalTheme theme) {
        widget.applyTheme(theme);
    }

    /** Re-apply the (possibly changed) terminal font to this running terminal. (#13) */
    public void applyFont() {
        widget.applyFont();
    }

    /** Run {@code handler} on the EDT when this terminal rings the bell. (#12) */
    public void onBell(Runnable handler) {
        widget.setBellHandler(handler);
    }

    /** Whether this tab has an unseen bell notification. (#12) */
    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Run {@code callback} once the shell process exits (off the EDT — marshal in the callback). */
    public void onExit(Runnable callback) {
        process.onExit().thenRun(callback);
    }

    public void close() {
        try {
            widget.close();
        } catch (Exception ignored) {
        }
        try {
            process.destroy();
        } catch (Exception ignored) {
        }
    }
}
