package com.verstile.client.mixin;

import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.render.HealthPrediction;
import com.verstile.client.module.render.Nametags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class VelocityEntityRendererMixin<T extends Entity> {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void velocity_shouldShowName(T entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        Nametags nametags = (Nametags) ModuleManager.getInstance().getModuleByName("Nametags");
        if (nametags != null && nametags.isEnabled() && entity instanceof LivingEntity) {
            Minecraft mc = Minecraft.getInstance();
            boolean allowed = (entity instanceof Player && nametags.players.getValue())
                    || (!(entity instanceof Player) && nametags.mobs.getValue());
            if (entity != mc.player && allowed && distance <= nametags.range.getValue() * nametags.range.getValue()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void velocity_getNameTag(T entity, CallbackInfoReturnable<Component> cir) {
        Nametags nametags = (Nametags) ModuleManager.getInstance().getModuleByName("Nametags");
        HealthPrediction hp = (HealthPrediction) ModuleManager.getInstance().getModuleByName("HealthPrediction");
        boolean showHealth = (nametags != null && nametags.isEnabled() && nametags.showHealth.getValue())
                || (hp != null && hp.isEnabled());
        if (showHealth && entity instanceof LivingEntity living && cir.getReturnValue() != null) {
            float health = hp != null && hp.isEnabled() ? hp.predictedHealth(living) : living.getHealth();
            cir.setReturnValue(Component.literal(String.format("§a[%.1f HP] §r", health)).append(cir.getReturnValue()));
        }
        if (nametags != null && nametags.isEnabled() && nametags.showDistance.getValue()
                && entity instanceof LivingEntity && Minecraft.getInstance().player != null && cir.getReturnValue() != null) {
            double meters = Minecraft.getInstance().player.distanceTo(entity);
            cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(String.format(" §7[%.1fm]", meters))));
        }
    }
}
