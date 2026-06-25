package dev.apronterm.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.project.Project;
import dev.apronterm.project.ProjectStore;
import dev.apronterm.project.ProjectsFile;
import dev.apronterm.project.SessionState;
import dev.apronterm.project.TabSpec;
import dev.apronterm.terminal.TerminalFactory;
import dev.apronterm.terminal.TerminalTab;
import dev.apronterm.terminal.TerminalTheme;
import dev.apronterm.wt.WtProfile;
import dev.apronterm.wt.WtSettings;
import dev.apronterm.wt.WtSettingsService;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The apronTERM main window: a tabbed set of terminals with project switching and a profile editor. */
public final class MainFrame extends JFrame {

    private final WtSettingsService wtService;
    private final ProjectStore store;
    private final ApronTermConfig config;
    private final TerminalTheme theme;
    private final TerminalFactory factory;

    private ProjectsFile projects;

    private final JTabbedPane tabbedPane = new JTabbedPane();

    /** Sentinel key for tabs not (yet) tied to a project — e.g. the first-run default tab. */
    private static final String SCRATCH = " scratch";
    /** Live terminal tabs per project name (plus {@link #SCRATCH}); all stay alive until exit. */
    private final Map<String, List<TerminalTab>> liveTabs = new LinkedHashMap<>();
    /** Remembered selected-tab index per project, so switching back restores the view. */
    private final Map<String, Integer> liveSelection = new HashMap<>();
    /** Which project's tabs are currently shown in {@link #tabbedPane}. */
    private String activeKey = SCRATCH;

    private final JComboBox<String> projectCombo = new JComboBox<>();
    private final JMenu newTabMenu = new JMenu("Neuer Tab");
    private final JMenu switchProjectMenu = new JMenu("Wechseln zu");
    private final JMenu addProjectMenu = new JMenu("Hinzufügen");

    public MainFrame(WtSettingsService wtService, ProjectStore store, ApronTermConfig config) {
        super("apronTERM");
        this.wtService = wtService;
        this.store = store;
        this.config = config;
        this.theme = new TerminalTheme(config.isDark());
        this.factory = new TerminalFactory(theme);
        this.projects = store.loadProjects();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        wtService.addListener(s -> rebuildDynamicMenus());
        rebuildDynamicMenus();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });

        setSize(1000, 680);
        setLocationRelativeTo(null);
        restoreSession();
    }

    // ---- UI construction ---------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Datei");
        file.add(newTabMenu);
        JMenuItem closeTab = new JMenuItem("Tab schließen");
        closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        closeTab.addActionListener(e -> closeTabAt(tabbedPane.getSelectedIndex()));
        file.add(closeTab);
        file.addSeparator();
        JMenuItem settings = new JMenuItem("Einstellungen…");
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK));
        settings.addActionListener(e -> openSettings());
        file.add(settings);
        file.addSeparator();
        JMenuItem exit = new JMenuItem("Beenden");
        exit.addActionListener(e -> onExit());
        file.add(exit);
        bar.add(file);

        JMenu projectsMenu = new JMenu("Projekte");
        projectsMenu.add(switchProjectMenu);
        projectsMenu.add(addProjectMenu);
        projectsMenu.addSeparator();
        JMenuItem saveAs = new JMenuItem("Aktuelle Tabs als Projekt speichern…");
        saveAs.addActionListener(e -> saveCurrentAsProject());
        projectsMenu.add(saveAs);
        JMenuItem manage = new JMenuItem("Projekte verwalten…");
        manage.addActionListener(e -> manageProjects());
        projectsMenu.add(manage);
        bar.add(projectsMenu);

        JMenu profiles = new JMenu("Profile");
        JMenuItem editProfiles = new JMenuItem("Profile bearbeiten…");
        editProfiles.addActionListener(e -> openProfileEditor());
        profiles.add(editProfiles);
        JMenuItem editSettings = new JMenuItem("settings.json bearbeiten (Rohformat)…");
        editSettings.addActionListener(e -> new SettingsEditorDialog(this, wtService).setVisible(true));
        profiles.add(editSettings);
        JMenuItem reload = new JMenuItem("Profile neu laden");
        reload.addActionListener(e -> wtService.reloadNow());
        profiles.add(reload);
        bar.add(profiles);

        JMenu help = new JMenu("Hilfe");
        JMenuItem about = new JMenuItem("Über apronTERM");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "apronTERM – ein Swing-Terminal auf Basis von JediTerm + pty4j.\n"
                        + "Profile aus Windows Terminal, organisiert in Projekten.",
                "Über apronTERM", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);

        return bar;
    }

    private JToolBar buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        tb.add(new JLabel(" Projekt: "));
        tb.add(projectCombo);
        JButton switchBtn = new JButton("Wechseln");
        switchBtn.setToolTipText("Aktuelle Tabs schließen und Projekt öffnen");
        switchBtn.addActionListener(e -> openSelectedProject(true));
        tb.add(switchBtn);
        JButton addBtn = new JButton("Hinzufügen");
        addBtn.setToolTipText("Projekt-Tabs zu den aktuellen hinzufügen");
        addBtn.addActionListener(e -> openSelectedProject(false));
        tb.add(addBtn);

        tb.addSeparator();

        JButton newTab = new JButton("Neuer Tab ▾");
        newTab.addActionListener(e -> showNewTabPopup(newTab));
        tb.add(newTab);

        return tb;
    }

    /** Rebuild everything that depends on the (mutable) profile or project lists. */
    private void rebuildDynamicMenus() {
        WtSettings settings = wtService.current();

        newTabMenu.removeAll();
        for (WtProfile p : settings.visible()) {
            JMenuItem item = new JMenuItem(p.name);
            item.addActionListener(e -> openTab(new TabSpec(p.name, null, null)));
            newTabMenu.add(item);
        }
        if (settings.visible().isEmpty()) {
            JMenuItem none = new JMenuItem("(keine Profile gefunden)");
            none.setEnabled(false);
            newTabMenu.add(none);
        }

        switchProjectMenu.removeAll();
        addProjectMenu.removeAll();
        for (Project pr : projects.projects) {
            JMenuItem s = new JMenuItem(pr.name);
            s.addActionListener(e -> openProject(pr, true));
            switchProjectMenu.add(s);
            JMenuItem a = new JMenuItem(pr.name);
            a.addActionListener(e -> openProject(pr, false));
            addProjectMenu.add(a);
        }

        String selected = (String) projectCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (Project pr : projects.projects) {
            model.addElement(pr.name);
        }
        projectCombo.setModel(model);
        if (selected != null) {
            projectCombo.setSelectedItem(selected);
        } else if (projects.activeProject != null) {
            projectCombo.setSelectedItem(projects.activeProject);
        }
    }

    private void showNewTabPopup(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        for (WtProfile p : wtService.current().visible()) {
            JMenuItem item = new JMenuItem(p.name);
            item.addActionListener(e -> openTab(new TabSpec(p.name, null, null)));
            popup.add(item);
        }
        popup.show(anchor, 0, anchor.getHeight());
    }

    // ---- Tab + project actions --------------------------------------------

    private void openTab(TabSpec spec) {
        try {
            TerminalTab tab = factory.create(spec, wtService.current());
            activeTabs().add(tab);
            attachTab(tab);
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
            // Auto-close when the shell exits (e.g. `exit`); checked at exit time so the
            // setting takes effect live. closeTab is identity-based, so it stays idempotent
            // and works even when the tab is parked in an inactive project.
            tab.onExit(() -> SwingUtilities.invokeLater(() -> {
                if (config.autoCloseExitedTabs) {
                    closeTab(tab);
                }
            }));
            SwingUtilities.invokeLater(() -> tab.widget().requestFocusInWindow());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Tab konnte nicht geöffnet werden", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The live tab list of the project currently shown; mirrors {@link #tabbedPane}. */
    private List<TerminalTab> activeTabs() {
        return liveTabs.computeIfAbsent(activeKey, k -> new ArrayList<>());
    }

    /** Add an already-created tab's widget to the visible pane (does not spawn a process). */
    private void attachTab(TerminalTab tab) {
        tabbedPane.addTab(tab.spec().effectiveTitle(), tab.widget());
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, new ButtonTabComponent(tabbedPane, this::closeTabAt));
    }

    private void closeTabAt(int index) {
        List<TerminalTab> tabs = activeTabs();
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        TerminalTab tab = tabs.remove(index);
        tabbedPane.remove(index);
        tab.close();
    }

    /** Close a specific tab by identity, in whichever project holds it; no-op if already gone. */
    private void closeTab(TerminalTab tab) {
        for (Map.Entry<String, List<TerminalTab>> e : liveTabs.entrySet()) {
            int i = e.getValue().indexOf(tab);
            if (i < 0) {
                continue;
            }
            e.getValue().remove(i);
            if (e.getKey().equals(activeKey)) {
                tabbedPane.remove(i); // only the active project's tabs are in the pane
            }
            tab.close();
            return;
        }
    }

    private void openSelectedProject(boolean replace) {
        String name = (String) projectCombo.getSelectedItem();
        Project pr = projects.find(name);
        if (pr == null) {
            JOptionPane.showMessageDialog(this, "Kein Projekt ausgewählt.");
            return;
        }
        openProject(pr, replace);
    }

    private void openProject(Project pr, boolean replace) {
        if (replace) {
            switchToProject(pr);
        } else {
            // "Hinzufügen": launch the project's tabs into the current workspace.
            for (TabSpec t : pr.tabs) {
                openTab(t.copy());
            }
        }
    }

    /**
     * Make {@code pr} the active project. The previously active project's terminals keep running
     * but are detached from the view; switching back re-attaches them. Tabs are only terminated
     * when the whole app exits. On first activation the project's tabs are launched from its
     * saved specs. (Issue #10)
     */
    private void switchToProject(Project pr) {
        if (pr.name.equals(activeKey)) {
            return; // already showing this project
        }
        parkActive();
        activeKey = pr.name;
        projects.activeProject = pr.name;
        projectCombo.setSelectedItem(pr.name);

        List<TerminalTab> tabs = liveTabs.get(pr.name);
        if (tabs == null || tabs.isEmpty()) {
            for (TabSpec t : pr.tabs) {
                openTab(t.copy());
            }
        } else {
            reattachActive();
        }
    }

    /** Detach the active project's tabs from the view, keeping their processes alive. */
    private void parkActive() {
        liveSelection.put(activeKey, tabbedPane.getSelectedIndex());
        tabbedPane.removeAll(); // removes the tab components only; terminals stay alive
    }

    /** Re-attach the active project's still-running tabs and restore its previous selection. */
    private void reattachActive() {
        for (TerminalTab t : activeTabs()) {
            attachTab(t);
        }
        Integer sel = liveSelection.get(activeKey);
        if (sel != null && sel >= 0 && sel < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(sel);
        }
    }

    private void saveCurrentAsProject() {
        if (activeTabs().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Es sind keine Tabs geöffnet.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name des Projekts:");
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        Project existing = projects.find(name);
        if (existing != null) {
            int r = JOptionPane.showConfirmDialog(this,
                    "Projekt '" + name + "' überschreiben?", "Überschreiben",
                    JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
            projects.projects.remove(existing);
        }
        Project pr = new Project(name);
        for (TerminalTab t : activeTabs()) {
            pr.tabs.add(t.spec().copy());
        }
        projects.projects.add(pr);
        projects.activeProject = name;
        store.saveProjects(projects);
        rebuildDynamicMenus();
        projectCombo.setSelectedItem(name);
    }

    private void manageProjects() {
        new ProjectDialog(this, projects, wtService.current(), saved -> {
            this.projects = saved;
            store.saveProjects(saved);
            rebuildDynamicMenus();
        }).setVisible(true);
    }

    private void openProfileEditor() {
        try {
            new ProfileEditorDialog(this, wtService).setVisible(true);
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, "settings.json konnte nicht gelesen werden:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Settings ----------------------------------------------------------

    private void openSettings() {
        new SettingsDialog(this, config, this::applyConfig).setVisible(true);
    }

    /** Re-apply everything derived from the (already saved) config to the running app. */
    private void applyConfig(ApronTermConfig cfg) {
        applyTheme(cfg.isDark());
    }

    private void applyTheme(boolean dark) {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI(); // restyle all open windows (chrome)

        theme.setDark(dark);
        for (List<TerminalTab> tabs : liveTabs.values()) {
            for (TerminalTab t : tabs) {
                t.applyTheme(theme); // re-theme all live terminals, even parked ones
            }
        }
    }

    // ---- Session persistence ----------------------------------------------

    private void restoreSession() {
        SessionState s = store.loadSession();
        if (s.windowBounds != null && s.windowBounds.length == 4) {
            setBounds(s.windowBounds[0], s.windowBounds[1], s.windowBounds[2], s.windowBounds[3]);
        }
        if (s.maximized) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        // Restore the last session's tabs into the project they belonged to, so switching
        // away and back keeps them running.
        if (s.activeProject != null && projects.find(s.activeProject) != null) {
            activeKey = s.activeProject;
        }
        for (TabSpec t : s.openTabs) {
            openTab(t);
        }
        if (s.activeProject != null) {
            projectCombo.setSelectedItem(s.activeProject);
        }
        if (s.selectedTab >= 0 && s.selectedTab < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(s.selectedTab);
        }
        // First run / empty session: open one default-profile tab so the window isn't empty.
        if (activeTabs().isEmpty()) {
            WtProfile def = wtService.current().defaultProfile();
            if (def != null) {
                openTab(new TabSpec(def.name, null, null));
            }
        }
    }

    private void onExit() {
        SessionState s = new SessionState();
        for (TerminalTab t : activeTabs()) {
            s.openTabs.add(t.spec().copy());
        }
        s.activeProject = (String) projectCombo.getSelectedItem();
        s.selectedTab = tabbedPane.getSelectedIndex();
        s.maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        if (!s.maximized) {
            s.windowBounds = new int[]{getX(), getY(), getWidth(), getHeight()};
        }
        store.saveSession(s);

        // Now — and only now — tear down every project's terminals.
        for (List<TerminalTab> tabs : liveTabs.values()) {
            for (TerminalTab t : tabs) {
                t.close();
            }
        }
        liveTabs.clear();
        wtService.stop();
        dispose();
        System.exit(0);
    }
}
