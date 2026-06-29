package dev.apronterm.app;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

/**
 * App-wide UI string lookup. Strings live in {@code dev/apronterm/i18n/messages[_xx].properties}
 * (UTF-8; Java 9+ reads property bundles as UTF-8). {@code messages.properties} is English and the
 * fallback; {@code messages_de.properties} is German.
 *
 * <p>Call {@link #setLanguage(String)} once at startup before any UI is built. Keys use a
 * dotted, per-area namespace (e.g. {@code menu.file}, {@code dialog.settings.title}).
 */
public final class I18n {

    private static final String BUNDLE = "dev.apronterm.i18n.messages";

    private static volatile ResourceBundle bundle = load(Locale.getDefault());

    private I18n() {
    }

    /** Active language: {@code "de"}, {@code "en"}, or {@code null}/blank to follow the OS locale. */
    public static void setLanguage(String lang) {
        Locale locale = (lang == null || lang.isBlank())
                ? Locale.getDefault()
                : Locale.forLanguageTag(lang);
        bundle = load(locale);
    }

    private static ResourceBundle load(Locale locale) {
        // No-fallback control: an absent locale bundle (e.g. messages_en) must fall straight back to
        // the English base (messages.properties), NOT to the OS default locale's bundle — otherwise
        // selecting "English" on a German machine would still load German. (#i18n)
        try {
            return ResourceBundle.getBundle(BUNDLE, locale,
                    Control.getNoFallbackControl(Control.FORMAT_PROPERTIES));
        } catch (MissingResourceException e) {
            // Resources somehow not on the classpath: degrade to showing keys rather than crashing.
            System.err.println("UI string bundle missing (" + BUNDLE + "); showing keys: " + e.getMessage());
            return null;
        }
    }

    /**
     * Translate {@code key}, formatting {@code args} with {@link MessageFormat} when given. Returns
     * the key itself if it is missing, so untranslated strings show up as visible gaps rather than
     * crashing.
     */
    public static String t(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle != null ? bundle.getString(key) : key;
        } catch (MissingResourceException e) {
            return key;
        }
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
