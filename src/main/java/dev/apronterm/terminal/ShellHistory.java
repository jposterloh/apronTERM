package dev.apronterm.terminal;

import dev.apronterm.app.AppPaths;
import dev.apronterm.project.TabSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Wires persistent, per-tab command history into launched shells, purely via environment
 * variables. A shell that doesn't know a variable simply ignores it, so no shell detection is
 * needed and the same set is safe for every profile:
 *
 * <ul>
 *   <li><b>bash</b> (Git Bash / MSYS2 / Cygwin): {@code HISTFILE} points at a per-project/per-tab
 *       file under {@code %LOCALAPPDATA%\apronTERM\history}, so tabs no longer clobber each
 *       other's {@code ~/.bash_history} on exit. The per-command append hook lives in {@code PS0},
 *       NOT {@code PROMPT_COMMAND}: MSYS2's profile.d scripts unconditionally overwrite
 *       PROMPT_COMMAND with a window-title setter, while PS0 (bash 4.4+, expanded right after a
 *       command is read) survives the whole rc chain. The hook appends exactly the just-entered
 *       command via {@code fc -ln -1}, so history survives killed tabs (tab close terminates the
 *       process, bash never gets a clean exit there).</li>
 *   <li>On a clean {@code exit}, bash additionally appends/rewrites the session's lines itself
 *       (MSYS2 enables {@code histappend}), duplicating what PS0 already wrote. That is healed by
 *       {@link #compact(Path)} at the next tab open, which dedupes keeping the last occurrence
 *       (side effect: "erasedups"-style history, repeated commands appear once).</li>
 *   <li><b>cmd.exe</b>: has no history persistence of its own (doskey history dies with the
 *       console) and there is no API to preload it. If <a href="https://chrisant996.github.io/clink/">clink</a>
 *       is installed, {@code CLINK_HISTORY_LABEL} (read natively by clink) gives each tab its own
 *       saved history file inside the user's normal clink profile; without clink the variable is
 *       inert and cmd keeps losing its history.</li>
 *   <li><b>PowerShell</b> persists history itself via PSReadLine and is left untouched.</li>
 *   <li><b>WSL</b> doesn't inherit Win32 environment variables (WSLENV would be needed), so WSL
 *       tabs keep their in-distro history behaviour.</li>
 * </ul>
 */
public final class ShellHistory {

    /** clink caps history labels at 32 characters. */
    private static final int CLINK_LABEL_MAX = 32;

    /** Cap for {@link #compact(Path)}; aligned with the HISTSIZE/HISTFILESIZE defaults below. */
    private static final int MAX_LINES = 50_000;

    /**
     * Per-command append hook for bash, set as {@code PS0}. PS0 is expanded after a command is
     * read and before it runs; the command substitution appends that command (the newest history
     * entry, leading whitespace stripped) to the history file. It runs in a subshell, which is why
     * plain {@code history -a} can't be used here: the parent's "already appended" marker never
     * advances there, re-appending the whole session on every command.
     */
    private static final String PS0_HOOK =
            "${HISTFILE:+$(fc -ln -1 2>/dev/null | sed \"s/^[[:space:]]*//\" >> \"$HISTFILE\")}";

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
            Path file = dir.resolve(tab + ".bash_history");
            compact(file);
            // Forward slashes so MSYS/Cygwin bash accepts the Windows path as-is.
            env.put("HISTFILE", file.toString().replace('\\', '/'));
        } catch (IOException e) {
            System.err.println("Could not create history dir, bash keeps its default history: " + e);
        }

        // Generous defaults; values the user set globally win.
        env.putIfAbsent("HISTSIZE", "50000");
        env.putIfAbsent("HISTFILESIZE", "50000");
        env.putIfAbsent("PS0", PS0_HOOK);

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

    /**
     * Rewrite {@code file} without duplicate lines (keeping the last occurrence) and capped at
     * {@link #MAX_LINES}. Called before the shell starts, so it also cleans up the duplicates a
     * clean bash exit leaves behind (see class doc). Bytes are round-tripped via ISO-8859-1 so
     * non-UTF-8 content survives unchanged.
     */
    private static void compact(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                unique.remove(line); // re-adding moves it to the end, keeping the newest position
                unique.add(line);
            }
            List<String> out = new ArrayList<>(unique);
            if (out.size() > MAX_LINES) {
                out = out.subList(out.size() - MAX_LINES, out.size());
            }
            if (out.size() == lines.size()) {
                return; // already clean, don't churn the file
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, out, StandardCharsets.ISO_8859_1);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not compact history file " + file + ": " + e);
        }
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
