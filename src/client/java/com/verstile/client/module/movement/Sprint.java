package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.client.Minecraft;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Automatically keeps your character sprinting", Category.MOVEMENT, 0);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (client.player.zza > 0 && !client.player.horizontalCollision && !client.player.isCrouching()) {
            client.player.setSprinting(true);
        }
    }
}
