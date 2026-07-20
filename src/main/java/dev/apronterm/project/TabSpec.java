package dev.apronterm.project;

/**
 * A single tab definition: which Windows Terminal profile to use, where to start, an optional tab
 * title, and an optional command to run once the shell has started. {@code startingDirectory}/
 * {@code title}/{@code command} may be {@code null}.
 */
public class TabSpec {

    public String profileName;
    public String startingDirectory;
    public String title;
    /** Command sent to the shell right after it starts (e.g. launch a script/tool); may be null. */
    public String command;

    public TabSpec() {
    }

    public TabSpec(String profileName, String startingDirectory, String title) {
        this(profileName, startingDirectory, title, null);
    }

    public TabSpec(String profileName, String startingDirectory, String title, String command) {
        this.profileName = profileName;
        this.startingDirectory = startingDirectory;
        this.title = title;
        this.command = command;
    }

    public String effectiveTitle() {
        return (title != null && !title.isBlank()) ? title : profileName;
    }

    public TabSpec copy() {
        return new TabSpec(profileName, startingDirectory, title, command);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TabSpec t
                && java.util.Objects.equals(profileName, t.profileName)
                && java.util.Objects.equals(startingDirectory, t.startingDirectory)
                && java.util.Objects.equals(title, t.title)
                && java.util.Objects.equals(command, t.command);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(profileName, startingDirectory, title, command);
    }
}
