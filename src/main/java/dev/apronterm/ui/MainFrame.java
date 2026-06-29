package dev.apronterm.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.apronterm.app.AppInfo;
import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.app.I18n;
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
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
    /** True while we mutate {@link #projectCombo} programmatically, to skip the switch listener. */
    private boolean suppressProjectComboEvents = false;
    /** Last non-maximized window bounds, persisted so un-maximizing restores the real size. (#11) */
    private Rectangle normalBounds;

    private final JComboBox<String> projectCombo = new JComboBox<>();
    private final JMenu newTabMenu = new JMenu(I18n.t("menu.newTab"));
    private final JMenu switchProjectMenu = new JMenu(I18n.t("menu.switchTo"));
    private final JMenu addProjectMenu = new JMenu(I18n.t("menu.add"));

    public MainFrame(WtSettingsService wtService, ProjectStore store, ApronTermConfig config) {
        super(AppInfo.nameAndVersion());
        this.wtService = wtService;
        this.store = store;
        this.config = config;
        this.theme = new TerminalTheme(config.isDark());
        this.factory = new TerminalFactory(theme, config);
        this.projects = store.loadProjects();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        // Focus the terminal when the tab selection changes (switching or after a close). (#16)
        // Also re-measure it: a tab in a JTabbedPane only gets a real size once it's the selected,
        // laid-out one, so its pty must be told the true size when it first becomes visible.
        tabbedPane.addChangeListener(e -> {
            focusSelectedTab();
            resizeSelectedTerminal();
            clearSelectedTabNotification(); // the user is now looking at this tab (#12)
        });

        wtService.addListener(s -> rebuildDynamicMenus());
        rebuildDynamicMenus();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // Terminals are created during restoreSession() while the window is still invisible
                // (0x0 panels), so JediTerm caches a bogus grid size; the window then shows already
                // maximized, firing no size *change* to correct it. Push the true size now.
                resizeSelectedTerminal();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                // Returning to apronTERM counts as seeing the current tab's bell notification. (#12)
                clearSelectedTabNotification();
            }
        });

        // A focused JediTerm terminal swallows key events (e.g. Ctrl+P -> shell), so a plain
        // menu accelerator won't fire over it. Catch the quick-switch chords app-wide before the
        // terminal sees them, and consume them so they never leak to the shell. (Issue #9)
        // Triggers: Ctrl+Shift+P and Ctrl+Y (the latter also shadows readline's "yank").
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !isActive() || e.isAltDown()) {
                return false;
            }
            boolean ctrlShiftP = e.getKeyCode() == KeyEvent.VK_P && e.isControlDown() && e.isShiftDown();
            boolean ctrlY = e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown() && !e.isShiftDown();
            if (ctrlShiftP || ctrlY) {
                openQuickSwitch();
                return true; // consumed
            }
            // Ctrl+Shift+D: duplicate the current tab (Ctrl+D alone is the shell's EOF). (#15)
            if (e.getKeyCode() == KeyEvent.VK_D && e.isControlDown() && e.isShiftDown()) {
                duplicateCurrentTab();
                return true; // consumed
            }
            return false;
        });

        // Remember the last non-maximized bounds so we can restore the real size next start. (#11)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rememberNormalBounds();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                rememberNormalBounds();
            }
        });

        setSize(1000, 680);
        setLocationRelativeTo(null);
        normalBounds = getBounds();
        restoreSession();
    }

    /** Record the window bounds whenever it is in its normal (non-maximized) state. (#11) */
    private void rememberNormalBounds() {
        if ((getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
            normalBounds = getBounds();
        }
    }

    /** Give keyboard focus to the currently selected terminal. (#16) */
    private void focusSelectedTab() {
        int i = tabbedPane.getSelectedIndex();
        List<TerminalTab> tabs = activeTabs();
        if (i >= 0 && i < tabs.size()) {
            tabs.get(i).focus();
        }
    }

    /**
     * Make the selected terminal re-measure itself and push the true size to its pty. JediTerm only
     * tells the pty about a size *change* (from its component-resize listener), so a terminal first
     * shown at a stale/zero size never gets corrected on its own. Re-dispatching a resize once the
     * panel is laid out fixes wrong line wrapping and full-screen TUI rendering (e.g. Claude Code).
     */
    private void resizeSelectedTerminal() {
        SwingUtilities.invokeLater(() -> {
            int i = tabbedPane.getSelectedIndex();
            List<TerminalTab> tabs = activeTabs();
            if (i >= 0 && i < tabs.size()) {
                Component panel = tabs.get(i).widget().getTerminalPanel();
                if (panel.getWidth() > 0 && panel.getHeight() > 0) {
                    panel.dispatchEvent(new ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED));
                }
            }
        });
    }

    /** Badge the tab that rang the bell, unless the user is already looking at it. (#12) */
    private void onBell(TerminalTab tab) {
        int idx = activeTabs().indexOf(tab);
        boolean alreadyWatching = idx >= 0 && idx == tabbedPane.getSelectedIndex() && isActive();
        if (alreadyWatching) {
            return;
        }
        tab.setNotified(true);
        // If the tab belongs to a parked project it has no header right now; the flag persists and
        // attachTab() restores the badge when that project is reactivated.
        if (idx >= 0 && tabbedPane.getTabComponentAt(idx) instanceof ButtonTabComponent header) {
            header.setNotified(true);
        }
        // Surface the badge in the project dropdown too, so a bell in a parked project is visible. (#12)
        projectCombo.repaint();
    }

    /** Clear the bell badge on the currently selected tab (the user is now looking at it). (#12) */
    private void clearSelectedTabNotification() {
        int i = tabbedPane.getSelectedIndex();
        List<TerminalTab> tabs = activeTabs();
        if (i >= 0 && i < tabs.size()) {
            TerminalTab tab = tabs.get(i);
            if (!tab.isNotified()) {
                return;
            }
            tab.setNotified(false);
            if (tabbedPane.getTabComponentAt(i) instanceof ButtonTabComponent header) {
                header.setNotified(false);
            }
            projectCombo.repaint(); // the dropdown's badge for this project may now be stale (#12)
        }
    }

    /** Whether any of {@code name}'s live tabs has an unseen bell notification. (#12) */
    private boolean projectHasNotification(String name) {
        List<TerminalTab> tabs = liveTabs.get(name);
        if (tabs == null) {
            return false;
        }
        for (TerminalTab t : tabs) {
            if (t.isNotified()) {
                return true;
            }
        }
        return false;
    }

    /** Open a new tab with the selected tab's profile, starting directory and startup command. (#15) */
    private void duplicateCurrentTab() {
        int i = tabbedPane.getSelectedIndex();
        List<TerminalTab> tabs = activeTabs();
        if (i >= 0 && i < tabs.size()) {
            openTab(tabs.get(i).spec().copy());
        }
    }

    // ---- UI construction ---------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu(I18n.t("menu.file"));
        file.add(newTabMenu);
        JMenuItem duplicateTab = new JMenuItem(I18n.t("menu.file.duplicateTab"));
        duplicateTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        duplicateTab.addActionListener(e -> duplicateCurrentTab());
        file.add(duplicateTab);
        JMenuItem closeTab = new JMenuItem(I18n.t("menu.file.closeTab"));
        closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        closeTab.addActionListener(e -> closeTabAt(tabbedPane.getSelectedIndex()));
        file.add(closeTab);
        file.addSeparator();
        JMenuItem settings = new JMenuItem(I18n.t("menu.file.settings"));
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK));
        settings.addActionListener(e -> openSettings());
        file.add(settings);
        file.addSeparator();
        JMenuItem exit = new JMenuItem(I18n.t("menu.file.exit"));
        exit.addActionListener(e -> onExit());
        file.add(exit);
        bar.add(file);

        JMenu projectsMenu = new JMenu(I18n.t("menu.projects"));
        JMenuItem quickSwitch = new JMenuItem(I18n.t("menu.projects.quickSwitch"));
        // Ctrl+Shift+P (not Ctrl+P): Ctrl+P is readline's "previous command" in bash/mingw.
        quickSwitch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        quickSwitch.setToolTipText(I18n.t("menu.projects.quickSwitch.tooltip"));
        quickSwitch.addActionListener(e -> openQuickSwitch());
        projectsMenu.add(quickSwitch);
        projectsMenu.addSeparator();
        projectsMenu.add(switchProjectMenu);
        projectsMenu.add(addProjectMenu);
        projectsMenu.addSeparator();
        JMenuItem saveAs = new JMenuItem(I18n.t("menu.projects.saveAs"));
        saveAs.addActionListener(e -> saveCurrentAsProject());
        projectsMenu.add(saveAs);
        JMenuItem manage = new JMenuItem(I18n.t("menu.projects.manage"));
        manage.addActionListener(e -> manageProjects());
        projectsMenu.add(manage);
        bar.add(projectsMenu);

        JMenu profiles = new JMenu(I18n.t("menu.profiles"));
        JMenuItem editProfiles = new JMenuItem(I18n.t("menu.profiles.edit"));
        editProfiles.addActionListener(e -> openProfileEditor());
        profiles.add(editProfiles);
        JMenuItem editSettings = new JMenuItem(I18n.t("menu.profiles.editRaw"));
        editSettings.addActionListener(e -> new SettingsEditorDialog(this, wtService).setVisible(true));
        profiles.add(editSettings);
        JMenuItem reload = new JMenuItem(I18n.t("menu.profiles.reload"));
        reload.addActionListener(e -> wtService.reloadNow());
        profiles.add(reload);
        bar.add(profiles);

        JMenu help = new JMenu(I18n.t("menu.help"));
        JMenuItem about = new JMenuItem(I18n.t("menu.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                AppInfo.nameAndVersion() + "\n\n" + I18n.t("about.message"),
                I18n.t("about.title"), JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);

        return bar;
    }

    private JToolBar buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        tb.add(new JLabel(I18n.t("toolbar.project")));
        projectCombo.setRenderer(new ProjectComboRenderer());
        projectCombo.setToolTipText(I18n.t("toolbar.project.tooltip"));
        projectCombo.addActionListener(e -> onProjectComboChanged());
        tb.add(projectCombo);
        JButton addBtn = new JButton(I18n.t("toolbar.add"));
        addBtn.setToolTipText(I18n.t("toolbar.add.tooltip"));
        addBtn.addActionListener(e -> showAddProjectPopup(addBtn));
        tb.add(addBtn);

        tb.addSeparator();

        JButton newTab = new JButton(I18n.t("toolbar.newTab"));
        newTab.addActionListener(e -> showNewTabPopup(newTab));
        tb.add(newTab);

        return tb;
    }

    /** Rebuild everything that depends on the (mutable) profile or project lists. */
    private void rebuildDynamicMenus() {
        newTabMenu.removeAll();
        List<WtProfile> visible = sortedProfiles();
        for (WtProfile p : visible) {
            JMenuItem item = new JMenuItem(p.name);
            item.addActionListener(e -> openTab(new TabSpec(p.name, null, null)));
            newTabMenu.add(item);
        }
        if (visible.isEmpty()) {
            JMenuItem none = new JMenuItem(I18n.t("menu.noProfiles"));
            none.setEnabled(false);
            newTabMenu.add(none);
        }

        switchProjectMenu.removeAll();
        addProjectMenu.removeAll();
        for (Project pr : sortedProjects()) {
            JMenuItem s = new JMenuItem(pr.name);
            s.addActionListener(e -> openProject(pr, true));
            switchProjectMenu.add(s);
            JMenuItem a = new JMenuItem(pr.name);
            a.addActionListener(e -> openProject(pr, false));
            addProjectMenu.add(a);
        }

        rebuildProjectCombo();
    }

    /** Rebuild the project dropdown in display order, preserving the selection. */
    private void rebuildProjectCombo() {
        String selected = (String) projectCombo.getSelectedItem();
        suppressProjectComboEvents = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (Project pr : sortedProjects()) {
                model.addElement(pr.name);
            }
            projectCombo.setModel(model);
            String want = (selected != null) ? selected : projects.activeProject;
            if (want != null) {
                projectCombo.setSelectedItem(want);
            }
        } finally {
            suppressProjectComboEvents = false;
        }
    }

    /** Visible profiles in alphabetical order. (#14) */
    private List<WtProfile> sortedProfiles() {
        List<WtProfile> list = new ArrayList<>(wtService.current().visible());
        list.sort(Comparator.comparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Projects ordered for display: those with running tabs first, each group alphabetical. (#14) */
    private List<Project> sortedProjects() {
        List<Project> list = new ArrayList<>(projects.projects);
        list.sort(Comparator
                .comparingInt((Project p) -> liveTabCount(p.name) > 0 ? 0 : 1)
                .thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The profile used for auto-created tabs: the configured one if valid, else the WT default. (#14) */
    private WtProfile effectiveDefaultProfile() {
        WtSettings s = wtService.current();
        if (config.defaultProfile != null && !config.defaultProfile.isBlank()) {
            WtProfile p = s.byName(config.defaultProfile);
            if (p != null) {
                return p;
            }
        }
        return s.defaultProfile();
    }

    /** Switch projects when the user picks a different one in the dropdown (replaces "Wechseln"). */
    private void onProjectComboChanged() {
        if (suppressProjectComboEvents) {
            return;
        }
        Project pr = projects.find((String) projectCombo.getSelectedItem());
        if (pr != null) {
            switchToProject(pr);
        }
    }

    /** Set the dropdown selection without triggering {@link #onProjectComboChanged()}. */
    private void setProjectComboSelection(String name) {
        suppressProjectComboEvents = true;
        try {
            projectCombo.setSelectedItem(name);
        } finally {
            suppressProjectComboEvents = false;
        }
    }

    private void showNewTabPopup(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        for (WtProfile p : sortedProfiles()) {
            JMenuItem item = new JMenuItem(p.name);
            item.addActionListener(e -> openTab(new TabSpec(p.name, null, null)));
            popup.add(item);
        }
        popup.show(anchor, 0, anchor.getHeight());
    }

    /** Popup listing every project; choosing one adds its tabs to the current workspace. */
    private void showAddProjectPopup(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        if (projects.projects.isEmpty()) {
            JMenuItem none = new JMenuItem(I18n.t("popup.noProjects"));
            none.setEnabled(false);
            popup.add(none);
        } else {
            for (Project pr : sortedProjects()) {
                JMenuItem item = new JMenuItem(pr.name);
                item.addActionListener(e -> openProject(pr, false));
                popup.add(item);
            }
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
            tab.onBell(() -> onBell(tab)); // highlight the tab on a bell (#12)
            tab.focus();
            refreshIndicators();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    I18n.t("dialog.openTabFailed.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The live tab list of the project currently shown; mirrors {@link #tabbedPane}. */
    private List<TerminalTab> activeTabs() {
        return liveTabs.computeIfAbsent(activeKey, k -> new ArrayList<>());
    }

    /** Number of running tabs the named project currently has. */
    private int liveTabCount(String name) {
        List<TerminalTab> t = liveTabs.get(name);
        return t == null ? 0 : t.size();
    }

    /** Refresh the dropdown after live-tab counts change: re-sort (active-first) and re-mark. */
    private void refreshIndicators() {
        rebuildProjectCombo();
    }

    /** Add an already-created tab's widget to the visible pane (does not spawn a process). */
    private void attachTab(TerminalTab tab) {
        tabbedPane.addTab(tab.spec().effectiveTitle(), tab.widget());
        int idx = tabbedPane.getTabCount() - 1;
        ButtonTabComponent header = new ButtonTabComponent(tabbedPane, this::closeTabAt);
        header.setNotified(tab.isNotified()); // restore a pending bell badge (e.g. after a project switch) (#12)
        tabbedPane.setTabComponentAt(idx, header);
    }

    private void closeTabAt(int index) {
        List<TerminalTab> tabs = activeTabs();
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        TerminalTab tab = tabs.remove(index);
        tabbedPane.remove(index);
        tab.close();
        refreshIndicators();
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
            refreshIndicators();
            return;
        }
    }

    /** Open the keyboard-driven quick switcher (Ctrl+Shift+P or Ctrl+Y). (Issue #9) */
    private void openQuickSwitch() {
        if (projects.projects.isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("dialog.noProjects.message"));
            return;
        }
        new QuickSwitchDialog(this, sortedProjects(), activeKey,
                pr -> liveTabCount(pr.name), pr -> projectHasNotification(pr.name),
                this::switchToProject).setVisible(true);
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
        setProjectComboSelection(pr.name);

        List<TerminalTab> tabs = liveTabs.get(pr.name);
        if (tabs == null || tabs.isEmpty()) {
            for (TabSpec t : pr.tabs) {
                openTab(t.copy());
            }
        } else {
            reattachActive();
        }
        refreshIndicators();
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
            JOptionPane.showMessageDialog(this, I18n.t("dialog.noTabs.message"));
            return;
        }
        String name = JOptionPane.showInputDialog(this, I18n.t("dialog.saveProject.prompt"));
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        Project existing = projects.find(name);
        if (existing != null) {
            int r = JOptionPane.showConfirmDialog(this,
                    I18n.t("dialog.overwriteProject.message", name), I18n.t("dialog.overwriteProject.title"),
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
        setProjectComboSelection(name);
    }

    private void manageProjects() {
        WtProfile def = effectiveDefaultProfile();
        String defName = (def != null) ? def.name : null;
        new ProjectDialog(this, projects, wtService.current(), defName, saved -> {
            this.projects = saved;
            store.saveProjects(saved);
            rebuildDynamicMenus();
        }).setVisible(true);
    }

    private void openProfileEditor() {
        try {
            new ProfileEditorDialog(this, wtService).setVisible(true);
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, I18n.t("dialog.settingsReadFailed.message", e.getMessage()),
                    I18n.t("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Settings ----------------------------------------------------------

    private void openSettings() {
        List<String> profileNames = new ArrayList<>();
        for (WtProfile p : sortedProfiles()) {
            profileNames.add(p.name);
        }
        new SettingsDialog(this, config, profileNames, this::applyConfig).setVisible(true);
    }

    /** Re-apply everything derived from the (already saved) config to the running app. */
    private void applyConfig(ApronTermConfig cfg) {
        applyTheme(cfg.isDark());
        applyFont(); // re-read terminal font for all open terminals (#13)
    }

    /** Push the (changed) terminal font to every live terminal. (#13) */
    private void applyFont() {
        for (List<TerminalTab> tabs : liveTabs.values()) {
            for (TerminalTab t : tabs) {
                t.applyFont();
            }
        }
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
        placeWindow(s);
        // Restore the last session's tabs into the project they belonged to, so switching
        // away and back keeps them running.
        if (s.activeProject != null && projects.find(s.activeProject) != null) {
            activeKey = s.activeProject;
        }
        for (TabSpec t : s.openTabs) {
            openTab(t);
        }
        if (s.activeProject != null) {
            setProjectComboSelection(s.activeProject);
        }
        if (s.selectedTab >= 0 && s.selectedTab < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(s.selectedTab);
        }
        // First run / empty session: open one default-profile tab so the window isn't empty.
        if (activeTabs().isEmpty()) {
            WtProfile def = effectiveDefaultProfile();
            if (def != null) {
                openTab(new TabSpec(def.name, null, null));
            }
        }
    }

    /**
     * Restore the window's position/size, choosing the correct monitor. We can't rely on
     * {@code windowBounds} alone: it only ever holds the <em>non-maximized</em> bounds, which for a
     * user who keeps the window maximized are stale primary-monitor defaults. So when we were
     * maximized we use the saved maximized bounds to find which monitor that was and re-home the
     * normal-sized window there before maximizing. Bounds left on a now-disconnected display are
     * discarded in favour of the constructor's centered default. (#11)
     */
    private void placeWindow(SessionState s) {
        Rectangle target = rect(s.windowBounds);
        if (s.maximized) {
            Rectangle max = rect(s.maximizedBounds);
            Rectangle screen = (max != null) ? screenBoundsContaining(center(max)) : null;
            if (screen != null) {
                Rectangle size = (target != null) ? target : new Rectangle(0, 0, 1000, 680);
                target = centerWithin(size, screen);
            }
        }
        if (target != null && isOnAScreen(target)) {
            setBounds(target);
            normalBounds = target;
        }
        // else: keep the centered default the constructor set (monitor gone / off-screen).
        if (s.maximized) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
    }

    private static Rectangle rect(int[] b) {
        return (b != null && b.length == 4) ? new Rectangle(b[0], b[1], b[2], b[3]) : null;
    }

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** Bounds of the screen whose area contains {@code p}, or {@code null} if none does. */
    private static Rectangle screenBoundsContaining(Point p) {
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = gd.getDefaultConfiguration().getBounds();
            if (b.contains(p)) {
                return b;
            }
        }
        return null;
    }

    /** A rectangle with {@code r}'s size centered inside {@code screen}. */
    private static Rectangle centerWithin(Rectangle r, Rectangle screen) {
        int w = Math.min(r.width, screen.width);
        int h = Math.min(r.height, screen.height);
        return new Rectangle(screen.x + (screen.width - w) / 2, screen.y + (screen.height - h) / 2, w, h);
    }

    /** True if a meaningful part of {@code r} lands on some currently-connected screen. */
    private static boolean isOnAScreen(Rectangle r) {
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle i = gd.getDefaultConfiguration().getBounds().intersection(r);
            if (i.width > 50 && i.height > 50) {
                return true;
            }
        }
        return false;
    }

    private void onExit() {
        SessionState s = new SessionState();
        for (TerminalTab t : activeTabs()) {
            s.openTabs.add(t.spec().copy());
        }
        s.activeProject = (String) projectCombo.getSelectedItem();
        s.selectedTab = tabbedPane.getSelectedIndex();
        s.maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        // Always persist the normal bounds (not the maximized ones) so un-maximizing on the next
        // start restores the real size/position alongside the maximized flag. (#11)
        Rectangle b = (normalBounds != null) ? normalBounds : getBounds();
        s.windowBounds = new int[]{b.x, b.y, b.width, b.height};
        // When maximized, also record where the window currently sits: its monitor is the only
        // clue to which display to re-home on next start, since windowBounds is non-maximized. (#11)
        if (s.maximized) {
            Rectangle m = getBounds();
            s.maximizedBounds = new int[]{m.x, m.y, m.width, m.height};
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

    /** Accent for an unseen bell notification; readable on both dark and light chrome. (#12) */
    private static final Color NOTIFY_COLOR = new Color(0xFF, 0x98, 0x00);

    /** Marks projects with running tabs in the dropdown: bold name + {@code ● N}; amber + a leading
     * {@code ●} when one of those tabs has an unseen bell notification. (#12) */
    private final class ProjectComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                      boolean selected, boolean focus) {
            super.getListCellRendererComponent(l, value, index, selected, focus);
            String name = (String) value;
            if (name != null) {
                int count = liveTabCount(name);
                boolean notified = projectHasNotification(name);
                String text = count > 0 ? name + "   ● " + count : name;
                if (notified) {
                    setText("● " + text);
                    setForeground(NOTIFY_COLOR);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (count > 0) {
                    setText(text);
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
            return this;
        }
    }
}
