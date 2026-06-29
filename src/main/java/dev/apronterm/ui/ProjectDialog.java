package dev.apronterm.ui;

import dev.apronterm.app.I18n;
import dev.apronterm.project.Project;
import dev.apronterm.project.ProjectsFile;
import dev.apronterm.project.TabSpec;
import dev.apronterm.wt.WtProfile;
import dev.apronterm.wt.WtSettings;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Create / rename / delete projects and edit the tabs (profile + starting directory + title)
 * inside each. On OK the (deep-copied) result is handed back via the {@code onSave} callback.
 */
public final class ProjectDialog extends JDialog {

    private final WtSettings settings;
    /** Configured default profile for new tabs; {@code null} = use the first profile. (#14) */
    private final String defaultProfileName;
    private final ProjectsFile working;
    private final DefaultListModel<Project> listModel = new DefaultListModel<>();
    private final JList<Project> projectList = new JList<>(listModel);
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{I18n.t("project.column.profile"), I18n.t("project.column.dir"),
                    I18n.t("project.column.title"), I18n.t("project.column.command")}, 0);
    private final JTable tabTable = new JTable(tableModel);

    private Project bound; // project currently shown in the table

    public ProjectDialog(Frame owner, ProjectsFile projects, WtSettings settings,
                         String defaultProfileName, Consumer<ProjectsFile> onSave) {
        super(owner, I18n.t("project.dialog.title"), true);
        this.settings = settings;
        this.defaultProfileName = defaultProfileName;
        this.working = deepCopy(projects);

        for (Project p : working.projects) {
            listModel.addElement(p);
        }
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                flushTable();
                bindSelected();
            }
        });

        // Profile combo editor for the first column (alphabetical, like the rest of the UI).
        JComboBox<String> profileCombo = new JComboBox<>();
        for (WtProfile p : sortedVisible()) {
            profileCombo.addItem(p.name);
        }
        tabTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(profileCombo));

        add(buildLeft(), BorderLayout.WEST);
        add(buildRight(), BorderLayout.CENTER);
        add(buildBottom(onSave), BorderLayout.SOUTH);

        if (!listModel.isEmpty()) {
            projectList.setSelectedIndex(0);
        }

        setSize(760, 480);
        setLocationRelativeTo(owner);
    }

    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(javax.swing.BorderFactory.createTitledBorder(I18n.t("project.left.title")));
        projectList.setPreferredSize(new Dimension(200, 0));
        left.add(new JScrollPane(projectList), BorderLayout.CENTER);

        JButton add = new JButton(I18n.t("project.button.new"));
        add.addActionListener(e -> newProject());
        JButton rename = new JButton(I18n.t("project.button.rename"));
        rename.addActionListener(e -> renameProject());
        JButton del = new JButton(I18n.t("project.button.delete"));
        del.addActionListener(e -> deleteProject());

        JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 4));
        buttons.add(add);
        buttons.add(rename);
        buttons.add(del);
        left.add(buttons, BorderLayout.SOUTH);
        return left;
    }

    private JPanel buildRight() {
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(javax.swing.BorderFactory.createTitledBorder(I18n.t("project.right.title")));
        right.add(new JScrollPane(tabTable), BorderLayout.CENTER);

        JButton addRow = new JButton(I18n.t("project.button.addTab"));
        addRow.addActionListener(e -> {
            if (bound == null) {
                return;
            }
            tableModel.addRow(new Object[]{defaultRowProfile(), "", "", ""});
        });
        JButton delRow = new JButton(I18n.t("project.button.removeTab"));
        delRow.addActionListener(e -> {
            int r = tabTable.getSelectedRow();
            if (r >= 0) {
                if (tabTable.isEditing()) {
                    tabTable.getCellEditor().stopCellEditing();
                }
                tableModel.removeRow(r);
            }
        });

        JPanel buttons = new JPanel();
        buttons.add(addRow);
        buttons.add(delRow);
        right.add(buttons, BorderLayout.SOUTH);
        return right;
    }

    private JPanel buildBottom(Consumer<ProjectsFile> onSave) {
        JButton ok = new JButton(I18n.t("project.button.save"));
        ok.addActionListener(e -> {
            flushTable();
            onSave.accept(working);
            dispose();
        });
        JButton cancel = new JButton(I18n.t("button.cancel"));
        cancel.addActionListener(e -> dispose());

        JPanel south = new JPanel();
        south.add(ok);
        south.add(cancel);
        return south;
    }

    private void bindSelected() {
        bound = projectList.getSelectedValue();
        tableModel.setRowCount(0);
        if (bound != null) {
            for (TabSpec t : bound.tabs) {
                tableModel.addRow(new Object[]{
                        t.profileName,
                        t.startingDirectory == null ? "" : t.startingDirectory,
                        t.title == null ? "" : t.title,
                        t.command == null ? "" : t.command});
            }
        }
    }

    /** Write the table's current contents back into the bound project. */
    private void flushTable() {
        if (bound == null) {
            return;
        }
        if (tabTable.isEditing()) {
            tabTable.getCellEditor().stopCellEditing();
        }
        List<TabSpec> tabs = new ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String profile = str(tableModel.getValueAt(r, 0));
            String dir = str(tableModel.getValueAt(r, 1));
            String title = str(tableModel.getValueAt(r, 2));
            String command = str(tableModel.getValueAt(r, 3));
            if (profile.isBlank()) {
                continue;
            }
            tabs.add(new TabSpec(profile,
                    dir.isBlank() ? null : dir,
                    title.isBlank() ? null : title,
                    command.isBlank() ? null : command));
        }
        bound.tabs = tabs;
    }

    private void newProject() {
        String name = JOptionPane.showInputDialog(this, I18n.t("project.prompt.newName"));
        if (name == null || name.isBlank()) {
            return;
        }
        if (working.find(name) != null) {
            JOptionPane.showMessageDialog(this, I18n.t("project.error.exists"));
            return;
        }
        Project p = new Project(name.trim());
        working.projects.add(p);
        listModel.addElement(p);
        projectList.setSelectedValue(p, true);
    }

    private void renameProject() {
        Project p = projectList.getSelectedValue();
        if (p == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, I18n.t("project.prompt.rename"), p.name);
        if (name == null || name.isBlank()) {
            return;
        }
        p.name = name.trim();
        projectList.repaint();
    }

    private void deleteProject() {
        Project p = projectList.getSelectedValue();
        if (p == null) {
            return;
        }
        int r = JOptionPane.showConfirmDialog(this, I18n.t("project.confirm.delete", p.name),
                I18n.t("project.confirm.deleteTitle"), JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            working.projects.remove(p);
            listModel.removeElement(p);
            bound = null;
            tableModel.setRowCount(0);
        }
    }

    /** Visible profiles, alphabetical (case-insensitive). (#14) */
    private List<WtProfile> sortedVisible() {
        List<WtProfile> list = new ArrayList<>(settings.visible());
        list.sort(Comparator.comparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Profile for a freshly added tab row: the configured default if available, else the first. (#14) */
    private String defaultRowProfile() {
        List<WtProfile> visible = sortedVisible();
        if (visible.isEmpty()) {
            return "";
        }
        if (defaultProfileName != null) {
            for (WtProfile p : visible) {
                if (p.name.equals(defaultProfileName)) {
                    return defaultProfileName;
                }
            }
        }
        return visible.get(0).name;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static ProjectsFile deepCopy(ProjectsFile src) {
        ProjectsFile copy = new ProjectsFile();
        copy.activeProject = src.activeProject;
        for (Project p : src.projects) {
            Project np = new Project(p.name);
            for (TabSpec t : p.tabs) {
                np.tabs.add(t.copy());
            }
            copy.projects.add(np);
        }
        return copy;
    }
}
