package com.verstile.bootstrap;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Applies desktop mode before Minecraft or another mod can initialize AWT. */
public final class DesktopBootstrap implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            System.setProperty("java.awt.headless", "false");
        }
    }
}
