package com.verstile.client.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("verstile").resolve("profiles");
    private static final Path LEGACY_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("vapeclient").resolve("profiles");
    private static volatile String activeProfile = "Auto";

    private ProfileManager() {}

    public static synchronized void save(String requestedName) throws IOException {
        String name = safeName(requestedName);
        Files.createDirectories(DIRECTORY);
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());
            moduleJson.addProperty("keyBind", module.getKeyBind());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                Object value = setting.getValue();
                if (value instanceof Boolean bool) settings.addProperty(setting.getName(), bool);
                else if (value instanceof Number number) settings.addProperty(setting.getName(), number);
                else settings.addProperty(setting.getName(), String.valueOf(value));
            }
            moduleJson.add("settings", settings);
            modules.add(module.getName(), moduleJson);
        }
        root.add("modules", modules);
        try (Writer writer = Files.newBufferedWriter(file(name), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, writer);
        }
        activeProfile = name;
    }

    public static synchronized void load(String requestedName) throws IOException {
        String name = safeName(requestedName);
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file(name))) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        if (root == null || !root.has("modules")) throw new IOException("Invalid profile");
        JsonObject modules = root.getAsJsonObject("modules");
        List<Runnable> stateChanges = new ArrayList<>();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonElement moduleElement = modules.get(module.getName());
            if (moduleElement == null && module instanceof com.verstile.client.module.movement.MLG) {
                moduleElement = modules.has("Clutch / MLG") ? modules.get("Clutch / MLG")
                        : modules.has("Clutch") ? modules.get("Clutch") : null;
            }
            if (moduleElement == null || !moduleElement.isJsonObject()) continue;
            JsonObject moduleJson = moduleElement.getAsJsonObject();
            if (moduleJson.has("keyBind")) module.setKeyBind(moduleJson.get("keyBind").getAsInt());
            if (moduleJson.has("settings") && moduleJson.get("settings").isJsonObject()) {
                JsonObject settings = moduleJson.getAsJsonObject("settings");
                for (Setting<?> setting : module.getSettings()) {
                    JsonElement value = settings.get(setting.getName());
                    if (value == null && module.getName().equalsIgnoreCase("CrystalSpam")
                            && setting.getName().equals("Speed CPS")) value = settings.get("CPS");
                    applySetting(setting, value);
                }
            }
            if (moduleJson.has("enabled")) {
                boolean enabled = moduleJson.get("enabled").getAsBoolean();
                stateChanges.add(() -> module.setEnabled(enabled));
            }
        }
        stateChanges.forEach(Runnable::run);
        activeProfile = name;
    }

    public static String activeProfile() {
        return activeProfile;
    }

    public static synchronized List<String> list() throws IOException {
        migrateLegacyProfiles();
        Files.createDirectories(DIRECTORY);
        try (var files = Files.list(DIRECTORY)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted(Comparator.naturalOrder()).toList();
        }
    }

    public static synchronized void delete(String requestedName) throws IOException {
        Files.deleteIfExists(file(safeName(requestedName)));
    }

    private static void applySetting(Setting<?> setting, JsonElement value) {
        if (value == null || value.isJsonNull()) return;
        try {
            if (setting instanceof BooleanSetting bool) bool.setValue(value.getAsBoolean());
            else if (setting instanceof NumberSetting number) number.setValueClamped(value.getAsDouble());
            else if (setting instanceof ModeSetting mode) {
                String modeValue = value.getAsString();
                if (!mode.getModes().contains(modeValue)
                        && mode.getName().equals("Rotation")
                        && (modeValue.equalsIgnoreCase("NONE") || modeValue.equalsIgnoreCase("BLATANT"))) {
                    modeValue = "NORMAL";
                }
                if (mode.getModes().contains(modeValue)) mode.setValue(modeValue);
            }
        } catch (RuntimeException ignored) {}
    }

    private static Path file(String name) throws IOException {
        migrateLegacyProfiles();
        Files.createDirectories(DIRECTORY);
        Path path = DIRECTORY.resolve(name + ".json").normalize();
        if (!path.getParent().equals(DIRECTORY.normalize())) throw new IOException("Invalid profile path");
        return path;
    }

    private static String safeName(String value) throws IOException {
        String name = value == null ? "" : value.trim();
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) throw new IOException("Use 1-32 letters, numbers, _ or -");
        return name;
    }

    private static void migrateLegacyProfiles() throws IOException {
        Files.createDirectories(DIRECTORY);
        if (!Files.isDirectory(LEGACY_DIRECTORY)) return;
        try (var files = Files.list(LEGACY_DIRECTORY)) {
            for (Path old : files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Path destination = DIRECTORY.resolve(old.getFileName().toString()).normalize();
                if (destination.getParent().equals(DIRECTORY.normalize()) && !Files.exists(destination)) {
                    Files.copy(old, destination);
                }
            }
        }
    }
}
