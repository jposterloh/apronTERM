package dev.apronterm.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Bridges a pty4j {@link PtyProcess} to JediTerm's {@link com.jediterm.terminal.TtyConnector}.
 *
 * <p>Adapted from JediTerm's own demo connector (the published artifacts don't ship one).
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {

    private final PtyProcess process;

    public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset,
                                  @Nullable List<String> commandLine) {
        super(process, charset, commandLine);
        this.process = process;
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        if (isConnected()) {
            process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public String getName() {
        return "Local";
    }
}
