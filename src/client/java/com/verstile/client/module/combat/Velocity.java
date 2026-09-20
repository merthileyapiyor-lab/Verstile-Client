package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;

/**
 * Velocity — knockback reduction.
 * The actual packet interception is handled by VerstileLivingEntityMixin.
 * Settings here are read by the mixin.
 */
public class Velocity extends Module {
    public final NumberSetting horizontal = new NumberSetting("Horizontal %", 0.0, 0.0, 100.0, 1.0);
    public final NumberSetting vertical   = new NumberSetting("Vertical %",   0.0, 0.0, 100.0, 1.0);
    public final BooleanSetting groundVelocity = new BooleanSetting("Work On Ground", true);
    public final BooleanSetting cancelExplosions = new BooleanSetting("Cancel Explosions", true);

    public Velocity() {
        super("Velocity", "Reduces or eliminates knockback taken on ground or in air", Category.COMBAT, 0);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(groundVelocity);
        addSetting(cancelExplosions);
    }

    // onTick not needed — mixin handles it at the entity-knockback call site
}
