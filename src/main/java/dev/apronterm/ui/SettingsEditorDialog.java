package dev.apronterm.ui;

import dev.apronterm.wt.WtSettings;
import dev.apronterm.wt.WtSettingsService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;

/**
 * A modeless editor for Windows Terminal's settings.json. Saving writes the file and triggers an
 * immediate reload of the profiles, so changes take effect at once. External changes (e.g. from
 * VS Code) are picked up too and, if the editor has no unsaved changes, refresh the text.
 */
public final class SettingsEditorDialog extends JDialog implements WtSettingsService.Listener {

    private final WtSettingsService service;
    private final JTextArea textArea = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private boolean dirty;

    public SettingsEditorDialog(Frame owner, WtSettingsService service) {
        super(owner, "Profile bearbeiten – settings.json", false);
        this.service = service;

        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setTabSize(2);

        JButton save = new JButton("Speichern");
        save.addActionListener(e -> save());
        JButton reload = new JButton("Neu laden");
        reload.addActionListener(e -> reloadFromDisk(true));
        JButton close = new JButton("Schließen");
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(save);
        buttons.add(reload);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        south.add(status, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);

        JLabel header = new JLabel(service.getSettingsPath().toString());
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        reloadFromDisk(false);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markDirty(); }
            @Override public void removeUpdate(DocumentEvent e) { markDirty(); }
            @Override public void changedUpdate(DocumentEvent e) { markDirty(); }
        });

        service.addListener(this);
        setSize(820, 640);
        setLocationRelativeTo(owner);

        // Ctrl+S to save.
        getRootPane().registerKeyboardAction(e -> save(),
                javax.swing.KeyStroke.getKeyStroke("control S"),
                JPanel.WHEN_IN_FOCUSED_WINDOW);
    }

    private void markDirty() {
        if (!dirty) {
            dirty = true;
            status.setText("Ungespeicherte Änderungen");
        }
    }

    private void clearDirty() {
        dirty = false;
        status.setText("Gespeichert");
    }

    private void save() {
        try {
            service.writeRaw(textArea.getText());
            clearDirty();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Speichern fehlgeschlagen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadFromDisk(boolean confirmIfDirty) {
        if (confirmIfDirty && dirty) {
            int r = JOptionPane.showConfirmDialog(this,
                    "Ungespeicherte Änderungen verwerfen und neu laden?",
                    "Neu laden", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            textArea.setText(service.readRaw());
            textArea.setCaretPosition(0);
            clearDirty();
            status.setText(" ");
        } catch (IOException e) {
            textArea.setText("// Konnte settings.json nicht lesen: " + e.getMessage());
        }
    }

    @Override
    public void settingsReloaded(WtSettings settings) {
        // External change picked up by the watcher: refresh only if we have nothing unsaved.
        if (!dirty && isShowing()) {
            try {
                String onDisk = service.readRaw();
                if (!onDisk.equals(textArea.getText())) {
                    textArea.setText(onDisk);
                    textArea.setCaretPosition(0);
                    status.setText("Von außen aktualisiert");
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void dispose() {
        service.removeListener(this);
        super.dispose();
    }
}
