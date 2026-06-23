package dev.apronterm.ui;

import dev.apronterm.project.Project;
import dev.apronterm.project.ProjectStore;
import dev.apronterm.project.ProjectsFile;
import dev.apronterm.project.SessionState;
import dev.apronterm.project.TabSpec;
import dev.apronterm.terminal.TerminalFactory;
import dev.apronterm.terminal.TerminalTab;
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
import java.util.List;

/** The apronTERM main window: a tabbed set of terminals with project switching and a profile editor. */
public final class MainFrame extends JFrame {

    private final WtSettingsService wtService;
    private final ProjectStore store;
    private final TerminalFactory factory;

    private ProjectsFile projects;

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<TerminalTab> openTabs = new ArrayList<>();

    private final JComboBox<String> projectCombo = new JComboBox<>();
    private final JMenu newTabMenu = new JMenu("Neuer Tab");
    private final JMenu switchProjectMenu = new JMenu("Wechseln zu");
    private final JMenu addProjectMenu = new JMenu("Hinzufügen");

    public MainFrame(WtSettingsService wtService, ProjectStore store, boolean dark) {
        super("apronTERM");
        this.wtService = wtService;
        this.store = store;
        this.factory = new TerminalFactory(dark);
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
        JMenuItem editSettings = new JMenuItem("settings.json bearbeiten…");
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
            tabbedPane.addTab(spec.effectiveTitle(), tab.widget());
            int idx = tabbedPane.getTabCount() - 1;
            openTabs.add(tab);
            tabbedPane.setTabComponentAt(idx, new ButtonTabComponent(tabbedPane, this::closeTabAt));
            tabbedPane.setSelectedIndex(idx);
            SwingUtilities.invokeLater(() -> tab.widget().requestFocusInWindow());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Tab konnte nicht geöffnet werden", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= openTabs.size()) {
            return;
        }
        TerminalTab tab = openTabs.remove(index);
        tabbedPane.remove(index);
        tab.close();
    }

    private void closeAllTabs() {
        while (!openTabs.isEmpty()) {
            closeTabAt(0);
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
            closeAllTabs();
        }
        for (TabSpec t : pr.tabs) {
            openTab(t.copy());
        }
        projects.activeProject = pr.name;
        projectCombo.setSelectedItem(pr.name);
    }

    private void saveCurrentAsProject() {
        if (openTabs.isEmpty()) {
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
        for (TerminalTab t : openTabs) {
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

    // ---- Session persistence ----------------------------------------------

    private void restoreSession() {
        SessionState s = store.loadSession();
        if (s.windowBounds != null && s.windowBounds.length == 4) {
            setBounds(s.windowBounds[0], s.windowBounds[1], s.windowBounds[2], s.windowBounds[3]);
        }
        if (s.maximized) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
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
        if (openTabs.isEmpty()) {
            WtProfile def = wtService.current().defaultProfile();
            if (def != null) {
                openTab(new TabSpec(def.name, null, null));
            }
        }
    }

    private void onExit() {
        SessionState s = new SessionState();
        for (TerminalTab t : openTabs) {
            s.openTabs.add(t.spec().copy());
        }
        s.activeProject = (String) projectCombo.getSelectedItem();
        s.selectedTab = tabbedPane.getSelectedIndex();
        s.maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        if (!s.maximized) {
            s.windowBounds = new int[]{getX(), getY(), getWidth(), getHeight()};
        }
        store.saveSession(s);

        closeAllTabs();
        wtService.stop();
        dispose();
        System.exit(0);
    }
}
