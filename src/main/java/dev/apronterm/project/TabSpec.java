package dev.apronterm.project;

/**
 * A single tab definition: which Windows Terminal profile to use, where to start, and an optional
 * tab title. {@code startingDirectory}/{@code title} may be {@code null} to fall back to the
 * profile default / the profile name.
 */
public class TabSpec {

    public String profileName;
    public String startingDirectory;
    public String title;

    public TabSpec() {
    }

    public TabSpec(String profileName, String startingDirectory, String title) {
        this.profileName = profileName;
        this.startingDirectory = startingDirectory;
        this.title = title;
    }

    public String effectiveTitle() {
        return (title != null && !title.isBlank()) ? title : profileName;
    }

    public TabSpec copy() {
        return new TabSpec(profileName, startingDirectory, title);
    }
}
