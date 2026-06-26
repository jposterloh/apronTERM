package dev.apronterm.terminal;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves terminal fonts against the fonts actually installed on this machine. (#13)
 *
 * <p>JediTerm/Java2D draws each cell with a single font and does not fall back for missing glyphs,
 * so symbols a font lacks (Nerd-Font icons, box drawing, …) render as rectangles. We therefore pick
 * a well-covered <em>fallback</em> font (a Nerd Font if present) that {@code ThemedTerminalWidget}
 * uses per-glyph when the primary font can't display a character — mirroring what Windows Terminal
 * does via DirectWrite.
 */
public final class TerminalFonts {

    /** Default primary family (matches Windows Terminal) and size. */
    public static final String DEFAULT_PRIMARY = "Cascadia Mono";
    public static final int DEFAULT_SIZE = 12;

    /** Preferred primary families, best first, when the configured one isn't available. */
    private static final String[] PRIMARY_PREFERENCE = {"Cascadia Mono", "Consolas", "Monospaced"};

    /**
     * Preferred fallback families, best first. "Mono"/"NFM" Nerd-Font variants come first because
     * their icon glyphs are single-cell, which keeps the fixed terminal grid aligned.
     */
    private static final String[] FALLBACK_PREFERENCE = {
            "JetBrainsMono NFM", "JetBrainsMonoNL NFM", "JetBrainsMono NF", "Cascadia Code",
    };

    private TerminalFonts() {
    }

    private static Set<String> available() {
        Set<String> names = new LinkedHashSet<>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            names.add(f);
        }
        return names;
    }

    private static boolean has(Set<String> avail, String family) {
        for (String f : avail) {
            if (f.equalsIgnoreCase(family)) {
                return true;
            }
        }
        return false;
    }

    /** The primary family to use: the configured one if installed, else the first available preference. */
    public static String resolvePrimaryFamily(String configured) {
        Set<String> avail = available();
        if (configured != null && !configured.isBlank() && has(avail, configured)) {
            return configured;
        }
        for (String f : PRIMARY_PREFERENCE) {
            if (has(avail, f)) {
                return f;
            }
        }
        return "Monospaced";
    }

    public static int resolveSize(int configured) {
        return configured > 0 ? configured : DEFAULT_SIZE;
    }

    /**
     * The best installed fallback family for missing glyphs, or {@code null} if none stands out.
     * Tries the explicit preference list, then any installed "Nerd Font Mono" / "Nerd Font" family.
     */
    public static String resolveFallbackFamily() {
        Set<String> avail = available();
        for (String f : FALLBACK_PREFERENCE) {
            if (has(avail, f)) {
                return f;
            }
        }
        String[] patterns = {"nerd font mono", "nerd font", "nfm", " nf"};
        for (String pat : patterns) {
            for (String f : avail) {
                if (f.toLowerCase(Locale.ROOT).contains(pat)) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Ordered fallback families for glyphs the primary font lacks: a Nerd Font first (powerline /
     * private-use icons), then Java's logical composite fonts, which fall back across many physical
     * fonts and cover most of the BMP (box drawing, ☐/●/❯, etc.). (#13)
     */
    public static List<String> fallbackFamilies() {
        List<String> out = new ArrayList<>();
        String nf = resolveFallbackFamily();
        if (nf != null) {
            out.add(nf);
        }
        out.add(Font.MONOSPACED); // logical composite — broad coverage, keeps cell width
        out.add(Font.DIALOG);     // logical composite — widest coverage as a last structured resort
        return out;
    }

    /** Any installed family that can display {@code codepoint}, or {@code null} if none can. */
    public static String familyDisplaying(int codepoint) {
        for (String f : available()) {
            if (new Font(f, Font.PLAIN, 1).canDisplay(codepoint)) {
                return f;
            }
        }
        return null;
    }

    /** Monospaced-ish families for the settings dropdown: a curated set first, then all installed. */
    public static List<String> selectableFamilies() {
        Set<String> avail = available();
        List<String> out = new ArrayList<>();
        for (String f : new String[]{"Cascadia Mono", "Cascadia Code", "Consolas",
                "JetBrainsMono NFM", "JetBrainsMono NF", "Monospaced"}) {
            if (has(avail, f) && !out.contains(f)) {
                out.add(f);
            }
        }
        List<String> rest = new ArrayList<>(avail);
        rest.sort(String.CASE_INSENSITIVE_ORDER);
        for (String f : rest) {
            if (!out.contains(f)) {
                out.add(f);
            }
        }
        return out;
    }
}
