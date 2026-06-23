package dev.apronterm.ui;

import dev.apronterm.app.ApronTermConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Central settings dialog. Currently exposes the UI theme; structured so further sections can be
 * added to {@link #buildSettingsPanel()} without touching the rest.
 *
 * <p>On apply, the (live) {@link ApronTermConfig} is updated, persisted, and the supplied
 * {@link Applier} re-applies everything derived from the config.
 */
public final class SettingsDialog extends JDialog {

    /** Re-applies the (already updated + saved) config to the running application. */
    public interface Applier {
        void apply(ApronTermConfig config);
    }

    private final ApronTermConfig config;
    private final Applier applier;

    private final JRadioButton darkRadio = new JRadioButton("Dunkel");
    private final JRadioButton lightRadio = new JRadioButton("Hell");

    public SettingsDialog(Frame owner, ApronTermConfig config, Applier applier) {
        super(owner, "Einstellungen", true);
        this.config = config;
        this.applier = applier;

        add(buildSettingsPanel(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        loadFromConfig();

        pack();
        // Breit genug, dass Abschnittstitel nicht abgeschnitten werden; volle Breite nutzbar.
        setSize(Math.max(getWidth(), 440), getHeight());
        setMinimumSize(new Dimension(360, getHeight()));
        setLocationRelativeTo(owner);
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 8, 0);

        panel.add(buildAppearanceSection(), c);
        // Weitere Abschnitte hier mit panel.add(section, c) ergänzen.

        // Filler unten, damit die Abschnitte oben bleiben statt mittig zu schweben.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), filler);

        return panel;
    }

    private JPanel buildAppearanceSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Darstellung"));

        ButtonGroup group = new ButtonGroup();
        group.add(darkRadio);
        group.add(lightRadio);
        section.add(darkRadio);
        section.add(lightRadio);

        return section;
    }

    private JPanel buildButtons() {
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            apply();
            dispose();
        });
        JButton apply = new JButton("Übernehmen");
        apply.addActionListener(e -> apply());
        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> dispose());

        JPanel south = new JPanel();
        south.add(ok);
        south.add(apply);
        south.add(cancel);
        return south;
    }

    private void loadFromConfig() {
        darkRadio.setSelected(config.isDark());
        lightRadio.setSelected(!config.isDark());
    }

    private void apply() {
        config.setDark(darkRadio.isSelected());
        config.save();
        applier.apply(config);
    }
}
