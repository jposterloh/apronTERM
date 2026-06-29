package dev.apronterm;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.apronterm.app.ApronTermConfig;
import dev.apronterm.app.I18n;
import dev.apronterm.project.ProjectStore;
import dev.apronterm.ui.MainFrame;
import dev.apronterm.wt.WtSettingsService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Application entry point. */
public final class ApronTerm {

    private ApronTerm() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ApronTerm::start);
    }

    private static void start() {
        ApronTermConfig config = ApronTermConfig.load();
        I18n.setLanguage(config.language); // before any UI strings are resolved

        if (config.isDark()) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        WtSettingsService wtService = new WtSettingsService(config.effectiveWtSettingsPath());
        wtService.start();

        if (wtService.current().all().isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    I18n.t("startup.noProfiles.message", wtService.getSettingsPath()),
                    "apronTERM", JOptionPane.WARNING_MESSAGE);
        }

        new MainFrame(wtService, new ProjectStore(), config).setVisible(true);
    }
}
