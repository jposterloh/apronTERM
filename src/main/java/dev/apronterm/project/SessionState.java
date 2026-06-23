package dev.apronterm.project;

import java.util.ArrayList;
import java.util.List;

/** What was open when ApronTerm last closed, restored on next start. */
public class SessionState {

    public List<TabSpec> openTabs = new ArrayList<>();
    public String activeProject;
    public int selectedTab = -1;
    /** {x, y, width, height} of the main window; {@code null} if unknown. */
    public int[] windowBounds;
    public boolean maximized;
}
