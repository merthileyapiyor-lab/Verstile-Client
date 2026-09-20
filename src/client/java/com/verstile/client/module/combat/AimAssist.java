package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class AimAssist extends Module {
    public final NumberSetting horizontalSpeed = new NumberSetting("Horizontal Speed", 150.0, 20.0, 540.0, 10.0);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", 110.0, 10.0, 360.0, 10.0);
    public final NumberSetting smoothing = new NumberSetting("Smoothness", 12.0, 2.0, 30.0, 1.0);
    public final NumberSetting acceleration = new NumberSetting("Acceleration", 900.0, 100.0, 2400.0, 50.0);
    public final NumberSetting deadZone = new NumberSetting("Dead Zone", 0.30, 0.0, 3.0, 0.05);
    public final NumberSetting range = new NumberSetting("Range", 4.5, 2.0, 8.0, 0.1);
    public final NumberSetting fov = new NumberSetting("FOV Angle", 70.0, 10.0, 180.0, 5.0);
    public final ModeSetting priority = new ModeSetting("Target Priority", "CROSSHAIR", "CROSSHAIR", "DISTANCE", "HEALTH");
    public final ModeSetting aimPoint = new ModeSetting("Aim Location", "HEAD", "HEAD", "CHEST", "EYES");
    public final BooleanSetting clickOnly = new BooleanSetting("Click Only", true);
    public final BooleanSetting verticalAim = new BooleanSetting("Vertical Aim", true);
    public final BooleanSetting targetPlayers = new BooleanSetting("Players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Mobs", true);

    private volatile LivingEntity target;
    private double yawVelocity;
    private double pitchVelocity;
    private long lastFrameNanos;

    public AimAssist() {
        super("AimAssist", "Frame-smoothed aim assistance with acceleration and a stable dead zone", Category.COMBAT, 0);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(smoothing);
        addSetting(acceleration);
        addSetting(deadZone);
        addSetting(range);
        addSetting(fov);
        addSetting(priority);
        addSetting(aimPoint);
        addSetting(clickOnly);
        addSetting(verticalAim);
        addSetting(targetPlayers);
        addSetting(targetMobs);
    }

    @Override
    public void onEnable() {
        resetMotion();
    }

    @Override
    public void onDisable() {
        target = null;
        resetMotion();
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null
                || (clickOnly.getValue() && !client.options.keyAttack.isDown())) {
            target = null;
            return;
        }
        target = selectTarget();
    }

    @Override
    public void onRender() {
        if (client.player == null || client.level == null) return;
        LivingEntity currentTarget = target;
        if (currentTarget == null || !currentTarget.isAlive()
                || client.player.distanceTo(currentTarget) > range.getValue()
                || (clickOnly.getValue() && !client.options.keyAttack.isDown())) {
            dampToStop(frameDelta());
            return;
        }

        double dt = frameDelta();
        if (dt <= 0.0) return;
        Vec3 eyes = client.player.getEyePosition(1.0f);
        Vec3 point = aimPoint.is("EYES") ? currentTarget.getEyePosition(1.0f)
                : currentTarget.position().add(0.0,
                currentTarget.getBbHeight() * (aimPoint.is("HEAD") ? 0.86 : 0.62), 0.0);
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        double yawDifference = Mth.wrapDegrees(desiredYaw - client.player.getYRot());
        double pitchDifference = desiredPitch - client.player.getXRot();
        if (Math.abs(yawDifference) > fov.getValue() * 0.5) {
            dampToStop(dt);
            return;
        }

        double zone = deadZone.getValue();
        if (Math.abs(yawDifference) <= zone) yawDifference = 0.0;
        if (Math.abs(pitchDifference) <= zone) pitchDifference = 0.0;
        double response = Math.max(2.0, smoothing.getValue());
        double desiredYawVelocity = Mth.clamp(yawDifference * response,
                -horizontalSpeed.getValue(), horizontalSpeed.getValue());
        double desiredPitchVelocity = verticalAim.getValue()
                ? Mth.clamp(pitchDifference * response, -verticalSpeed.getValue(), verticalSpeed.getValue()) : 0.0;
        double change = acceleration.getValue() * dt;
        yawVelocity = approach(yawVelocity, desiredYawVelocity, change);
        pitchVelocity = approach(pitchVelocity, desiredPitchVelocity, change);

        double yawStep = clampToDifference(yawVelocity * dt, yawDifference);
        double pitchStep = clampToDifference(pitchVelocity * dt, pitchDifference);
        client.player.setYRot(client.player.getYRot() + (float) yawStep);
        if (verticalAim.getValue()) {
            client.player.setXRot(Mth.clamp(client.player.getXRot() + (float) pitchStep, -90.0f, 90.0f));
        }
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eyes = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0f);
        for (LivingEntity candidate : client.level.getEntitiesOfClass(LivingEntity.class,
                client.player.getBoundingBox().inflate(range.getValue()), this::allowedTarget)) {
            double distance = client.player.distanceTo(candidate);
            if (distance > range.getValue() || !client.player.hasLineOfSight(candidate)) continue;
            Vec3 direction = candidate.getEyePosition().subtract(eyes).normalize();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(direction), -1.0, 1.0)));
            if (angle > fov.getValue() * 0.5) continue;
            double score = priority.is("HEALTH") ? candidate.getHealth()
                    : priority.is("DISTANCE") ? distance : angle;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean allowedTarget(LivingEntity entity) {
        if (entity == client.player || !entity.isAlive() || entity instanceof ArmorStand) return false;
        if (entity instanceof Player player) return targetPlayers.getValue() && TargetUtil.canTarget(player);
        return targetMobs.getValue();
    }

    private double frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0.0;
        }
        double delta = Math.min(0.05, Math.max(0.001, (now - lastFrameNanos) / 1_000_000_000.0));
        lastFrameNanos = now;
        return delta;
    }

    private void dampToStop(double dt) {
        double change = acceleration.getValue() * Math.max(0.001, dt);
        yawVelocity = approach(yawVelocity, 0.0, change);
        pitchVelocity = approach(pitchVelocity, 0.0, change);
    }

    private void resetMotion() {
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        lastFrameNanos = 0L;
    }

    private static double approach(double value, double target, double amount) {
        if (value < target) return Math.min(value + amount, target);
        return Math.max(value - amount, target);
    }

    private static double clampToDifference(double step, double difference) {
        if (difference == 0.0 || Math.signum(step) != Math.signum(difference)) return 0.0;
        return Math.abs(step) > Math.abs(difference) ? difference : step;
    }
}
