package dev.apronterm.wt;

/**
 * One Windows Terminal profile, flattened from settings.json (with profile {@code defaults}
 * already merged in). Only the fields ApronTerm needs to launch and label a tab are kept.
 */
public final class WtProfile {

    public final String guid;
    public final String name;
    /** May be {@code null} for source-generated profiles (WSL, Azure, VS dev prompts). */
    public final String commandline;
    public final String startingDirectory; // may be null
    public final String icon;              // may be null
    public final String source;            // may be null (set for generated profiles)
    public final boolean hidden;

    public WtProfile(String guid, String name, String commandline, String startingDirectory,
                     String icon, String source, boolean hidden) {
        this.guid = guid;
        this.name = name;
        this.commandline = commandline;
        this.startingDirectory = startingDirectory;
        this.icon = icon;
        this.source = source;
        this.hidden = hidden;
    }

    @Override
    public String toString() {
        return name;
    }
}
