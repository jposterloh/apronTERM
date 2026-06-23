package dev.apronterm.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Resolves the program's own folders/files (under {@code %LOCALAPPDATA%\apronTERM}) and locates the
 * Windows Terminal {@code settings.json}.
 */
public final class AppPaths {

    private static final String APP_FOLDER = "apronTERM";

    private AppPaths() {
    }

    /** {@code %LOCALAPPDATA%\apronTERM}, created on demand. */
    public static Path configDir() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = (local != null && !local.isBlank())
                ? Paths.get(local)
                : Paths.get(System.getProperty("user.home"), "AppData", "Local");
        Path dir = base.resolve(APP_FOLDER);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create config dir: " + dir, e);
        }
        return dir;
    }

    public static Path projectsFile() {
        return configDir().resolve("projects.json");
    }

    public static Path sessionFile() {
        return configDir().resolve("session.json");
    }

    public static Path configFile() {
        return configDir().resolve("config.json");
    }

    /**
     * Best-effort discovery of the Windows Terminal settings.json. Returns the first existing
     * candidate, or the Store-install path as a fallback.
     */
    public static Path findWindowsTerminalSettings() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = (local != null && !local.isBlank())
                ? Paths.get(local)
                : Paths.get(System.getProperty("user.home"), "AppData", "Local");

        List<Path> candidates = List.of(
                base.resolve("Packages\\Microsoft.WindowsTerminal_8wekyb3d8bbwe\\LocalState\\settings.json"),
                base.resolve("Packages\\Microsoft.WindowsTerminalPreview_8wekyb3d8bbwe\\LocalState\\settings.json"),
                base.resolve("Microsoft\\Windows Terminal\\settings.json"));

        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                return c;
            }
        }
        return candidates.get(0);
    }
}
