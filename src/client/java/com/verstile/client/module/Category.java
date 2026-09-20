package com.verstile.client.module;

public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "➜"),
    RENDER("Render", "◈"),
    HUD("HUD", "▣"),
    UTILITY("Utility", "⚙");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return icon + " " + name;
    }
}
