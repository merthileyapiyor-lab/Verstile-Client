package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

/** Immediate directional lunge while using a spear item. */
public final class SpearLunge extends Module {
    public final NumberSetting speed = new NumberSetting("Lunge Speed", 2.4, 0.3, 8.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Cooldown Ticks", 8.0, 1.0, 40.0, 1.0);
    public final BooleanSetting requireUse = new BooleanSetting("Require Right Click", true);
    public final BooleanSetting preserveVertical = new BooleanSetting("Preserve Vertical Velocity", false);
    private boolean previousUse;
    private int timer;

    public SpearLunge() {
        super("SpearLunge", "Instantly lunges in the look direction while holding a spear", Category.UTILITY, 0);
        addSetting(speed); addSetting(cooldown); addSetting(requireUse); addSetting(preserveVertical);
    }

    @Override public void onTick() {
        if (client.player == null) return;
        if (timer > 0) timer--;
        boolean use = client.options.keyUse.isDown();
        boolean edge = use && !previousUse;
        previousUse = use;
        String id = BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem()).getPath();
        if (!id.contains("spear") || timer > 0 || (requireUse.getValue() && !edge)) return;
        Vec3 look = client.player.getViewVector(1.0f).normalize().scale(speed.getValue());
        double y = preserveVertical.getValue() ? client.player.getDeltaMovement().y : look.y;
        client.player.setDeltaMovement(look.x, y, look.z);
        timer = cooldown.getValue().intValue();
    }

    @Override public void onEnable() { previousUse = false; timer = 0; }
    @Override public void onDisable() { previousUse = false; timer = 0; }
}
