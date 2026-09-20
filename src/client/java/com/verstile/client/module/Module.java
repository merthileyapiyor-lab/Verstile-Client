package com.verstile.client.module;

import com.verstile.client.setting.Setting;
import com.verstile.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final Minecraft client = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private volatile int keyBind;
    private volatile boolean enabled;
    private final ModeSetting keybindMode = new ModeSetting("Keybind Mode", "TOGGLE", "TOGGLE", "HOLD");

    public Module(String name, String description, Category category, int defaultKeyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = defaultKeyBind;
        this.enabled = false;
        this.settings.add(keybindMode);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isHoldBind() {
        return keybindMode.is("HOLD");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender() {}
}
