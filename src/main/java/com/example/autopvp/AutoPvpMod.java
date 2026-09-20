package com.example.autopvp;

import com.example.autopvp.config.AutoPvpConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPvpMod implements ModInitializer {
    public static final String MOD_ID = "autopvp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static AutoPvpConfig config;

    @Override
    public void onInitialize() {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            System.setProperty("java.awt.headless", "false");
        }
        LOGGER.info("[Verstile] Initializing...");
        config = AutoPvpConfig.load();
        LOGGER.info("[Verstile] Configuration loaded successfully.");
    }
}
