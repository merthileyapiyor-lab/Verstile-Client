package com.verstile.client.mixin;

import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.utility.Blink;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class VelocityConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void velocity_blinkMovement(Packet<?> packet, CallbackInfo ci) {
        var module = ModuleManager.getInstance().getModuleByName("Blink");
        if (module instanceof Blink blink && blink.isEnabled() && blink.isChokingPackets()
                && packet instanceof ServerboundMovePlayerPacket) {
            blink.queue(packet);
            ci.cancel();
        }
    }
}
