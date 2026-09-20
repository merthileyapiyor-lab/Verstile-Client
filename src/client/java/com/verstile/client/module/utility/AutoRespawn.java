package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

public class AutoRespawn extends Module {
    public AutoRespawn() {
        super("AutoRespawn", "Automatically skips death screen and respawns immediately", Category.UTILITY, 0);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.getConnection() == null) return;
        if (client.player.isDeadOrDying()) {
            client.player.respawn();
            client.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }
}
