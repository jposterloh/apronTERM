package dev.apronterm.wt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of the relevant parts of Windows Terminal's settings.json:
 * the profile list plus the default-profile pointer.
 */
public final class WtSettings {

    private final String defaultProfileGuid;
    private final List<WtProfile> profiles;
    private final Map<String, WtProfile> byName;

    public WtSettings(String defaultProfileGuid, List<WtProfile> profiles) {
        this.defaultProfileGuid = defaultProfileGuid;
        this.profiles = List.copyOf(profiles);
        Map<String, WtProfile> idx = new LinkedHashMap<>();
        for (WtProfile p : profiles) {
            if (p.name != null) {
                idx.put(p.name, p);
            }
        }
        this.byName = Collections.unmodifiableMap(idx);
    }

    /** All profiles, including hidden ones, in file order. */
    public List<WtProfile> all() {
        return profiles;
    }

    /** Non-hidden profiles, in file order. */
    public List<WtProfile> visible() {
        List<WtProfile> out = new ArrayList<>();
        for (WtProfile p : profiles) {
            if (!p.hidden) {
                out.add(p);
            }
        }
        return out;
    }

    public WtProfile byName(String name) {
        return byName.get(name);
    }

    public WtProfile defaultProfile() {
        if (defaultProfileGuid != null) {
            for (WtProfile p : profiles) {
                if (defaultProfileGuid.equals(p.guid)) {
                    return p;
                }
            }
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public static WtSettings empty() {
        return new WtSettings(null, List.of());
    }
}
