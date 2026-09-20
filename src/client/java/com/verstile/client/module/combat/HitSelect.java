package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class HitSelect extends Module {
    public final NumberSetting cooldown = new NumberSetting("Minimum Cooldown %", 95.0, 10.0, 100.0, 5.0);
    public final NumberSetting range = new NumberSetting("Range", 3.2, 2.0, 6.0, 0.1);
    public final BooleanSetting criticalOnly = new BooleanSetting("Critical Hits Only", false);
    public final BooleanSetting requireAttackHeld = new BooleanSetting("Require Attack Held", true);

    public HitSelect() {
        super("HitSelect", "Times server-side attacks for full-strength critical or spaced hits", Category.COMBAT, 0);
        addSetting(cooldown); addSetting(range); addSetting(criticalOnly); addSetting(requireAttackHeld);
    }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (requireAttackHeld.getValue() && !client.options.keyAttack.isDown()) return;
        if (client.player.getAttackStrengthScale(0.5f) * 100.0f < cooldown.getValue()) return;
        if (criticalOnly.getValue() && (client.player.onGround() || client.player.getDeltaMovement().y >= 0.0)) return;
        LivingEntity target = client.crosshairPickEntity instanceof LivingEntity living && allowed(living) ? living :
                client.level.getEntitiesOfClass(LivingEntity.class, client.player.getBoundingBox().inflate(range.getValue()),
                        entity -> allowed(entity) && client.player.hasLineOfSight(entity))
                        .stream().filter(entity -> {
                            var direction = entity.getBoundingBox().getCenter().subtract(client.player.getEyePosition()).normalize();
                            return client.player.getViewVector(1.0f).dot(direction) > 0.985;
                        }).min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
        if (target == null || !target.isAlive() || client.player.distanceTo(target) > range.getValue()) return;
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean allowed(LivingEntity entity) {
        return entity != client.player && entity.isAlive()
                && (!(entity instanceof Player player) || TargetUtil.canTarget(player));
    }
}
