package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.PlacementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Silent block clutch for recoverable edge falls. */
public final class Clutch extends Module {
    public final NumberSetting minFallDistance = new NumberSetting("Min Fall Distance", 0.8, 0.0, 8.0, 0.1);
    public final NumberSetting minFallSpeed = new NumberSetting("Min Fall Speed", 0.18, 0.05, 1.5, 0.05);
    public final NumberSetting prediction = new NumberSetting("Prediction Ticks", 2.0, 0.0, 6.0, 0.5);
    public final NumberSetting reach = new NumberSetting("Placement Reach", 4.5, 3.0, 5.0, 0.1);
    public final NumberSetting attempts = new NumberSetting("Attempts Per Tick", 3.0, 1.0, 8.0, 1.0);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);
    public final BooleanSetting disableInCreative = new BooleanSetting("Disable In Creative", true);

    private boolean usedThisFall;
    private int preparedSlot = -1;
    private int slotDelay;

    public Clutch() {
        super("Clutch", "Silent scaffold-style block placement that catches recoverable edge falls", Category.MOVEMENT, 0);
        addSetting(minFallDistance); addSetting(minFallSpeed); addSetting(prediction); addSetting(reach);
        addSetting(attempts); addSetting(restoreSlot); addSetting(swing); addSetting(disableInCreative);
    }

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null) return;
        if (client.player.onGround() || client.player.isInWater()) { reset(); return; }
        if (usedThisFall || (disableInCreative.getValue() && client.player.isCreative())) return;
        if (client.player.fallDistance < minFallDistance.getValue()
                || client.player.getDeltaMovement().y > -minFallSpeed.getValue()) return;

        int blockSlot = PlacementUtil.findBlockSlot(client);
        if (blockSlot < 0) return;
        if (client.player.getInventory().getSelectedSlot() != blockSlot) {
            preparedSlot = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(blockSlot);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(blockSlot));
            slotDelay = 1;
            return;
        }
        if (slotDelay-- > 0) return;

        Vec3 velocity = client.player.getDeltaMovement();
        double lead = prediction.getValue();
        double predictedX = client.player.getX() + velocity.x * lead;
        double predictedZ = client.player.getZ() + velocity.z * lead;
        int feetY = (int) Math.floor(client.player.getBoundingBox().minY);
        Set<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(BlockPos.containing(predictedX, feetY - 1, predictedZ));
        candidates.add(BlockPos.containing(client.player.getX(), feetY - 1, client.player.getZ()));
        // Edge falls often need the block on the platform-facing side rather
        // than exactly below the player's center.
        for (int yOffset = 1; yOffset <= 2; yOffset++) {
            BlockPos center = BlockPos.containing(predictedX, feetY - yOffset, predictedZ);
            candidates.add(center.north()); candidates.add(center.south());
            candidates.add(center.east()); candidates.add(center.west());
        }

        int tried = 0;
        for (BlockPos target : candidates) {
            if (!client.level.getBlockState(target).canBeReplaced()) continue;
            if (PlacementUtil.findSupport(client, target, reach.getValue()) == null) continue;
            if (tried++ >= attempts.getValue().intValue()) break;
            boolean placed = PlacementUtil.place(client, target, "SILENT", swing.getValue(), false, reach.getValue());
            if (!placed) continue;
            if (restoreSlot.getValue() && preparedSlot >= 0) {
                client.player.getInventory().setSelectedSlot(preparedSlot);
                client.getConnection().send(new ServerboundSetCarriedItemPacket(preparedSlot));
            }
            return;
        }
    }

    private void reset() {
        usedThisFall = false;
        preparedSlot = -1;
        slotDelay = 0;
    }
}
