package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.client.Minecraft;

public class WTap extends Module {
    private boolean wasAttacking;
    private int restartTicks;
    public WTap() {
        super("WTap", "Manages sprint resetting (W-tapping) to deal maximum knockback", Category.COMBAT, 0);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean attacking = client.options.keyAttack.isDown() && client.crosshairPickEntity != null;
        if (attacking && !wasAttacking && client.player.isSprinting()) {
            client.player.setSprinting(false);
            restartTicks = 2;
        }
        if (restartTicks > 0 && --restartTicks == 0 && client.player.zza > 0) {
            client.player.setSprinting(true);
        }
        wasAttacking = attacking;
    }
}
