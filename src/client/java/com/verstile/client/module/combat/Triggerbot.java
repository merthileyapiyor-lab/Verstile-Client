package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public class Triggerbot extends Module {
    public final NumberSetting range = new NumberSetting("Range", 3.8, 2.0, 6.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Minimum Cooldown %", 99.0, 75.0, 100.0, 1.0);
    public final NumberSetting randomDelay = new NumberSetting("Random Delay MS", 20.0, 0.0, 200.0, 5.0);
    public final BooleanSetting ignoreCooldown = new BooleanSetting("Ignore Cooldown", false);
    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", true);
    public final BooleanSetting requireCrosshair = new BooleanSetting("Require Crosshair", true);
    public final BooleanSetting criticalOnly = new BooleanSetting("Critical Only", false);
    public final BooleanSetting disableWhileUsing = new BooleanSetting("Disable While Using Item", true);
    public final BooleanSetting leftClickOnly = new BooleanSetting("Only While Pressing Left Click", true);

    private Item heldItem;
    private long heldSince;
    private long nextAttackAt;

    public Triggerbot() {
        super("Triggerbot", "Attacks valid targets only after the held item's full attack recovery", Category.COMBAT, 0);
        addSetting(range);
        addSetting(cooldown);
        addSetting(randomDelay);
        addSetting(ignoreCooldown);
        addSetting(players);
        addSetting(mobs);
        addSetting(requireCrosshair);
        addSetting(criticalOnly);
        addSetting(disableWhileUsing);
        addSetting(leftClickOnly);
    }

    @Override
    public void onEnable() {
        heldItem = null;
        heldSince = System.currentTimeMillis();
        nextAttackAt = 0L;
    }

    @Override
    public void onDisable() {
        nextAttackAt = 0L;
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (leftClickOnly.getValue() && !client.options.keyAttack.isDown()) return;
        if (disableWhileUsing.getValue() && client.player.isUsingItem()) return;

        Item currentItem = client.player.getMainHandItem().getItem();
        long now = System.currentTimeMillis();
        if (currentItem != heldItem) {
            heldItem = currentItem;
            heldSince = now;
            nextAttackAt = 0L;
            return;
        }

        LivingEntity target = acquireTarget();
        if (target == null || !target.isAlive() || !client.player.hasLineOfSight(target)) {
            nextAttackAt = 0L;
            return;
        }
        if (client.player.distanceTo(target) > range.getValue()) return;
        if (criticalOnly.getValue() && !isCriticalReady()) return;

        float required = cooldown.getValue().floatValue() / 100.0f;
        // Vanilla derives this scale from the currently equipped item's attack-speed
        // attribute. Waiting after an item switch prevents carrying recovery from a
        // previous weapon into a faster/slower one.
        if (!ignoreCooldown.getValue()
                && (now - heldSince < 50L || client.player.getAttackStrengthScale(0.5f) + 0.0001f < required)) {
            nextAttackAt = 0L;
            return;
        }

        if (nextAttackAt == 0L) {
            int maximum = randomDelay.getValue().intValue();
            nextAttackAt = now + (maximum <= 0 ? 0 : ThreadLocalRandom.current().nextInt(maximum + 1));
        }
        if (now < nextAttackAt) return;

        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
        nextAttackAt = 0L;
    }

    private LivingEntity acquireTarget() {
        if (requireCrosshair.getValue()) {
            if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return null;
            return client.crosshairPickEntity instanceof LivingEntity living && allowed(living) ? living : null;
        }

        Vec3 eyes = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0f);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maximum = range.getValue();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !allowed(living)) continue;
            double distance = client.player.distanceTo(living);
            if (distance > maximum) continue;
            Vec3 direction = living.getEyePosition().subtract(eyes).normalize();
            double anglePenalty = 1.0 - look.dot(direction);
            if (anglePenalty > 0.08) continue;
            double score = distance + anglePenalty * 20.0;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean allowed(LivingEntity entity) {
        if (entity == client.player || !entity.isAlive()) return false;
        if (entity instanceof Player player) return players.getValue() && TargetUtil.canTarget(player);
        return mobs.getValue();
    }

    private boolean isCriticalReady() {
        return !client.player.onGround() && client.player.fallDistance > 0.0f
                && !client.player.isInWater() && !client.player.isPassenger();
    }
}
