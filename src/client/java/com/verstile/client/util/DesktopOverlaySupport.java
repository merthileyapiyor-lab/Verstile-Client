package com.verstile.client.util;

import java.awt.GraphicsEnvironment;

/** One safe capability check shared by every Swing/AWT overlay. */
public final class DesktopOverlaySupport {
    private DesktopOverlaySupport() {}

    public static boolean isAvailable() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return false;
        try {
            System.setProperty("java.awt.headless", "false");
            boolean available = !GraphicsEnvironment.isHeadless();
            if (!available) {
                System.err.println("[Verstile] Desktop overlay unavailable: AWT remains headless"
                        + " (property=" + System.getProperty("java.awt.headless") + ")");
            }
            return available;
        } catch (Throwable error) {
            System.err.println("[Verstile] Desktop overlay capability check failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
            return false;
        }
    }
}
