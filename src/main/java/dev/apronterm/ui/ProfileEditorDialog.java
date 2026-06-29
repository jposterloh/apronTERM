package dev.apronterm.ui;

import dev.apronterm.app.I18n;
import dev.apronterm.wt.WtSettingsService;
import dev.apronterm.wt.WtSettingsWriter;
import dev.apronterm.wt.WtSettingsWriter.Profile;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Form/table editor for Windows Terminal profiles. Saving goes through {@link WtSettingsWriter},
 * which preserves comments/formatting and only rewrites changed profiles. The raw JSON editor
 * ({@link SettingsEditorDialog}) remains available as an expert fallback.
 */
public final class ProfileEditorDialog extends JDialog {

    private static final int COL_NAME = 0;
    private static final int COL_CMD = 1;
    private static final int COL_DIR = 2;
    private static final int COL_ICON = 3;
    private static final int COL_HIDDEN = 4;
    private static final int COL_GUID = 5; // kept in the model, hidden from view

    private final WtSettingsService service;
    private final WtSettingsWriter writer; // snapshot taken when the dialog opened
    private final DefaultTableModel model;
    private final JTable table;

    public ProfileEditorDialog(Frame owner, WtSettingsService service) throws IOException {
        super(owner, I18n.t("profileEditor.title"), true);
        this.service = service;
        this.writer = new WtSettingsWriter(service.readRaw());

        model = new DefaultTableModel(
                new Object[]{I18n.t("profileEditor.column.name"), I18n.t("profileEditor.column.commandline"),
                        I18n.t("profileEditor.column.startingDirectory"), I18n.t("profileEditor.column.icon"),
                        I18n.t("profileEditor.column.hidden"), I18n.t("profileEditor.column.guid")}, 0) {
            @Override
            public Class<?> getColumnClass(int c) {
                return c == COL_HIDDEN ? Boolean.class : String.class;
            }
        };
        for (Profile p : writer.profiles()) {
            model.addRow(new Object[]{
                    nz(p.name), nz(p.commandline), nz(p.startingDirectory), nz(p.icon), p.hidden, nz(p.guid)});
        }

        table = new JTable(model);
        table.removeColumn(table.getColumnModel().getColumn(COL_GUID)); // hide guid (model keeps it)
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(140);
        table.getColumnModel().getColumn(COL_CMD).setPreferredWidth(340);
        table.getColumnModel().getColumn(COL_DIR).setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_HIDDEN).setPreferredWidth(70);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(940, 480);
        setLocationRelativeTo(owner);
    }

    private JPanel buildButtons() {
        JButton add = new JButton(I18n.t("profileEditor.add"));
        add.addActionListener(e -> {
            stopEditing();
            model.addRow(new Object[]{"", "", "", "", Boolean.FALSE, ""});
        });
        JButton remove = new JButton(I18n.t("profileEditor.remove"));
        remove.addActionListener(e -> {
            stopEditing();
            int r = table.getSelectedRow();
            if (r >= 0) {
                model.removeRow(table.convertRowIndexToModel(r));
            }
        });
        JButton save = new JButton(I18n.t("profileEditor.save"));
        save.addActionListener(e -> save());
        JButton cancel = new JButton(I18n.t("button.cancel"));
        cancel.addActionListener(e -> dispose());

        JPanel south = new JPanel();
        south.add(add);
        south.add(remove);
        south.add(cancel);
        south.add(save);
        return south;
    }

    private void save() {
        stopEditing();
        List<Profile> desired = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            String name = str(model.getValueAt(r, COL_NAME));
            if (name.isBlank()) {
                continue; // skip nameless rows
            }
            Profile p = new Profile();
            p.guid = blankToNull(str(model.getValueAt(r, COL_GUID)));
            p.name = name;
            p.commandline = blankToNull(str(model.getValueAt(r, COL_CMD)));
            p.startingDirectory = blankToNull(str(model.getValueAt(r, COL_DIR)));
            p.icon = blankToNull(str(model.getValueAt(r, COL_ICON)));
            p.hidden = Boolean.TRUE.equals(model.getValueAt(r, COL_HIDDEN));
            desired.add(p);
        }
        if (desired.isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("profileEditor.error.noProfile"));
            return;
        }
        try {
            service.writeRaw(writer.render(desired)); // writes settings.json + triggers reload
            dispose();
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, I18n.t("profileEditor.error.saveFailed", ex.getMessage()),
                    I18n.t("profileEditor.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
