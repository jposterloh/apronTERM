package dev.apronterm.terminal;

import dev.apronterm.app.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a tab's shell with administrator rights. A non-elevated process cannot start an elevated
 * child on its own ConPTY (elevation only happens via UAC/ShellExecute, which detaches the new
 * process), so a sudo-style bridge is prepended to the commandline instead:
 *
 * <ul>
 *   <li><a href="https://github.com/gerardog/gsudo">gsudo</a>, preferred: purpose-built for this
 *       and attaches the elevated process to the current console/ConPTY.</li>
 *   <li>Windows 11's built-in {@code sudo.exe} (24H2+). Must be enabled under
 *       <i>Settings &gt; System &gt; For developers</i>; only its "Inline" mode keeps the elevated
 *       shell in the tab, the default "In a new window" mode opens a separate window (the tab then
 *       just closes again).</li>
 * </ul>
 *
 * The UAC prompt appears when the tab opens.
 */
final class Elevation {

    private Elevation() {
    }

    /** Prepend the elevation bridge to {@code argv}, or fail with a user-readable message. */
    static List<String> wrap(List<String> argv) throws IOException {
        String bridge = findBridge();
        if (bridge == null) {
            throw new IOException(I18n.t("error.elevation.missing"));
        }
        List<String> out = new ArrayList<>();
        out.add(bridge);
        out.addAll(argv);
        return out;
    }

    /** Full path of gsudo (searched on PATH) or the built-in sudo, or {@code null} if neither exists. */
    private static String findBridge() {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(";")) {
                if (entry.isBlank()) {
                    continue;
                }
                try {
                    Path p = Path.of(entry.trim(), "gsudo.exe");
                    if (Files.isRegularFile(p)) {
                        return p.toString();
                    }
                } catch (InvalidPathException ignored) {
                    // malformed PATH entry, skip
                }
            }
        }
        String sysRoot = System.getenv("SystemRoot");
        Path sudo = Path.of(sysRoot != null ? sysRoot : "C:\\Windows", "System32", "sudo.exe");
        return Files.isRegularFile(sudo) && sudoUsable(sudo) ? sudo.toString() : null;
    }

    /**
     * sudo.exe can exist but be turned off (Settings toggle or group policy); {@code sudo config}
     * then exits non-zero. Launching through it anyway would only flash its error text into a tab
     * that immediately closes again, so treat that as "no bridge" and show our own hint instead.
     */
    private static boolean sudoUsable(Path sudo) {
        try {
            Process p = new ProcessBuilder(sudo.toString(), "config")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
