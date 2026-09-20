package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Keeps you from slipping off edges of blocks", Category.MOVEMENT, 0);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null) return;
        Vec3 motion = client.player.getDeltaMovement();
        double length = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double dx = length > 0.01 ? motion.x / length : 0.0;
        double dz = length > 0.01 ? motion.z / length : 0.0;
        BlockPos edge = BlockPos.containing(client.player.getX() + dx * 0.65,
                client.player.getBoundingBox().minY - 0.12, client.player.getZ() + dz * 0.65);
        boolean deepDrop = client.level.getBlockState(edge).isAir();
        // Allow ordinary 1-4 block drops. Sneak only when there is still no
        // floor four full blocks below the ledge.
        for (int depth = 1; depth <= 4 && deepDrop; depth++) {
            if (!client.level.getBlockState(edge.below(depth)).isAir()) deepDrop = false;
        }
        boolean protect = deepDrop && client.player.onGround() && length > 0.01;
        client.options.keyShift.setDown(protect || org.lwjgl.glfw.GLFW.glfwGetKey(
                client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }

    @Override
    public void onDisable() {
        if (client.options != null) client.options.keyShift.setDown(false);
    }
}
