package com.verstile.client.setting;

import java.util.List;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Default mode is not in modes");
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    public void cycle() {
        int index = modes.indexOf(getValue());
        setValue(modes.get((index + 1) % modes.size()));
    }
}
