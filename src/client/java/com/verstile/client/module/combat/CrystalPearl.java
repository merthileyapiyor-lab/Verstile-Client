package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Throws a pearl toward the predicted landing point of a crystal-launched target. */
public final class CrystalPearl extends Module {
    public final NumberSetting detectRange = new NumberSetting("Detect Range", 14.0, 5.0, 32.0, 1.0);
    public final NumberSetting minLaunchSpeed = new NumberSetting("Min Launch Speed", 0.35, 0.1, 2.0, 0.05);
    public final NumberSetting predictionTicks = new NumberSetting("Prediction Ticks", 60.0, 10.0, 120.0, 5.0);
    public final NumberSetting crystalMemory = new NumberSetting("Crystal Memory", 12.0, 2.0, 30.0, 1.0);
    public final NumberSetting cooldown = new NumberSetting("Cooldown Ticks", 30.0, 5.0, 100.0, 1.0);
    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", true);
    public final BooleanSetting silent = new BooleanSetting("Silent Rotation", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);

    private final Map<Integer, Integer> previousHurt = new HashMap<>();
    private Vec3 recentCrystal;
    private int crystalTicks;
    private int pearlCooldown;

    public CrystalPearl() {
        super("CrystalPearl", "Pearls to the predicted landing point of crystal-launched targets", Category.COMBAT, 0);
        addSetting(detectRange); addSetting(minLaunchSpeed); addSetting(predictionTicks); addSetting(crystalMemory);
        addSetting(cooldown); addSetting(players); addSetting(mobs); addSetting(silent); addSetting(restoreSlot);
    }

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return;
        if (pearlCooldown > 0) pearlCooldown--;
        rememberNearbyCrystal();
        if (crystalTicks > 0) crystalTicks--; else recentCrystal = null;

        double range = detectRange.getValue();
        AABB area = client.player.getBoundingBox().inflate(range);
        for (LivingEntity target : client.level.getEntitiesOfClass(LivingEntity.class, area, this::allowed)) {
            int oldHurt = previousHurt.getOrDefault(target.getId(), 0);
            previousHurt.put(target.getId(), target.hurtTime);
            boolean freshlyHit = target.hurtTime > oldHurt;
            boolean launched = target.getDeltaMovement().y >= minLaunchSpeed.getValue();
            boolean crystalRelated = recentCrystal != null && target.position().distanceTo(recentCrystal) <= 8.0;
            if (pearlCooldown == 0 && freshlyHit && launched && crystalRelated) {
                Vec3 landing = predictLanding(target);
                if (throwPearl(landing)) {
                    pearlCooldown = cooldown.getValue().intValue();
                    return;
                }
            }
        }
    }

    private void rememberNearbyCrystal() {
        EndCrystal closest = client.level.getEntitiesOfClass(EndCrystal.class,
                        client.player.getBoundingBox().inflate(detectRange.getValue()), EndCrystal::isAlive)
                .stream().min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
        if (closest != null) {
            recentCrystal = closest.position();
            crystalTicks = crystalMemory.getValue().intValue();
        }
    }

    private Vec3 predictLanding(LivingEntity target) {
        Vec3 pos = target.position();
        Vec3 velocity = target.getDeltaMovement();
        for (int tick = 0; tick < predictionTicks.getValue().intValue(); tick++) {
            pos = pos.add(velocity);
            if (velocity.y <= 0.0) {
                BlockPos feet = BlockPos.containing(pos.x, pos.y - 0.12, pos.z);
                if (!client.level.getBlockState(feet).isAir()) return new Vec3(pos.x, feet.getY() + 1.0, pos.z);
            }
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
        }
        return pos;
    }

    private boolean throwPearl(Vec3 destination) {
        int pearlSlot = -1;
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) { pearlSlot = slot; break; }
        if (pearlSlot < 0) return false;
        int oldSlot = client.player.getInventory().getSelectedSlot();
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        float[] aim = ballisticAim(client.player.getEyePosition(), destination);
        client.player.getInventory().setSelectedSlot(pearlSlot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(pearlSlot));
        if (silent.getValue()) sendRotation(aim[0], aim[1]);
        else { client.player.setYRot(aim[0]); client.player.setXRot(aim[1]); }
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        if (silent.getValue()) sendRotation(oldYaw, oldPitch);
        if (restoreSlot.getValue()) {
            client.player.getInventory().setSelectedSlot(oldSlot);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(oldSlot));
        }
        return true;
    }

    private float[] ballisticAim(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double speed = 1.5, gravity = 0.03, speed2 = speed * speed;
        double root = speed2 * speed2 - gravity * (gravity * horizontal * horizontal + 2.0 * dy * speed2);
        double pitch = root >= 0.0 && horizontal > 0.001
                ? -Math.toDegrees(Math.atan((speed2 - Math.sqrt(root)) / (gravity * horizontal)))
                : -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f, (float) pitch};
    }

    private void sendRotation(float yaw, float pitch) {
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                client.player.onGround(), client.player.horizontalCollision));
    }

    private boolean allowed(LivingEntity entity) {
        if (entity == client.player || !entity.isAlive() || entity instanceof ArmorStand) return false;
        return entity instanceof Player player ? players.getValue() && TargetUtil.canTarget(player) : mobs.getValue();
    }

    private void reset() {
        previousHurt.clear(); recentCrystal = null; crystalTicks = 0; pearlCooldown = 0;
    }
}
