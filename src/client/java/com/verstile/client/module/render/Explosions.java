package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class Explosions extends Module implements WorldRenderModule {
    public final BooleanSetting tnt = new BooleanSetting("TNT", true);
    public final BooleanSetting crystals = new BooleanSetting("End Crystals", true);
    public final BooleanSetting destructionArea = new BooleanSetting("Red Destruction Area", true);
    public final BooleanSetting damageArea = new BooleanSetting("Orange Damage Area", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 2.0, 0.5, 5.0, 0.25);
    private volatile List<ExplosionSource> sources = List.of();

    public Explosions() {
        super("Explosions", "TNT/crystal destruction and entity-damage radii", Category.RENDER, 0);
        addSetting(tnt); addSetting(crystals); addSetting(destructionArea); addSetting(damageArea);
        addSetting(throughWalls); addSetting(lineWidth);
    }

    @Override public void onTick() {
        if (client.player == null || client.level == null) { sources = List.of(); return; }
        var area = client.player.getBoundingBox().inflate(96.0);
        List<ExplosionSource> fresh = new ArrayList<>();
        if (tnt.getValue()) for (PrimedTnt entity : client.level.getEntitiesOfClass(PrimedTnt.class, area, Entity::isAlive)) {
            fresh.add(new ExplosionSource(entity.position(), 4.0f, "TNT"));
        }
        if (crystals.getValue()) for (EndCrystal entity : client.level.getEntitiesOfClass(EndCrystal.class, area, Entity::isAlive)) {
            fresh.add(new ExplosionSource(entity.position(), 6.0f, "Crystal"));
        }
        sources = List.copyOf(fresh);
    }

    @Override public void onDisable() { sources = List.of(); }

    @Override public void renderWorld() {
        float width = lineWidth.getValue().floatValue();
        for (ExplosionSource source : sources) {
            if (destructionArea.getValue()) {
                var red = Gizmos.circle(source.center(), source.power(), GizmoStyle.stroke(0xE8FF2020, width));
                if (throughWalls.getValue()) red.setAlwaysOnTop();
            }
            if (damageArea.getValue()) {
                // Vanilla explosion entity checks use power * 2 as their
                // maximum distance; exposure and armour reduce real damage.
                var orange = Gizmos.circle(source.center(), source.power() * 2.0f,
                        GizmoStyle.stroke(0xE8FF9718, width));
                if (throughWalls.getValue()) orange.setAlwaysOnTop();
            }
            var center = Gizmos.point(source.center(), 0xFFFF3030, 6.0f);
            if (throughWalls.getValue()) center.setAlwaysOnTop();
        }
    }

    private record ExplosionSource(Vec3 center, float power, String type) {}
}
