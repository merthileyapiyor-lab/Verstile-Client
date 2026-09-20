package com.verstile.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementUtil {
    private PlacementUtil() {}

    public static int findBlockSlot(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return -1;
        int fallback = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (fallback < 0) fallback = slot;
            var state = blockItem.getBlock().defaultBlockState();
            if (!state.getCollisionShape(client.level, BlockPos.ZERO).isEmpty()
                    && state.isCollisionShapeFullBlock(client.level, BlockPos.ZERO)) return slot;
        }
        return fallback;
    }

    public static BlockHitResult findSupport(Minecraft client, BlockPos target) {
        return findSupport(client, target, 5.0);
    }

    public static BlockHitResult findSupport(Minecraft client, BlockPos target, double maximumReach) {
        BlockHitResult nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            BlockPos support = target.relative(direction);
            if (!client.level.getBlockState(support).isAir()
                    && !client.level.getBlockState(support).getCollisionShape(client.level, support).isEmpty()) {
                Direction face = direction.getOpposite();
                Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                double distance = client.player.getEyePosition().distanceToSqr(hit);
                if (distance <= maximumReach * maximumReach && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = new BlockHitResult(hit, face, support, false);
                }
            }
        }
        return nearest;
    }

    public static boolean place(Minecraft client, BlockPos target, boolean silentRotation, boolean swing) {
        return place(client, target, silentRotation, swing, true);
    }

    public static boolean place(Minecraft client, BlockPos target, boolean silentRotation, boolean swing, boolean restoreSlot) {
        return place(client, target, silentRotation ? "SILENT" : "LEGIT", swing, restoreSlot, 5.0);
    }

    public static boolean place(Minecraft client, BlockPos target, String rotationMode,
                                boolean swing, boolean restoreSlot, double maximumReach) {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return false;
        if (!client.level.getBlockState(target).canBeReplaced()) return false;
        int slot = findBlockSlot(client);
        BlockHitResult hit = findSupport(client, target, maximumReach);
        if (slot < 0 || hit == null) return false;

        int previousSlot = client.player.getInventory().getSelectedSlot();
        if (slot != previousSlot) {
            client.player.getInventory().setSelectedSlot(slot);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
        float[] rotations = rotations(client.player, hit.getLocation());
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        boolean rotate = !"NONE".equalsIgnoreCase(rotationMode);
        boolean visible = "LEGIT".equalsIgnoreCase(rotationMode);
        if (rotate) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotations[0], rotations[1],
                    client.player.onGround(), client.player.horizontalCollision));
        }
        if (visible) {
            client.player.setYRot(rotations[0]);
            client.player.setXRot(rotations[1]);
        }
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        if (swing) client.player.swing(InteractionHand.MAIN_HAND);
        if (visible) {
            client.player.setYRot(oldYaw);
            client.player.setXRot(oldPitch);
        }
        // LEGIT restores visibly and on the wire. SILENT leaves the server-side
        // placement rotation active until vanilla's next movement packet; an
        // immediate restore made stricter servers reject the interaction.
        if (rotate && visible) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch,
                    client.player.onGround(), client.player.horizontalCollision));
        }
        if (restoreSlot && slot != previousSlot) {
            client.player.getInventory().setSelectedSlot(previousSlot);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(previousSlot));
        }
        return result.consumesAction();
    }

    private static float[] rotations(LocalPlayer player, Vec3 target) {
        Vec3 eyes = player.getEyePosition();
        double dx = target.x - eyes.x, dy = target.y - eyes.y, dz = target.z - eyes.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }
}
