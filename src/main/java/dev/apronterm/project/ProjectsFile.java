package dev.apronterm.project;

import java.util.ArrayList;
import java.util.List;

/** Root document persisted to {@code projects.json}. */
public class ProjectsFile {

    public List<Project> projects = new ArrayList<>();
    /** Name of the project last opened, for convenience. */
    public String activeProject;

    public Project find(String name) {
        if (name == null) {
            return null;
        }
        for (Project p : projects) {
            if (name.equals(p.name)) {
                return p;
            }
        }
        return null;
    }
}
