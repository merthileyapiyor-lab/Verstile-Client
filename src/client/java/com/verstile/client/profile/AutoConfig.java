package com.verstile.client.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.movement.MLG;
import com.verstile.client.gui.GuiMode;
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

/** Automatic persistent state for the last enabled modules and their settings. */
public final class AutoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("verstile").resolve("client.json");

    private AutoConfig() {}

    public static synchronized void load() {
        if (!Files.isRegularFile(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("guiMode")) GuiMode.select(root.get("guiMode").getAsString());
            if (!root.has("modules") || !root.get("modules").isJsonObject()) return;
            JsonObject modules = root.getAsJsonObject("modules");
            for (Module module : ModuleManager.getInstance().getModules()) {
                JsonElement element = modules.get(module.getName());
                if (element == null && module instanceof MLG) {
                    element = modules.has("Clutch / MLG") ? modules.get("Clutch / MLG")
                            : modules.has("MLG") ? modules.get("MLG") : modules.get("Clutch");
                }
                if (element == null || !element.isJsonObject()) continue;
                JsonObject moduleJson = element.getAsJsonObject();
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
                    module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                }
            }
        } catch (Exception error) {
            System.err.println("[Verstile] Could not load client config: " + error.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            root.addProperty("client", "Verstile");
            root.addProperty("guiMode", GuiMode.selected().name());
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
            try (Writer writer = Files.newBufferedWriter(FILE, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException error) {
            System.err.println("[Verstile] Could not save client config: " + error.getMessage());
        }
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
}
