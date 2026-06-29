package dev.apronterm.ui;

import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.app.I18n;
import dev.apronterm.terminal.TerminalFonts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;

/**
 * Central settings dialog. Exposes appearance, language, fonts and behaviour; structured so further
 * sections can be added to {@link #buildSettingsPanel()} without touching the rest.
 *
 * <p>On apply, the (live) {@link ApronTermConfig} is updated, persisted, and the supplied
 * {@link Applier} re-applies everything derived from the config.
 */
public final class SettingsDialog extends JDialog {

    /** Re-applies the (already updated + saved) config to the running application. */
    public interface Applier {
        void apply(ApronTermConfig config);
    }

    /** Language codes parallel to the {@link #languageCombo} entries (System / Deutsch / English). */
    private static final String[] LANG_CODES = {null, "de", "en"};

    /** Combo entry meaning "fall back to Windows Terminal's default profile". */
    private final String wtDefault = I18n.t("settings.newTabs.wtDefault");

    private final ApronTermConfig config;
    private final Applier applier;
    private final List<String> profileNames;

    private final JRadioButton darkRadio = new JRadioButton(I18n.t("settings.theme.dark"));
    private final JRadioButton lightRadio = new JRadioButton(I18n.t("settings.theme.light"));
    private final JComboBox<String> languageCombo = new JComboBox<>();
    private final JCheckBox autoCloseCheck =
            new JCheckBox(I18n.t("settings.behavior.autoClose"));
    private final JComboBox<String> defaultProfileCombo = new JComboBox<>();
    private final JComboBox<String> fontCombo = new JComboBox<>();
    private final JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(
            TerminalFonts.DEFAULT_SIZE, 6, 72, 1));

    public SettingsDialog(Frame owner, ApronTermConfig config, List<String> profileNames,
                          Applier applier) {
        super(owner, I18n.t("settings.title"), true);
        this.config = config;
        this.applier = applier;
        this.profileNames = profileNames;

        add(buildSettingsPanel(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        loadFromConfig();

        pack();
        // Wide enough that section titles aren't clipped; full width usable.
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
        panel.add(buildLanguageSection(), c);
        panel.add(buildFontSection(), c);
        panel.add(buildBehaviorSection(), c);
        panel.add(buildNewTabsSection(), c);
        // Add further sections here with panel.add(section, c).

        // Filler at the bottom so the sections stay top-aligned instead of floating centred.
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
        section.setBorder(BorderFactory.createTitledBorder(I18n.t("settings.section.appearance")));

        ButtonGroup group = new ButtonGroup();
        group.add(darkRadio);
        group.add(lightRadio);
        section.add(darkRadio);
        section.add(lightRadio);

        return section;
    }

    private JPanel buildLanguageSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(I18n.t("settings.section.language")));

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(I18n.t("settings.language.system"));
        model.addElement("Deutsch");
        model.addElement("English");
        languageCombo.setModel(model);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel(I18n.t("settings.language.label")), BorderLayout.WEST);
        row.add(languageCombo, BorderLayout.CENTER);
        section.add(row);

        JLabel hint = new JLabel(I18n.t("settings.language.restartHint"));
        hint.setEnabled(false);
        section.add(hint);

        return section;
    }

    private JPanel buildFontSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(I18n.t("settings.section.font")));

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String family : TerminalFonts.selectableFamilies()) {
            model.addElement(family);
        }
        fontCombo.setModel(model);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel(I18n.t("settings.font.family")), BorderLayout.WEST);
        row.add(fontCombo, BorderLayout.CENTER);
        JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
        sizeRow.add(new JLabel(I18n.t("settings.font.size")), BorderLayout.WEST);
        sizeRow.add(fontSizeSpinner, BorderLayout.CENTER);
        row.add(sizeRow, BorderLayout.EAST);
        section.add(row);

        JLabel hint = new JLabel(I18n.t("settings.font.fallbackHint"));
        hint.setEnabled(false);
        section.add(hint);

        return section;
    }

    private JPanel buildBehaviorSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(I18n.t("settings.section.behavior")));

        section.add(autoCloseCheck);

        return section;
    }

    private JPanel buildNewTabsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(I18n.t("settings.section.newTabs")));

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(wtDefault);
        for (String name : profileNames) {
            model.addElement(name);
        }
        defaultProfileCombo.setModel(model);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel(I18n.t("settings.newTabs.defaultProfile")), BorderLayout.WEST);
        row.add(defaultProfileCombo, BorderLayout.CENTER);
        section.add(row);

        return section;
    }

    private JPanel buildButtons() {
        JButton ok = new JButton(I18n.t("button.ok"));
        ok.addActionListener(e -> {
            apply();
            dispose();
        });
        JButton apply = new JButton(I18n.t("button.apply"));
        apply.addActionListener(e -> apply());
        JButton cancel = new JButton(I18n.t("button.cancel"));
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
        languageCombo.setSelectedIndex(languageIndex(config.language));
        autoCloseCheck.setSelected(config.autoCloseExitedTabs);
        if (config.defaultProfile != null && profileNames.contains(config.defaultProfile)) {
            defaultProfileCombo.setSelectedItem(config.defaultProfile);
        } else {
            defaultProfileCombo.setSelectedItem(wtDefault);
        }
        fontCombo.setSelectedItem(TerminalFonts.resolvePrimaryFamily(config.terminalFont));
        fontSizeSpinner.setValue(TerminalFonts.resolveSize(config.terminalFontSize));
    }

    private static int languageIndex(String code) {
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (Objects.equals(LANG_CODES[i], code)) {
                return i;
            }
        }
        return 0; // System default
    }

    private void apply() {
        String previousLanguage = config.language;
        config.setDark(darkRadio.isSelected());
        config.language = LANG_CODES[languageCombo.getSelectedIndex()];
        config.autoCloseExitedTabs = autoCloseCheck.isSelected();
        Object sel = defaultProfileCombo.getSelectedItem();
        config.defaultProfile = (sel == null || wtDefault.equals(sel)) ? null : (String) sel;
        config.terminalFont = (String) fontCombo.getSelectedItem();
        config.terminalFontSize = (Integer) fontSizeSpinner.getValue();
        config.save();
        applier.apply(config);

        // The UI is built once with the active locale; a language change needs a restart.
        if (!Objects.equals(previousLanguage, config.language)) {
            JOptionPane.showMessageDialog(this,
                    I18n.t("settings.language.restartMessage"),
                    I18n.t("settings.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
