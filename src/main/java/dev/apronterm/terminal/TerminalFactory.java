package dev.apronterm.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
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

    private final boolean dark;

    public TerminalFactory(boolean dark) {
        this.dark = dark;
    }

    private SettingsProvider settingsProvider() {
        return dark ? new DarkSettingsProvider() : new DefaultSettingsProvider();
    }

    public TerminalTab create(TabSpec spec, WtSettings settings) throws IOException {
        WtProfile profile = settings.byName(spec.profileName);
        if (profile == null) {
            throw new IOException("Profile not found: '" + spec.profileName
                    + "'. Open Profiles → Edit settings.json to check the name.");
        }

        List<String> argv = buildCommand(profile);
        String dir = resolveDirectory(spec, profile);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        PtyProcess process = new PtyProcessBuilder()
                .setCommand(argv.toArray(new String[0]))
                .setDirectory(dir)
                .setEnvironment(env)
                .setInitialColumns(INITIAL_COLS)
                .setInitialRows(INITIAL_ROWS)
                .setConsole(false)
                .start();

        JediTermWidget widget = new JediTermWidget(INITIAL_COLS, INITIAL_ROWS, settingsProvider());
        widget.setTtyConnector(new PtyProcessTtyConnector(process, StandardCharsets.UTF_8, argv));
        widget.start();

        return new TerminalTab(spec, widget, process);
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

    private String resolveDirectory(TabSpec spec, WtProfile profile) {
        String dir = firstNonBlank(spec.startingDirectory, profile.startingDirectory,
                System.getProperty("user.home"));
        dir = CommandLine.expandEnv(dir);
        // Fall back to home if the configured directory doesn't exist (avoids launch failure).
        if (dir == null || !Files.isDirectory(Path.of(dir))) {
            return System.getProperty("user.home");
        }
        return dir;
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
