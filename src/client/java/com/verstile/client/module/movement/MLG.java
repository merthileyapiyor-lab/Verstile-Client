package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ground-aware pearl/water MLG that never fires immediately after a fall begins. */
public final class MLG extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "AUTO", "AUTO", "PEARL", "WATER");
    public final NumberSetting scanDepth = new NumberSetting("Ground Scan Depth", 32.0, 6.0, 64.0, 2.0);
    public final NumberSetting pearlHeight = new NumberSetting("Pearl Trigger Height", 10.0, 3.0, 24.0, 0.5);
    public final NumberSetting waterHeight = new NumberSetting("Water Trigger Height", 3.8, 2.0, 5.0, 0.1);
    public final NumberSetting fallDistance = new NumberSetting("Min Fall Distance", 3.0, 1.0, 12.0, 0.5);
    public final NumberSetting fallSpeed = new NumberSetting("Min Fall Speed", 0.35, 0.1, 1.5, 0.05);
    public final BooleanSetting silentPearl = new BooleanSetting("Silent Pearl", true);
    public final BooleanSetting silentWater = new BooleanSetting("Silent Water", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);
    public final BooleanSetting disableInCreative = new BooleanSetting("Disable In Creative", true);
    private boolean usedThisFall;
    private int preparedWaterSlot = -1;
    private int previousSlot = -1;
    private int waterSlotDelay;
    private BlockPos pendingWaterGround;

    public MLG() {
        super("MLG", "Ground-aware automatic pearl or water-bucket fall save", Category.MOVEMENT, 0);
        addSetting(mode); addSetting(scanDepth); addSetting(pearlHeight); addSetting(waterHeight);
        addSetting(fallDistance); addSetting(fallSpeed); addSetting(silentPearl); addSetting(silentWater);
        addSetting(restoreSlot); addSetting(swing); addSetting(disableInCreative);
    }

    @Override public void onEnable() { resetFall(); }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return;
        if (client.player.onGround() || client.player.isInWater()) { resetFall(); return; }
        if (usedThisFall || (disableInCreative.getValue() && client.player.isCreative())) return;
        if (client.player.fallDistance < fallDistance.getValue()
                || client.player.getDeltaMovement().y > -fallSpeed.getValue()) return;
        Ground ground = findGround();
        if (ground == null) return;

        if (pendingWaterGround != null) {
            if (waterSlotDelay-- > 0) return;
            usedThisFall = placeWater(pendingWaterGround, preparedWaterSlot);
            pendingWaterGround = null;
            return;
        }

        int pearl = find(Items.ENDER_PEARL);
        if (!mode.is("WATER") && pearl >= 0 && ground.distance() <= pearlHeight.getValue()) {
            usedThisFall = usePearl(pearl);
            return;
        }
        boolean waterAllowed = client.level.dimension() != Level.NETHER;
        int bucket = find(Items.WATER_BUCKET);
        if (!mode.is("PEARL") && waterAllowed && bucket >= 0 && ground.distance() <= waterHeight.getValue()) {
            previousSlot = client.player.getInventory().getSelectedSlot();
            preparedWaterSlot = bucket;
            switchSlot(bucket);
            pendingWaterGround = ground.pos();
            waterSlotDelay = 1;
        }
    }

    private Ground findGround() {
        double feetY = client.player.getBoundingBox().minY;
        BlockPos feet = BlockPos.containing(client.player.getX(), feetY, client.player.getZ());
        for (int depth = 1; depth <= scanDepth.getValue().intValue(); depth++) {
            BlockPos pos = feet.below(depth);
            var shape = client.level.getBlockState(pos).getCollisionShape(client.level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            double distance = feetY - top;
            if (distance >= 0.0) return new Ground(pos.immutable(), distance);
        }
        return null;
    }

    private boolean usePearl(int slot) {
        int oldSlot = client.player.getInventory().getSelectedSlot();
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        switchSlot(slot);
        if (silentPearl.getValue()) sendRotation(oldYaw, 89.5f);
        else client.player.setXRot(89.5f);
        var result = client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);
        if (silentPearl.getValue()) sendRotation(oldYaw, oldPitch);
        if (restoreSlot.getValue()) switchSlot(oldSlot);
        return result.consumesAction();
    }

    private void resetFall() {
        usedThisFall = false;
        preparedWaterSlot = -1;
        previousSlot = -1;
        waterSlotDelay = 0;
        pendingWaterGround = null;
    }

    private boolean placeWater(BlockPos ground, int slot) {
        int oldSlot = previousSlot >= 0 ? previousSlot : client.player.getInventory().getSelectedSlot();
        float oldYaw = client.player.getYRot(), oldPitch = client.player.getXRot();
        switchSlot(slot);
        Vec3 hitLocation = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, ground, false);
        if (silentWater.getValue()) sendRotation(oldYaw, 89.5f);
        else client.player.setXRot(89.5f);
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);
        if (silentWater.getValue()) sendRotation(oldYaw, oldPitch);
        if (restoreSlot.getValue()) switchSlot(oldSlot);
        return result.consumesAction();
    }

    private int find(Item item) {
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }

    private void switchSlot(int slot) {
        if (client.player.getInventory().getSelectedSlot() == slot) return;
        client.player.getInventory().setSelectedSlot(slot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void sendRotation(float yaw, float pitch) {
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                client.player.onGround(), client.player.horizontalCollision));
    }

    private record Ground(BlockPos pos, double distance) {}
}
