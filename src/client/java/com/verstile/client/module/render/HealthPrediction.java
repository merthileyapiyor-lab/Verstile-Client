package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.world.entity.LivingEntity;

public class HealthPrediction extends Module {
    public final BooleanSetting includeAbsorption = new BooleanSetting("Include Absorption", true);
    public final NumberSetting expectedDamage = new NumberSetting("Expected Damage", 0.0, 0.0, 20.0, 0.5);
    public HealthPrediction() {
        super("HealthPrediction", "Calculates and displays target enemy health values", Category.RENDER, 0);
        addSetting(includeAbsorption);
        addSetting(expectedDamage);
    }

    public float predictedHealth(LivingEntity living) {
        float health = living.getHealth() + (includeAbsorption.getValue() ? living.getAbsorptionAmount() : 0.0f);
        return Math.max(0.0f, health - expectedDamage.getValue().floatValue());
    }
}
