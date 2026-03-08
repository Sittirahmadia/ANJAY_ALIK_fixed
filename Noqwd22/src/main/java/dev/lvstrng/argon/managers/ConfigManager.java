package dev.lvstrng.argon.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ConfigManager — manages named config profiles stored as JSON files.
 * Each config is a separate file under the hidden argon data directory.
 */
public final class ConfigManager {

    private final Gson gson = new Gson();
    private final Path configDir;
    private String activeConfig = "default";

    public ConfigManager() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        String base   = isWin ? System.getProperty("java.io.tmpdir") : System.getProperty("user.home");
        configDir     = Paths.get(base, "UJHfsGGjbPfVZ", "configs");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String getActiveConfig() { return activeConfig; }

    /** All saved config names (without .json extension), sorted alphabetically. */
    public List<String> listConfigs() {
        ensureDir();
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(configDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .map(p -> p.getFileName().toString().replace(".json", ""))
                 .sorted()
                 .forEach(names::add);
        } catch (IOException ignored) {}
        return names;
    }

    /** Save current module state to a named config. */
    public void saveConfig(String name) {
        ensureDir();
        try {
            Files.writeString(configDir.resolve(sanitize(name) + ".json"), gson.toJson(buildProfile()));
            activeConfig = name;
        } catch (Exception ignored) {}
    }

    /** Load a named config and apply it to all modules. */
    public boolean loadConfig(String name) {
        Path p = configDir.resolve(sanitize(name) + ".json");
        if (!Files.isRegularFile(p)) return false;
        try {
            applyProfile(gson.fromJson(Files.readString(p), JsonObject.class));
            activeConfig = name;
            return true;
        } catch (Exception ignored) { return false; }
    }

    /** Delete a named config. */
    public void deleteConfig(String name) {
        try { Files.deleteIfExists(configDir.resolve(sanitize(name) + ".json")); } catch (IOException ignored) {}
        if (activeConfig.equals(name)) activeConfig = "default";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureDir() {
        try { Files.createDirectories(configDir); } catch (IOException ignored) {}
    }

    /** Strip chars that are illegal in filenames. */
    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private JsonObject buildProfile() {
        JsonObject root = new JsonObject();
        List<Module> modules = Argon.INSTANCE.getModuleManager().getModules();
        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            JsonObject mc = new JsonObject();
            mc.addProperty("enabled", mod.isEnabled());
            List<Setting<?>> settings = mod.getSettings();
            for (int si = 0; si < settings.size(); si++) {
                Setting<?> s = settings.get(si);
                String key   = String.valueOf(si);
                if      (s instanceof BooleanSetting b)   mc.addProperty(key, b.getValue());
                else if (s instanceof ModeSetting<?> m)   mc.addProperty(key, m.getModeIndex());
                else if (s instanceof NumberSetting n)     mc.addProperty(key, n.getValue());
                else if (s instanceof KeybindSetting k)    mc.addProperty(key, k.getKey());
                else if (s instanceof StringSetting st)    mc.addProperty(key, st.getValue());
                else if (s instanceof MinMaxSetting mm) {
                    JsonObject o = new JsonObject();
                    o.addProperty("1", mm.getMinValue());
                    o.addProperty("2", mm.getMaxValue());
                    mc.add(key, o);
                }
            }
            root.add(String.valueOf(i), mc);
        }
        return root;
    }

    private void applyProfile(JsonObject root) {
        List<Module> modules = Argon.INSTANCE.getModuleManager().getModules();
        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            JsonElement el = root.get(String.valueOf(i));
            if (el == null || !el.isJsonObject()) continue;
            JsonObject mc = el.getAsJsonObject();

            JsonElement en = mc.get("enabled");
            if (en != null && en.isJsonPrimitive()) {
                boolean want = en.getAsBoolean();
                if (want != mod.isEnabled()) mod.setEnabled(want);
            }
            List<Setting<?>> settings = mod.getSettings();
            for (int si = 0; si < settings.size(); si++) {
                Setting<?> s  = settings.get(si);
                JsonElement se = mc.get(String.valueOf(si));
                if (se == null) continue;
                if      (s instanceof BooleanSetting b)    b.setValue(se.getAsBoolean());
                else if (s instanceof ModeSetting<?> m)    m.setModeIndex(se.getAsInt());
                else if (s instanceof NumberSetting n)      n.setValue(se.getAsDouble());
                else if (s instanceof KeybindSetting k) {
                    k.setKey(se.getAsInt());
                    if (k.isModuleKey()) mod.setKey(se.getAsInt());
                }
                else if (s instanceof StringSetting st)    st.setValue(se.getAsString());
                else if (s instanceof MinMaxSetting mm && se.isJsonObject()) {
                    mm.setMinValue(se.getAsJsonObject().get("1").getAsDouble());
                    mm.setMaxValue(se.getAsJsonObject().get("2").getAsDouble());
                }
            }
        }
    }
}
