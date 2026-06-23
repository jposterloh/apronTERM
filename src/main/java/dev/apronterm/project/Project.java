package dev.apronterm.project;

import java.util.ArrayList;
import java.util.List;

/** A named set of tabs you switch between. */
public class Project {

    public String name;
    public List<TabSpec> tabs = new ArrayList<>();

    public Project() {
    }

    public Project(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
