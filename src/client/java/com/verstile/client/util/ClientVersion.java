package com.verstile.client.util;

import net.fabricmc.loader.api.FabricLoader;

public final class ClientVersion {
    private ClientVersion() {}

    public static String current() {
        return FabricLoader.getInstance().getModContainer("autopvp")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
