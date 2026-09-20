package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;

public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", "Deflects incoming Ghast or Blaze fireballs automatically", Category.UTILITY, 0);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        Projectile nearest = null;
        double best = 5.0;
        for (Projectile fireball : client.level.getEntitiesOfClass(Projectile.class,
                client.player.getBoundingBox().inflate(best))) {
            double distance = client.player.distanceTo(fireball);
            String type = fireball.getType().toString().toLowerCase();
            if (type.contains("fireball") && distance < best && fireball.isAlive()) {
                best = distance;
                nearest = fireball;
            }
        }
        if (nearest != null) {
            client.gameMode.attack(client.player, nearest);
            client.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
