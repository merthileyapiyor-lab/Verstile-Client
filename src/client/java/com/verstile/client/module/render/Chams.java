package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class Chams extends Module implements WorldRenderModule {
    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    public final NumberSetting range = new NumberSetting("Range", 48.0, 8.0, 128.0, 4.0);
    public final NumberSetting color = new NumberSetting("RGB", 0xB14DFF, 0, 0xFFFFFF, 1);
    public final NumberSetting opacity = new NumberSetting("Fill Opacity", 22.0, 2.0, 80.0, 2.0);
    private volatile List<LivingEntity> targets = List.of();

    public Chams() {
        super("Chams", "Solid-color world silhouettes without the glowing effect", Category.RENDER, 0);
        addSetting(players); addSetting(mobs); addSetting(throughWalls); addSetting(range); addSetting(color); addSetting(opacity);
    }

    @Override public void onTick() {
        if (client.player == null || client.level == null) { targets = List.of(); return; }
        double r = range.getValue();
        targets = List.copyOf(client.level.getEntitiesOfClass(LivingEntity.class,
                client.player.getBoundingBox().inflate(r), entity -> entity != client.player && entity.isAlive()
                        && ((players.getValue() && entity instanceof Player) || (mobs.getValue() && !(entity instanceof Player)))));
    }

    @Override public void onDisable() { targets = List.of(); }

    @Override public void renderWorld() {
        int argb = ((int) Math.round(opacity.getValue() * 2.55) << 24) | color.getValue().intValue();
        for (LivingEntity entity : targets) {
            var gizmo = Gizmos.cuboid(entity.getBoundingBox(), GizmoStyle.fill(argb));
            if (throughWalls.getValue()) gizmo.setAlwaysOnTop();
        }
    }
}
