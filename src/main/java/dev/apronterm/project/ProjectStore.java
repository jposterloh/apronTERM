package dev.apronterm.project;

import dev.apronterm.app.AppPaths;
import dev.apronterm.app.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves {@code projects.json} and {@code session.json}. */
public final class ProjectStore {

    public ProjectsFile loadProjects() {
        Path f = AppPaths.projectsFile();
        if (Files.isRegularFile(f)) {
            try {
                return Json.APP.readValue(Files.readString(f), ProjectsFile.class);
            } catch (IOException e) {
                System.err.println("Could not read projects.json: " + e.getMessage());
            }
        }
        return new ProjectsFile();
    }

    public void saveProjects(ProjectsFile pf) {
        try {
            Files.writeString(AppPaths.projectsFile(), Json.APP.writeValueAsString(pf));
        } catch (IOException e) {
            System.err.println("Could not write projects.json: " + e.getMessage());
        }
    }

    public SessionState loadSession() {
        Path f = AppPaths.sessionFile();
        if (Files.isRegularFile(f)) {
            try {
                return Json.APP.readValue(Files.readString(f), SessionState.class);
            } catch (IOException e) {
                System.err.println("Could not read session.json: " + e.getMessage());
            }
        }
        return new SessionState();
    }

    public void saveSession(SessionState s) {
        try {
            Files.writeString(AppPaths.sessionFile(), Json.APP.writeValueAsString(s));
        } catch (IOException e) {
            System.err.println("Could not write session.json: " + e.getMessage());
        }
    }
}
