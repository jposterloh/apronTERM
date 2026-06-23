package dev.apronterm.tools;

import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.terminal.CommandLine;
import dev.apronterm.wt.WtProfile;
import dev.apronterm.wt.WtSettings;
import dev.apronterm.wt.WtSettingsService;

/**
 * Headless diagnostic: prints the parsed Windows Terminal profiles and the argv ApronTerm would launch.
 * Run with: {@code mvn exec:java -Dexec.mainClass=dev.apronterm.tools.ProfileDump}
 */
public final class ProfileDump {

    private ProfileDump() {
    }

    public static void main(String[] args) {
        ApronTermConfig config = ApronTermConfig.load();
        WtSettingsService service = new WtSettingsService(config.effectiveWtSettingsPath());
        WtSettings settings = service.reloadNow();

        System.out.println("settings.json: " + service.getSettingsPath());
        WtProfile def = settings.defaultProfile();
        System.out.println("default profile: " + (def != null ? def.name : "(none)"));
        System.out.println();

        for (WtProfile p : settings.all()) {
            System.out.println((p.hidden ? "[hidden] " : "          ") + p.name);
            if (p.commandline != null) {
                System.out.println("            commandline: " + p.commandline);
                System.out.println("            -> argv: " + CommandLine.resolve(p.commandline));
            } else {
                System.out.println("            (no commandline; source=" + p.source + ")");
            }
            if (p.startingDirectory != null) {
                System.out.println("            startingDirectory: " + p.startingDirectory);
            }
        }
    }
}
