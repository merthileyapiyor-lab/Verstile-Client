package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Fly extends Module {
    public final NumberSetting flySpeed = new NumberSetting("Fly Speed", 0.6, 0.1, 2.0, 0.1);

    public Fly() {
        super("Fly", "Overrides player physics to allow free flight across maps", Category.MOVEMENT, 0);
        addSetting(flySpeed);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        client.player.getAbilities().flying = true;
        Vec3 currentVel = client.player.getDeltaMovement();
        double mult = flySpeed.getValue();
        client.player.setDeltaMovement(currentVel.x * mult, 0.0, currentVel.z * mult);
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.getAbilities().flying = false;
    }
}
