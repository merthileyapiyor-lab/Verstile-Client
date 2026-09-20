package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class Projectiles extends Module implements WorldRenderModule {
    public final NumberSetting range = new NumberSetting("Range", 96.0, 8.0, 192.0, 8.0);
    public final NumberSetting predictionTicks = new NumberSetting("Full Path Ticks", 120.0, 20.0, 240.0, 10.0);
    public final NumberSetting maxProjectiles = new NumberSetting("Max Projectiles", 24.0, 4.0, 64.0, 4.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.8, 0.5, 5.0, 0.1);
    public final NumberSetting color = new NumberSetting("RGB", 0x5DDCFF, 0, 0xFFFFFF, 1);
    public final BooleanSetting heldPreview = new BooleanSetting("Preview Before Throw", true);
    public final BooleanSetting landingPoint = new BooleanSetting("Landing Point", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    private volatile List<List<Vec3>> paths = List.of();

    public Projectiles() {
        super("Projectiles", "Predicts complete held and in-flight projectile paths", Category.RENDER, 0);
        addSetting(range); addSetting(predictionTicks); addSetting(maxProjectiles); addSetting(lineWidth);
        addSetting(color); addSetting(heldPreview); addSetting(landingPoint); addSetting(throughWalls);
    }

    @Override public void onTick() {
        if (client.player == null || client.level == null) { paths = List.of(); return; }
        List<List<Vec3>> fresh = new ArrayList<>();
        if (heldPreview.getValue()) {
            Launch launch = heldLaunch(client.player.getMainHandItem());
            if (launch == null) launch = heldLaunch(client.player.getOffhandItem());
            if (launch != null) fresh.add(predict(client.player.getEyePosition().add(client.player.getViewVector(1.0f).scale(0.25)),
                    client.player.getViewVector(1.0f).scale(launch.speed()).add(client.player.getDeltaMovement()), launch.gravity()));
        }
        int added = 0;
        for (Projectile projectile : client.level.getEntitiesOfClass(Projectile.class,
                client.player.getBoundingBox().inflate(range.getValue()), Projectile::isAlive)) {
            if (added++ >= maxProjectiles.getValue().intValue()) break;
            fresh.add(predict(projectile.position(), projectile.getDeltaMovement(), gravityFor(projectile)));
        }
        paths = List.copyOf(fresh);
    }

    @Override public void onDisable() { paths = List.of(); }

    @Override public void renderWorld() {
        int argb = 0xE0000000 | color.getValue().intValue();
        for (List<Vec3> path : paths) {
            for (int index = 1; index < path.size(); index++) {
                var line = Gizmos.line(path.get(index - 1), path.get(index), argb, lineWidth.getValue().floatValue());
                if (throughWalls.getValue()) line.setAlwaysOnTop();
            }
            if (landingPoint.getValue() && !path.isEmpty()) {
                var point = Gizmos.point(path.get(path.size() - 1), 0xFFFFC44D, 5.0f);
                if (throughWalls.getValue()) point.setAlwaysOnTop();
            }
        }
    }

    private List<Vec3> predict(Vec3 start, Vec3 initialVelocity, double gravity) {
        List<Vec3> path = new ArrayList<>();
        Vec3 position = start;
        Vec3 velocity = initialVelocity;
        path.add(position);
        for (int tick = 0; tick < predictionTicks.getValue().intValue(); tick++) {
            Vec3 next = position.add(velocity);
            HitResult hit = client.level.clip(new ClipContext(position, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, client.player));
            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getLocation());
                break;
            }
            path.add(next);
            position = next;
            velocity = velocity.scale(0.99).add(0.0, -gravity, 0.0);
            if (position.y < client.level.getMinY() - 8) break;
        }
        return List.copyOf(path);
    }

    private Launch heldLaunch(ItemStack stack) {
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) return new Launch(1.5, 0.03);
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.EXPERIENCE_BOTTLE)) return new Launch(0.75, 0.05);
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)) return new Launch(3.0, 0.05);
        if (stack.is(Items.WIND_CHARGE)) return new Launch(1.5, 0.0);
        return null;
    }

    private double gravityFor(Projectile projectile) {
        String type = projectile.getType().toString().toLowerCase();
        if (type.contains("wind_charge")) return 0.0;
        if (type.contains("arrow") || type.contains("trident")) return 0.05;
        if (type.contains("potion") || type.contains("experience_bottle")) return 0.05;
        return 0.03;
    }

    private record Launch(double speed, double gravity) {}
}
