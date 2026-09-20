package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.world.phys.Vec3;

public class JumpReset extends Module {
    private Vec3 lastVelocity = Vec3.ZERO;
    private int lastHurtTime;
    private int jumpWindow;
    
    public JumpReset() {
        super("JumpReset", "Automatically times jumps to minimize received knockback", Category.MOVEMENT, 0);
    }

    @Override
    public void onTick() {
        if (client.player == null) return;
        
        // Handle jump reset when player takes damage / knockback
        if (client.player.hurtTime > lastHurtTime) jumpWindow = 8;
        lastHurtTime = client.player.hurtTime;
        
        Vec3 velocity = client.player.getDeltaMovement();
        boolean knockback = client.player.onGround()
                && velocity.horizontalDistanceSqr() > lastVelocity.horizontalDistanceSqr() + 0.02;
        if ((knockback || jumpWindow > 0) && client.player.onGround()
                && !client.player.isInWater() && !client.player.isCrouching()) {
            client.player.jumpFromGround();
            jumpWindow = 0;
        }
        if (jumpWindow > 0) jumpWindow--;
        lastVelocity = velocity;
    }

    @Override public void onEnable() { lastVelocity = Vec3.ZERO; lastHurtTime = 0; jumpWindow = 0; }
}
