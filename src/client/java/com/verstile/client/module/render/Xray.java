package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Xray extends Module {
    public final BooleanSetting nightVision = new BooleanSetting("Night Vision", true);
    public final NumberSetting gamma = new NumberSetting("Gamma", 1.0, 0.0, 1.0, 0.05);
    private double previousGamma = 0.5;
    private boolean suppliedNightVision;

    public Xray() {
        super("Fullbright", "Maximum brightness in caves and dark areas", Category.RENDER, 0);
        addSetting(nightVision);
        addSetting(gamma);
    }

    @Override
    public void onEnable() {
        if (client.options != null) previousGamma = client.options.gamma().get();
    }

    @Override
    public void onTick() {
        if (client.options != null) client.options.gamma().set(gamma.getValue());
        if (client.player == null || !nightVision.getValue()) return;
        var current = client.player.getEffect(MobEffects.NIGHT_VISION);
        if (current == null || current.getDuration() < 220) {
            client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
            suppliedNightVision = true;
        }
    }

    @Override
    public void onDisable() {
        if (client.options != null) client.options.gamma().set(previousGamma);
        if (client.player != null && suppliedNightVision) client.player.removeEffect(MobEffects.NIGHT_VISION);
        suppliedNightVision = false;
    }
}
