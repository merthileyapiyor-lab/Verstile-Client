package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;

public class Panic extends Module {
    public Panic() {
        super("Panic", "Kill-switch button that disables all active features instantly", Category.UTILITY, 0);
    }

    @Override
    public void onEnable() {
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m != this && m.isEnabled()) {
                m.setEnabled(false);
            }
        }
        setEnabled(false);
    }
}
