package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {
    // Max plausible extra speed without flagging most anti-cheats
    // Real safe cap is ~0.35 blocks/tick extra over sprint baseline (~0.286)
    public final NumberSetting speedBoost = new NumberSetting("Speed Boost", 0.08, 0.01, 0.40, 0.01);
    public final BooleanSetting jumpBoost  = new BooleanSetting("Jump Boost", true);
    public final BooleanSetting groundOnly = new BooleanSetting("Ground Only", true);

    private boolean wasOnGround = true;
    private int jumpTick = 0;

    public Speed() {
        super("Speed", "Boosts movement velocity within server-safe thresholds", Category.MOVEMENT, 0);
        addSetting(speedBoost);
        addSetting(jumpBoost);
        addSetting(groundOnly);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        var player = client.player;

        boolean moving = player.zza != 0 || player.xxa != 0;
        if (!moving) return;

        boolean onGround = player.onGround();
        if (groundOnly.getValue() && !onGround) {
            wasOnGround = false;
            return;
        }

        Vec3 vel = player.getDeltaMovement();

        // Jump-boost mode: apply burst on the tick the player jumps, then sustained boost
        if (jumpBoost.getValue() && !wasOnGround && onGround) {
            // Just landed — apply horizontal burst so AC sees speed spikes only on jump frames
            jumpTick = 0;
        }

        if (onGround || jumpTick < 4) {
            double boost = speedBoost.getValue();
            // Only boost horizontal; never touch Y to avoid fly detection
            double nx = vel.x + Math.sin(Math.toRadians(player.getYRot() + 180)) * boost;
            double nz = vel.z - Math.cos(Math.toRadians(player.getYRot() + 180)) * boost;
            // Clamp to avoid obvious cap bypass (most ACs flag > 0.7 blocks/tick)
            double maxH = 0.62;
            double hSpeed = Math.sqrt(nx * nx + nz * nz);
            if (hSpeed > maxH) {
                double scale = maxH / hSpeed;
                nx *= scale;
                nz *= scale;
            }
            player.setDeltaMovement(nx, vel.y, nz);
            jumpTick++;
        }

        wasOnGround = onGround;
    }
}
