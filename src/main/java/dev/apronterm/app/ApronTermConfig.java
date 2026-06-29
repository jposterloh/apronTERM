package dev.apronterm.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Program-level configuration stored in {@code %LOCALAPPDATA%\apronTERM\config.json}.
 */
public class ApronTermConfig {

    /** Explicit path to the Windows Terminal settings.json; {@code null} = auto-detect. */
    public String wtSettingsPath;

    /** UI theme: "dark" (default) or "light". */
    public String theme = "dark";

    /** UI language: "de", "en", or {@code null}/blank = follow the OS locale. (#i18n) */
    public String language;

    /** Close a tab automatically when its shell process exits. */
    public boolean autoCloseExitedTabs = true;

    /**
     * Name of the profile used for auto-created tabs (first run / empty session).
     * {@code null} or blank = use Windows Terminal's default profile.
     */
    public String defaultProfile;

    /** Terminal font family; {@code null}/blank = auto (Cascadia Mono, like Windows Terminal). (#13) */
    public String terminalFont;

    /** Terminal font size in points; {@code <= 0} = default (12). (#13) */
    public int terminalFontSize;

    public boolean isDark() {
        return !"light".equalsIgnoreCase(theme);
    }

    public void setDark(boolean dark) {
        theme = dark ? "dark" : "light";
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
