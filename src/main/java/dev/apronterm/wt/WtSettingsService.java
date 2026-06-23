package dev.apronterm.wt;

import com.fasterxml.jackson.databind.JsonNode;
import dev.apronterm.app.Json;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads Windows Terminal profiles from settings.json and watches the file so changes become
 * effective immediately. Listeners are notified on the Swing EDT after each successful reload.
 *
 * <p>The file is also editable through this service ({@link #readRaw()} / {@link #writeRaw(String)}),
 * which is what the in-app settings editor uses.
 */
public final class WtSettingsService {

    /** Listener invoked (on the EDT) whenever the settings have been (re)loaded. */
    public interface Listener {
        void settingsReloaded(WtSettings settings);
    }

    private final Path settingsPath;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile WtSettings current = WtSettings.empty();
    private Thread watchThread;
    private WatchService watchService;
    private volatile boolean running;

    public WtSettingsService(Path settingsPath) {
        this.settingsPath = settingsPath;
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public WtSettings current() {
        return current;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Parse settings.json now. Failures leave the previous snapshot in place and are logged. */
    public WtSettings reloadNow() {
        try {
            WtSettings parsed = parse(readRaw());
            current = parsed;
            notifyListeners(parsed);
            return parsed;
        } catch (Exception e) {
            System.err.println("Failed to parse " + settingsPath + ": " + e.getMessage());
            return current;
        }
    }

    public String readRaw() throws IOException {
        return Files.readString(settingsPath);
    }

    /** Writes new content to settings.json and immediately reloads. */
    public void writeRaw(String content) throws IOException {
        Files.writeString(settingsPath, content);
        reloadNow();
    }

    /** Starts a background watcher on the settings file's directory. */
    public void start() {
        reloadNow();
        if (!Files.isReadable(settingsPath)) {
            return;
        }
        try {
            Path dir = settingsPath.toAbsolutePath().getParent();
            watchService = dir.getFileSystem().newWatchService();
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            running = true;
            watchThread = new Thread(this::watchLoop, "wt-settings-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            System.err.println("Could not watch settings.json: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void watchLoop() {
        String fileName = settingsPath.getFileName().toString();
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                return;
            }
            boolean relevant = false;
            for (var event : key.pollEvents()) {
                Object ctx = event.context();
                if (ctx instanceof Path p && fileName.equals(p.getFileName().toString())) {
                    relevant = true;
                }
            }
            key.reset();
            if (relevant) {
                // Debounce: editors often emit several events per save.
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    return;
                }
                reloadNow();
            }
        }
    }

    private void notifyListeners(WtSettings settings) {
        Runnable r = () -> {
            for (Listener l : listeners) {
                l.settingsReloaded(settings);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static WtSettings parse(String jsonc) throws IOException {
        JsonNode root = Json.WT.readTree(jsonc);
        String defaultGuid = text(root, "defaultProfile");

        JsonNode profilesNode = root.path("profiles");
        JsonNode defaults = profilesNode.path("defaults");
        JsonNode list = profilesNode.path("list");

        List<WtProfile> profiles = new ArrayList<>();
        if (list.isArray()) {
            for (JsonNode p : list) {
                profiles.add(toProfile(p, defaults));
            }
        }
        return new WtSettings(defaultGuid, profiles);
    }

    private static WtProfile toProfile(JsonNode p, JsonNode defaults) {
        return new WtProfile(
                text(p, "guid"),
                text(p, "name"),
                inherited(p, defaults, "commandline"),
                inherited(p, defaults, "startingDirectory"),
                inherited(p, defaults, "icon"),
                text(p, "source"),
                p.path("hidden").asBoolean(false));
    }

    /** A profile field, falling back to the profiles.defaults value if the profile omits it. */
    private static String inherited(JsonNode profile, JsonNode defaults, String field) {
        String v = text(profile, field);
        return (v != null) ? v : text(defaults, field);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }
}
