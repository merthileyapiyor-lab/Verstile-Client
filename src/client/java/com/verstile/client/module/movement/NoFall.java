package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Prevents fall damage by spoofing ground status to the server", Category.MOVEMENT, 0);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.getConnection() == null) return;
        if (client.player.fallDistance > 2.0f) {
            client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, client.player.horizontalCollision));
            client.player.resetFallDistance();
        }
    }
}
