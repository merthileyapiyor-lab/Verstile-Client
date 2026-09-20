package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class SilentAura extends Module {
    public final ModeSetting rotation = new ModeSetting("Rotation", "SILENT", "SILENT", "BLATANT");
    public final NumberSetting range = new NumberSetting("Range", 4.0, 2.5, 6.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Cooldown", 90.0, 10.0, 100.0, 5.0);
    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    public final BooleanSetting requireSight = new BooleanSetting("Require Sight", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);

    public SilentAura() {
        super("SilentAura", "Attacks the nearest valid target with silent or visible rotations", Category.COMBAT, 0);
        addSetting(rotation);
        addSetting(range);
        addSetting(cooldown);
        addSetting(players);
        addSetting(mobs);
        addSetting(requireSight);
        addSetting(swing);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return;
        if (client.player.getAttackStrengthScale(0.0f) * 100.0f < cooldown.getValue()) return;
        LivingEntity target = getTarget();
        if (target == null) return;

        float[] aim = aimAt(target);
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        if (rotation.is("SILENT")) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Rot(aim[0], aim[1],
                    client.player.onGround(), client.player.horizontalCollision));
        } else {
            client.player.setYRot(aim[0]);
            client.player.setXRot(aim[1]);
        }
        client.gameMode.attack(client.player, target);
        if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);
        if (rotation.is("SILENT")) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch,
                    client.player.onGround(), client.player.horizontalCollision));
        }
    }

    private LivingEntity getTarget() {
        LivingEntity nearest = null;
        double best = range.getValue();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == client.player || !living.isAlive()) continue;
            if (living instanceof Player player) {
                if (!players.getValue() || !TargetUtil.canTarget(player)) continue;
            } else if (!mobs.getValue()) continue;
            if (requireSight.getValue() && !client.player.hasLineOfSight(living)) continue;
            double distance = client.player.distanceTo(living);
            if (distance < best) { best = distance; nearest = living; }
        }
        return nearest;
    }

    private float[] aimAt(LivingEntity target) {
        Vec3 eyes = client.player.getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        double dx = center.x - eyes.x, dy = center.y - eyes.y, dz = center.z - eyes.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }
}
