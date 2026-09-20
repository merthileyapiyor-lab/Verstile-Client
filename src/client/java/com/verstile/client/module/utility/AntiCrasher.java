package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;

/**
 * AntiCrasher — Özel Koruma Modülü.
 * Yalnızca kullanıcı adı "avcibora" olan oyuncu tarafından aktifleştirilebilir.
 * Sunuculardan veya diğer oyunculardan gelen zararlı/crash paketlerini engeller.
 */
public class AntiCrasher extends Module {
    public final BooleanSetting protectInventory = new BooleanSetting("Protect Inventory", true);
    public final BooleanSetting ignoreInvalidItems = new BooleanSetting("Ignore Invalid Items", true);

    public AntiCrasher() {
        super("AntiCrasher", "Özel Anti-Crash Koruması (Sadece 'avcibora' kullanabilir)", Category.UTILITY, 0);
        addSetting(protectInventory);
        addSetting(ignoreInvalidItems);
    }

    @Override
    public void onEnable() {
        if (client.player == null) return;
        String username = client.getUser() != null ? client.getUser().getName() : "";
        if (!"avcibora".equalsIgnoreCase(username)) {
            setEnabled(false);
            if (client.player != null) {
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c[AntiCrasher] §fBu modülü sadece §eavcibora§f kullanabilir!"), false);
            }
        } else {
            if (client.player != null) {
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[AntiCrasher] §favcibora özel koruması aktif!"), false);
            }
        }
    }

    @Override
    public void onTick() {
        if (client.player == null) return;
        String username = client.getUser() != null ? client.getUser().getName() : "";
        if (!"avcibora".equalsIgnoreCase(username)) {
            setEnabled(false);
        }
    }
}
