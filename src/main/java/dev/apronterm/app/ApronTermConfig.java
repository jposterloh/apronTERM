package dev.apronterm.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Program-level configuration stored in {@code %LOCALAPPDATA%\ApronTerm\config.json}.
 * Currently just an optional override for the Windows Terminal settings.json location.
 */
public class ApronTermConfig {

    /** Explicit path to the Windows Terminal settings.json; {@code null} = auto-detect. */
    public String wtSettingsPath;

    /** UI theme: "dark" (default) or "light". */
    public String theme = "dark";

    public boolean isDark() {
        return !"light".equalsIgnoreCase(theme);
    }

    public static ApronTermConfig load() {
        Path f = AppPaths.configFile();
        if (Files.isRegularFile(f)) {
            try {
                return Json.APP.readValue(Files.readString(f), ApronTermConfig.class);
            } catch (IOException e) {
                System.err.println("Could not read config.json, using defaults: " + e.getMessage());
            }
        }
        return new ApronTermConfig();
    }

    public void save() {
        try {
            Files.writeString(AppPaths.configFile(), Json.APP.writeValueAsString(this));
        } catch (IOException e) {
            System.err.println("Could not write config.json: " + e.getMessage());
        }
    }

    /** The settings.json path to use: the explicit override if set, otherwise auto-detected. */
    public Path effectiveWtSettingsPath() {
        if (wtSettingsPath != null && !wtSettingsPath.isBlank()) {
            return Path.of(wtSettingsPath);
        }
        return AppPaths.findWindowsTerminalSettings();
    }
}
