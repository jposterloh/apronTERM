package dev.apronterm.app;

/** Static facts about this build of apronTERM (name, version). */
public final class AppInfo {

    public static final String NAME = "apronTERM";

    private AppInfo() {
    }

    /**
     * The release version, e.g. {@code "0.1.7"}. Read from the JAR manifest's
     * {@code Implementation-Version} (written by maven-jar-plugin); falls back to {@code "dev"} when
     * running from sources/IDE where there is no manifest.
     */
    public static String version() {
        String v = AppInfo.class.getPackage().getImplementationVersion();
        return (v != null && !v.isBlank()) ? v : "dev";
    }

    /** e.g. {@code "apronTERM 0.1.7"}. */
    public static String nameAndVersion() {
        return NAME + " " + version();
    }
}
