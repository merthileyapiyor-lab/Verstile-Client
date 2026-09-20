package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Mace attack with mutually-exclusive velocity simulation modes. */
public class AutoMace extends Module {
    public final ModeSetting velocityMode = new ModeSetting("Velocity Mode", "MULTIPLY", "OFF", "MULTIPLY", "ONE_SHOT");
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "CROSSHAIR", "CROSSHAIR", "NEAREST");
    public final ModeSetting rotation = new ModeSetting("Rotation", "NORMAL", "NORMAL", "SILENT");
    public final NumberSetting range = new NumberSetting("Range", 3.5, 2.0, 6.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Cooldown", 90.0, 10.0, 100.0, 5.0);
    public final NumberSetting velocityMultiplier = new NumberSetting("Velocity Multiplier", 8.0, 2.0, 30.0, 1.0);
    public final NumberSetting oneShotHeight = new NumberSetting("One Shot Height", 128.0, 32.0, 512.0, 16.0);
    public final BooleanSetting requireLeftClick = new BooleanSetting("Require Left Click", true);
    // The explicit names also migrate old profiles whose generic "Mobs" value
    // was saved as false before mob targeting was implemented correctly.
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", true);
    public final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);
    private boolean previousAttack;
    private LivingEntity pendingTarget;
    private int pendingOldSlot = -1;
    private int switchDelay;
    private boolean queuedClick;

    public AutoMace() {
        super("AutoMace", "Ground-capable mace attack with multiply or one-shot velocity", Category.UTILITY, 0);
        addSetting(velocityMode); addSetting(targetMode); addSetting(rotation); addSetting(range); addSetting(cooldown);
        addSetting(velocityMultiplier); addSetting(oneShotHeight); addSetting(requireLeftClick);
        addSetting(targetPlayers); addSetting(targetMobs); addSetting(autoSwitch); addSetting(restoreSlot); addSetting(swing);
    }

    @Override public void onEnable() { clearPending(); previousAttack = false; }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return;
        boolean attackDown = client.options.keyAttack.isDown();
        boolean attackEdge = attackDown && !previousAttack;
        previousAttack = attackDown;
        if (attackEdge) queuedClick = true;

        // Hotbar selection is acknowledged separately by multiplayer servers.
        // Finish a queued hit one tick after switching instead of attacking with
        // the item that the server still considers selected.
        if (pendingTarget != null) {
            if (switchDelay-- > 0) return;
            if (!allowed(pendingTarget) || client.player.distanceTo(pendingTarget) > range.getValue() + 0.5
                    || !client.player.getMainHandItem().is(Items.MACE)) {
                restorePendingSlot(); clearPending(); return;
            }
            attackTarget(pendingTarget);
            restorePendingSlot(); clearPending();
            return;
        }
        if (requireLeftClick.getValue() && !queuedClick) return;
        if (client.player.getAttackStrengthScale(0.0f) * 100.0f < cooldown.getValue()) return;
        LivingEntity target = findTarget();
        if (target == null) return;
        queuedClick = false;
        int maceSlot = findMace();
        if (maceSlot < 0) return;
        int oldSlot = client.player.getInventory().getSelectedSlot();
        if (autoSwitch.getValue() && oldSlot != maceSlot) {
            switchSlot(maceSlot);
            pendingTarget = target;
            pendingOldSlot = oldSlot;
            switchDelay = 1;
            return;
        }
        if (!client.player.getMainHandItem().is(Items.MACE)) return;

        attackTarget(target);
    }

    private void attackTarget(LivingEntity target) {
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        float[] aim = aimAt(target);
        if (rotation.is("SILENT")) sendRotation(aim[0], aim[1]);
        else { client.player.setYRot(aim[0]); client.player.setXRot(aim[1]); }

        // Send the real attack first. Large synthetic movement packets sent
        // before it made many servers discard the hit as out-of-range.
        client.gameMode.attack(client.player, target);
        if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);

        if (!velocityMode.is("OFF")) {
            double simulatedHeight = velocityMode.is("ONE_SHOT") ? oneShotHeight.getValue()
                    : Math.max(8.0, Math.abs(client.player.getDeltaMovement().y)
                    * velocityMultiplier.getValue() * 12.0);

            // Send multiple intermediate height packets so AntiCheat/Server accepts fall velocity calculation
            // even if player is standing still on the ground
            double currentY = client.player.getY();
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    client.player.getX(), currentY + simulatedHeight, client.player.getZ(),
                    false, false));
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    client.player.getX(), currentY + (simulatedHeight / 2.0), client.player.getZ(),
                    false, false));
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    client.player.getX(), currentY, client.player.getZ(),
                    false, false));

            client.player.fallDistance = (float) Math.max(client.player.fallDistance, simulatedHeight);
        }

        if (rotation.is("SILENT")) sendRotation(oldYaw, oldPitch);
    }

    private LivingEntity findTarget() {
        if (targetMode.is("CROSSHAIR")) {
            if (client.crosshairPickEntity instanceof LivingEntity living && allowed(living)
                    && client.player.distanceTo(living) <= range.getValue()) return living;
            // Crosshair hit detection can briefly be null while sprinting or
            // swapping. Use a narrow aim cone as a stable fallback.
            Vec3 eyes = client.player.getEyePosition();
            Vec3 look = client.player.getViewVector(1.0f);
            return client.level.getEntitiesOfClass(LivingEntity.class,
                            client.player.getBoundingBox().inflate(range.getValue()), this::allowed).stream()
                    .filter(client.player::hasLineOfSight)
                    .filter(entity -> {
                        Vec3 direction = entity.getBoundingBox().getCenter().subtract(eyes).normalize();
                        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(direction))))) <= 12.0;
                    })
                    .min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
        }
        return client.level.getEntitiesOfClass(LivingEntity.class, client.player.getBoundingBox().inflate(range.getValue()),
                entity -> allowed(entity) && client.player.hasLineOfSight(entity)).stream()
                .min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
    }

    private boolean allowed(LivingEntity entity) {
        if (entity == client.player || !entity.isAlive() || entity.isSpectator() || entity instanceof ArmorStand) return false;
        return entity instanceof Player player ? targetPlayers.getValue() && TargetUtil.canTarget(player) : targetMobs.getValue();
    }

    private int findMace() {
        if (client.player.getMainHandItem().is(Items.MACE)) return client.player.getInventory().getSelectedSlot();
        if (!autoSwitch.getValue()) return -1;
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(Items.MACE)) return slot;
        return -1;
    }

    private void switchSlot(int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void sendRotation(float yaw, float pitch) {
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                client.player.onGround(), client.player.horizontalCollision));
    }

    private void restorePendingSlot() {
        if (restoreSlot.getValue() && pendingOldSlot >= 0
                && pendingOldSlot != client.player.getInventory().getSelectedSlot()) switchSlot(pendingOldSlot);
    }

    private void clearPending() {
        pendingTarget = null;
        pendingOldSlot = -1;
        switchDelay = 0;
        queuedClick = false;
    }

    private float[] aimAt(LivingEntity target) {
        Vec3 eyes = client.player.getEyePosition(), center = target.getBoundingBox().getCenter();
        double dx = center.x - eyes.x, dy = center.y - eyes.y, dz = center.z - eyes.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    @Override public void onDisable() {
        if (client.player != null && client.getConnection() != null) restorePendingSlot();
        clearPending();
        previousAttack = false;
    }
}
