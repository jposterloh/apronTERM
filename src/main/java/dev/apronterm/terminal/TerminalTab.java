package dev.apronterm.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.pty4j.PtyProcess;
import dev.apronterm.project.TabSpec;

/** A live terminal: the JediTerm widget, its pty process, and the spec it was created from. */
public final class TerminalTab {

    private final TabSpec spec;
    private final JediTermWidget widget;
    private final PtyProcess process;

    public TerminalTab(TabSpec spec, JediTermWidget widget, PtyProcess process) {
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

    public boolean isAlive() {
        return process.isAlive();
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
