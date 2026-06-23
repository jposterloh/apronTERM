package dev.apronterm.wt;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.apronterm.app.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comment- and format-preserving editor for the {@code profiles.list} array of Windows Terminal's
 * settings.json (JSONC).
 *
 * <p>Strategy: everything outside {@code profiles.list} is left byte-for-byte untouched. Inside the
 * list, unchanged profiles are copied verbatim (keeping their comments/formatting); only profiles
 * that actually changed are re-emitted — and even then their non-edited keys are preserved verbatim,
 * so data like {@code source} or {@code colorScheme} is never lost. New profiles are generated,
 * deleted ones are dropped. No whole-file Jackson re-dump.
 */
public final class WtSettingsWriter {

    /** The five fields the form editor exposes. */
    public static final class Profile {
        public String guid;   // identity; null/blank for a new profile
        public String name;
        public String commandline;
        public String startingDirectory;
        public String icon;
        public boolean hidden;

        public Profile() {
        }
    }

    private static final class Parsed {
        String guid;
        String name;
        String commandline;
        String startingDirectory;
        String icon;
        boolean hidden;
        int objStart;
        int objEnd;
        final List<String> keyOrder = new ArrayList<>();
        final Map<String, String> valueText = new LinkedHashMap<>(); // key -> verbatim value source
    }

    private final String raw;
    private final List<Parsed> parsed = new ArrayList<>();
    private final Map<String, Parsed> byGuid = new LinkedHashMap<>();
    private int arrStart = -1; // offset of '['
    private int arrEnd = -1;   // offset of ']'
    private String elemIndent = "        ";
    private String fieldIndent = "            ";
    private String closingIndent = "    ";

    public WtSettingsWriter(String raw) {
        this.raw = raw;
        parse();
    }

    /** The profiles as currently stored, in file order, for populating the form. */
    public List<Profile> profiles() {
        List<Profile> out = new ArrayList<>();
        for (Parsed p : parsed) {
            Profile f = new Profile();
            f.guid = p.guid;
            f.name = p.name;
            f.commandline = p.commandline;
            f.startingDirectory = p.startingDirectory;
            f.icon = p.icon;
            f.hidden = p.hidden;
            out.add(f);
        }
        return out;
    }

    /** Render new settings.json text for the desired profile list (in the given order). */
    public String render(List<Profile> desired) {
        if (arrStart < 0 || arrEnd < 0) {
            throw new IllegalStateException("profiles.list not found in settings.json");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(raw, 0, arrStart + 1); // up to and including '['

        for (int i = 0; i < desired.size(); i++) {
            sb.append(i == 0 ? "" : ",");
            sb.append('\n').append(elemIndent);
            Profile d = desired.get(i);
            Parsed orig = (d.guid == null) ? null : byGuid.get(d.guid);
            if (orig != null && unchanged(orig, d)) {
                sb.append(raw, orig.objStart, orig.objEnd); // verbatim
            } else if (orig != null) {
                sb.append(rebuild(orig, d)); // edited: keep other keys verbatim
            } else {
                sb.append(generate(d)); // new
            }
        }

        sb.append('\n').append(closingIndent);
        sb.append(raw, arrEnd, raw.length()); // from ']' onward
        return sb.toString();
    }

    // ---- rendering helpers -------------------------------------------------

    private boolean unchanged(Parsed o, Profile d) {
        return eq(o.name, d.name)
                && eq(o.commandline, d.commandline)
                && eq(o.startingDirectory, d.startingDirectory)
                && eq(o.icon, d.icon)
                && o.hidden == d.hidden;
    }

    private String rebuild(Parsed orig, Profile d) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String k : orig.keyOrder) {
            fields.put(k, orig.valueText.get(k)); // verbatim values for all keys
        }
        fields.put("name", jsonString(d.name));
        applyString(fields, "commandline", d.commandline);
        applyString(fields, "startingDirectory", d.startingDirectory);
        applyString(fields, "icon", d.icon);
        if (d.hidden) {
            fields.put("hidden", "true");
        } else {
            fields.remove("hidden");
        }
        return emit(fields);
    }

    private String generate(Profile d) {
        Map<String, String> fields = new LinkedHashMap<>();
        String guid = (d.guid != null && !d.guid.isBlank()) ? d.guid : "{" + UUID.randomUUID() + "}";
        fields.put("guid", jsonString(guid));
        fields.put("name", jsonString(d.name == null ? "" : d.name));
        applyString(fields, "commandline", d.commandline);
        applyString(fields, "startingDirectory", d.startingDirectory);
        applyString(fields, "icon", d.icon);
        if (d.hidden) {
            fields.put("hidden", "true");
        }
        return emit(fields);
    }

    private void applyString(Map<String, String> fields, String key, String value) {
        if (value == null || value.isBlank()) {
            fields.remove(key);
        } else {
            fields.put(key, jsonString(value));
        }
    }

    private String emit(Map<String, String> fields) {
        StringBuilder o = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            o.append('\n').append(fieldIndent)
                    .append(jsonString(e.getKey())).append(": ").append(e.getValue());
            if (++i < fields.size()) {
                o.append(',');
            }
        }
        o.append('\n').append(elemIndent).append('}');
        return o.toString();
    }

    private static String jsonString(String s) {
        try {
            return Json.WT.writeValueAsString(s == null ? "" : s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean eq(String a, String b) {
        String na = (a == null) ? "" : a;
        String nb = (b == null) ? "" : b;
        return na.equals(nb);
    }

    // ---- parsing -----------------------------------------------------------

    private void parse() {
        JsonFactory f = Json.WT.getFactory();
        try (JsonParser p = f.createParser(raw)) {
            boolean sawProfiles = false;
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t != JsonToken.FIELD_NAME) {
                    continue;
                }
                String fn = p.currentName();
                if (fn.equals("profiles")) {
                    sawProfiles = true;
                } else if (fn.equals("list") && sawProfiles) {
                    parseList(p);
                    break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not parse settings.json", e);
        }
        computeIndents();
    }

    private void parseList(JsonParser p) throws IOException {
        p.nextToken(); // START_ARRAY
        arrStart = (int) p.getTokenLocation().getCharOffset();
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
            if (t == JsonToken.START_OBJECT) {
                parsed.add(parseProfile(p));
            }
        }
        arrEnd = (int) p.getTokenLocation().getCharOffset();
        for (Parsed pp : parsed) {
            if (pp.guid != null) {
                byGuid.put(pp.guid, pp);
            }
        }
    }

    private Parsed parseProfile(JsonParser p) throws IOException {
        Parsed pp = new Parsed();
        pp.objStart = (int) p.getTokenLocation().getCharOffset();
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
            if (t != JsonToken.FIELD_NAME) {
                continue;
            }
            String k = p.currentName();
            JsonToken vt = p.nextToken();
            int vs = (int) p.getTokenLocation().getCharOffset();
            if (vt == JsonToken.START_OBJECT || vt == JsonToken.START_ARRAY) {
                p.skipChildren();
            } else {
                p.finishToken(); // force full parse so the end offset is correct
            }
            int ve = (int) p.getCurrentLocation().getCharOffset();

            pp.keyOrder.add(k);
            pp.valueText.put(k, raw.substring(vs, ve));
            switch (k) {
                case "guid" -> pp.guid = p.getValueAsString();
                case "name" -> pp.name = p.getValueAsString();
                case "commandline" -> pp.commandline = p.getValueAsString();
                case "startingDirectory" -> pp.startingDirectory = p.getValueAsString();
                case "icon" -> pp.icon = p.getValueAsString();
                case "hidden" -> pp.hidden = p.getValueAsBoolean();
                default -> { /* preserved verbatim, not surfaced in the form */ }
            }
        }
        pp.objEnd = (int) p.getCurrentLocation().getCharOffset();
        return pp;
    }

    private void computeIndents() {
        if (!parsed.isEmpty()) {
            elemIndent = indentBefore(parsed.get(0).objStart);
            // first field indent: first newline inside the first object
            int firstFieldNl = raw.indexOf('\n', parsed.get(0).objStart);
            if (firstFieldNl >= 0) {
                int q = raw.indexOf('"', firstFieldNl);
                if (q > firstFieldNl) {
                    fieldIndent = raw.substring(firstFieldNl + 1, q);
                }
            }
        }
        if (arrEnd >= 0) {
            closingIndent = indentBefore(arrEnd);
        }
    }

    /** The whitespace from the start of {@code offset}'s line up to {@code offset}. */
    private String indentBefore(int offset) {
        int nl = raw.lastIndexOf('\n', offset - 1);
        String s = raw.substring(nl + 1, offset);
        return s.isBlank() ? s : "";
    }
}
