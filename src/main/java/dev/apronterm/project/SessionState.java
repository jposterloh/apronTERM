package dev.apronterm.project;

import java.util.ArrayList;
import java.util.List;

/** What was open when ApronTerm last closed, restored on next start. */
public class SessionState {

    public List<TabSpec> openTabs = new ArrayList<>();
    public String activeProject;
    public int selectedTab = -1;
    /** {x, y, width, height} of the main window in its normal (non-maximized) state; {@code null} if unknown. */
    public int[] windowBounds;
    public boolean maximized;
    /**
     * {x, y, width, height} of the window while it was maximized at exit; {@code null} if it
     * wasn't maximized. Lets restore re-home the window on the right monitor before maximizing,
     * since {@link #windowBounds} only ever holds non-maximized (often primary-monitor) bounds.
     */
    public int[] maximizedBounds;
}
