package dev.apronterm.terminal;

import dev.apronterm.app.AppPaths;
import dev.apronterm.project.TabSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Wires persistent, per-tab command history into launched shells, purely via environment
 * variables. A shell that doesn't know a variable simply ignores it, so no shell detection is
 * needed and the same set is safe for every profile:
 *
 * <ul>
 *   <li><b>bash</b> (Git Bash / MSYS2 / Cygwin): {@code HISTFILE} points at a per-project/per-tab
 *       file under {@code %LOCALAPPDATA%\apronTERM\history}, so tabs no longer clobber each
 *       other's {@code ~/.bash_history} on exit. {@code PROMPT_COMMAND=history -a} appends after
 *       every command, so history survives a killed tab too (a user rc-file that sets its own
 *       {@code PROMPT_COMMAND} simply overrides this, everything else keeps working).</li>
 *   <li><b>cmd.exe</b>: has no history persistence of its own (doskey history dies with the
 *       console) and there is no API to preload it. If <a href="https://chrisant996.github.io/clink/">clink</a>
 *       is installed, {@code CLINK_HISTORY_LABEL} (read natively by clink) gives each tab its own
 *       saved history file inside the user's normal clink profile; without clink the variable is
 *       inert.</li>
 *   <li><b>PowerShell</b> persists history itself via PSReadLine and is left untouched.</li>
 *   <li><b>WSL</b> doesn't inherit Win32 environment variables (WSLENV would be needed), so WSL
 *       tabs keep their in-distro history behaviour.</li>
 * </ul>
 */
public final class ShellHistory {

    /** clink caps history labels at 32 characters. */
    private static final int CLINK_LABEL_MAX = 32;

    private ShellHistory() {
    }

    /**
     * Add the history-related variables for one tab to {@code env}. {@code projectName} scopes the
     * files so equally named tabs in different projects don't share history.
     */
    public static void configure(Map<String, String> env, String projectName, TabSpec spec) {
        String project = sanitize(projectName);
        String tab = sanitize(spec.effectiveTitle());

        try {
            Path dir = AppPaths.historyDir().resolve(project);
            Files.createDirectories(dir);
            // Forward slashes so MSYS/Cygwin bash accepts the Windows path as-is.
            String histFile = dir.resolve(tab + ".bash_history").toString().replace('\\', '/');
            env.put("HISTFILE", histFile);
        } catch (IOException e) {
            System.err.println("Could not create history dir, bash keeps its default history: " + e);
        }

        // Generous defaults; values the user set globally win.
        env.putIfAbsent("HISTSIZE", "50000");
        env.putIfAbsent("HISTFILESIZE", "50000");
        env.putIfAbsent("PROMPT_COMMAND", "history -a");

        String label = project + "-" + tab;
        if (label.length() > CLINK_LABEL_MAX) {
            // Rebalance instead of chopping the end, so the tab part (which distinguishes the
            // histories within a project) always survives.
            label = trimTo(project, CLINK_LABEL_MAX - 13) + "-" + trimTo(tab, 12);
        }
        env.put("CLINK_HISTORY_LABEL", label);
    }

    private static String trimTo(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Turn a project/tab name into a safe file-name component. */
    private static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "default";
        }
        String cleaned = s.trim().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (cleaned.isBlank()) {
            return "default";
        }
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
