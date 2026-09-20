package com.example.autopvp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class AutoPvpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // Config Values
    public boolean crystalPvp = false;
    public boolean macePvp = false;
    public boolean swordPvp = false;
    public boolean axePvp = false;
    public boolean potPvp = false;
    public boolean spearPvp = false;

    // Advanced Mechanics
    public boolean critChaining = false;
    public boolean wTap = false;
    public boolean sTap = false;
    public boolean hitSelecting = false;
    public boolean totemCycling = true;
    public boolean blockHitting = false;

    // GUI/Keybind Options
    public int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public String theme = "Dark Mode";
    public boolean hudEnabled = true;
    public int hudX = 10;
    public int hudY = 10;

    public static AutoPvpConfig load() {
        configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                AutoPvpConfig config = GSON.fromJson(reader, AutoPvpConfig.class);
                if (config != null) return config;
            } catch (IOException e) {
                System.err.println("[AutoPvP] Failed to load configuration: " + e.getMessage());
            }
        }
        AutoPvpConfig defaultConfig = new AutoPvpConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    public void save() {
        if (configFile == null) {
            configFile = getConfigFile();
        }
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[AutoPvP] Failed to save configuration: " + e.getMessage());
        }
    }

    private static File getConfigFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve("autopvp.json").toFile();
    }
}
