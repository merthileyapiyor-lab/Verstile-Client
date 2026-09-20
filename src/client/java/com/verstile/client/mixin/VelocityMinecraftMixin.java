package com.verstile.client.mixin;

import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.utility.FastPlace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class VelocityMinecraftMixin {
    @Shadow private int rightClickDelay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void velocity_fastPlace(CallbackInfo ci) {
        FastPlace fastPlace = (FastPlace) ModuleManager.getInstance().getModuleByName("FastPlace");
        if (fastPlace != null && fastPlace.isEnabled()) {
            rightClickDelay = Math.min(rightClickDelay, fastPlace.delay.getValue().intValue());
        }
    }
}
