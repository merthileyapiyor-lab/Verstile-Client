package com.verstile.client.mixin;

import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.combat.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class VelocityLivingEntityMixin {

    // Intercept knockback application on the local player only
    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void velocity_knockbackCancel(double strength, double x, double z, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity self = (LivingEntity)(Object)this;
        if (mc.player == null || !self.equals(mc.player)) return;

        Velocity vel = getVelocityModule();
        if (vel == null || !vel.isEnabled()) return;

        if (!vel.groundVelocity.getValue() && mc.player.onGround()) return;

        double hPct = vel.horizontal.getValue() / 100.0;
        double vPct = vel.vertical.getValue() / 100.0;
        if (hPct <= 0.0 && vPct <= 0.0) {
            ci.cancel();
            return;
        }

        ci.cancel();
        double scaledStrength = strength * hPct;
        if (scaledStrength > 0) {
            var currentVel = mc.player.getDeltaMovement();
            double nx = (currentVel.x / 2.0 - x * scaledStrength) * hPct;
            double ny = (mc.player.onGround() ? Math.min(0.4, currentVel.y / 2.0 + scaledStrength) : currentVel.y) * vPct;
            double nz = (currentVel.z / 2.0 - z * scaledStrength) * hPct;
            mc.player.setDeltaMovement(nx, ny, nz);
        }
    }

    private static Velocity getVelocityModule() {
        for (var mod : ModuleManager.getInstance().getModules()) {
            if (mod instanceof Velocity v) return v;
        }
        return null;
    }
}
