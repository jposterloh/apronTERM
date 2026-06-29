package dev.apronterm.terminal;

import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.project.TabSpec;
import dev.apronterm.wt.WtProfile;
import dev.apronterm.wt.WtSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates {@link TerminalTab}s by launching a profile's shell on a pseudo-console. */
public final class TerminalFactory {

    private static final int INITIAL_COLS = 80;
    private static final int INITIAL_ROWS = 24;

    private final TerminalTheme theme;
    private final ApronTermConfig config;

    public TerminalFactory(TerminalTheme theme, ApronTermConfig config) {
        this.theme = theme;
        this.config = config;
    }

    public TerminalTab create(TabSpec spec, WtSettings settings) throws IOException {
        WtProfile profile = settings.byName(spec.profileName);
        if (profile == null) {
            throw new IOException("Profile not found: '" + spec.profileName
                    + "'. Open Profiles → Edit settings.json to check the name.");
        }

        List<String> argv = buildCommand(profile);

        // An explicit starting directory (from the tab or the profile) wins over the shell's own
        // default. We also signal it via APRONTERM_KEEP_CWD so shell rc-files (e.g. msys2's
        // .bashrc.local) can skip their own `cd` when apronTERM already provided a directory.
        String explicitDir = explicitDirectory(spec, profile);
        String dir = explicitDir != null ? explicitDir : System.getProperty("user.home");

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        if (explicitDir != null) {
            env.put("APRONTERM_KEEP_CWD", "1");
        }

        PtyProcess process = new PtyProcessBuilder()
                .setCommand(argv.toArray(new String[0]))
                .setDirectory(dir)
                .setEnvironment(env)
                .setInitialColumns(INITIAL_COLS)
                .setInitialRows(INITIAL_ROWS)
                .setConsole(false)
                // Use real ConPTY (bundled OpenConsole.exe), not pty4j's default legacy WinPTY.
                // WinPTY scrapes a hidden console and has no VT mouse-input passthrough, so
                // full-screen TUIs (e.g. Claude Code) can't get mouse clicks/scroll; ConPTY relays
                // them. pty4j falls back to WinPTY automatically if ConPTY can't start.
                .setUseWinConPty(true)
                .start();

        ThemedTerminalWidget widget = new ThemedTerminalWidget(INITIAL_COLS, INITIAL_ROWS,
                new ThemedSettingsProvider(theme, config));
        TtyConnector connector = new PtyProcessTtyConnector(process, StandardCharsets.UTF_8, argv);
        widget.setTtyConnector(connector);
        widget.start();

        if (spec.command != null && !spec.command.isBlank()) {
            sendStartupCommand(connector, spec.command);
        }

        return new TerminalTab(spec, widget, process);
    }

    /**
     * Send a startup command to the freshly started shell. A short delay gives the shell time to
     * initialise (source its rc files) before the input arrives, so it isn't swallowed.
     */
    private void sendStartupCommand(TtyConnector connector, String command) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(500);
                connector.write(command + "\r");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // terminal may have closed already
            }
        }, "apronterm-startup-command");
        t.setDaemon(true);
        t.start();
    }

    private List<String> buildCommand(WtProfile profile) throws IOException {
        if (profile.commandline != null && !profile.commandline.isBlank()) {
            return CommandLine.resolve(profile.commandline);
        }
        // Source-generated profiles have no commandline. Best-effort support for WSL distros.
        String source = profile.source == null ? "" : profile.source.toLowerCase();
        if (source.contains("canonical") || source.contains("wsl") || source.contains("ubuntu")
                || source.contains("debian") || source.contains("kali") || source.contains("suse")) {
            return List.of("wsl.exe", "-d", profile.name);
        }
        throw new IOException("Profile '" + profile.name + "' has no launchable commandline"
                + (profile.source != null ? " (source: " + profile.source + ")" : "")
                + ". Such profiles aren't supported yet.");
    }

    /**
     * The explicit starting directory for this tab (from the tab spec or the profile), or
     * {@code null} if none is configured (or it doesn't exist) and the default should be used.
     */
    private String explicitDirectory(TabSpec spec, WtProfile profile) {
        String dir = CommandLine.toWindowsPath(
                CommandLine.expandEnv(firstNonBlank(spec.startingDirectory, profile.startingDirectory)));
        if (dir != null) {
            try {
                Path p = Path.of(dir);
                if (Files.isDirectory(p)) {
                    return p.toString(); // canonical Windows separators for setDirectory()
                }
            } catch (java.nio.file.InvalidPathException ignored) {
                // not a usable path -> fall back to default
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
