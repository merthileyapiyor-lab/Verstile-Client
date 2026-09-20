package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

import java.util.Comparator;

public final class CrystalSpam extends Module {
    public final NumberSetting cps = new NumberSetting("Speed CPS", 50.0, 1.0, 100.0, 1.0);
    public final NumberSetting burstLimit = new NumberSetting("Max Actions Per Tick", 3.0, 1.0, 10.0, 1.0);
    public final NumberSetting reach = new NumberSetting("Reach", 4.5, 2.0, 6.0, 0.1);
    public final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);
    public final BooleanSetting allowBedrock = new BooleanSetting("Allow Bedrock", true);
    private double actionBudget;
    private BlockPos activeBase;
    private int originalSlot = -1;

    public CrystalSpam() {
        super("CrystalSpam", "Hold right click on obsidian to place and break crystals rapidly", Category.COMBAT, 0);
        addSetting(cps);
        addSetting(burstLimit);
        addSetting(reach);
        addSetting(autoSwitch);
        addSetting(restoreSlot);
        addSetting(swing);
        addSetting(allowBedrock);
    }

    @Override
    public void onEnable() {
        actionBudget = 0.0;
        activeBase = null;
        originalSlot = -1;
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || client.screen != null) return;
        if (!client.options.keyUse.isDown()) {
            actionBudget = 0.0;
            finishHold();
            return;
        }
        if (client.hitResult instanceof BlockHitResult lookedAt && lookedAt.getType() == HitResult.Type.BLOCK) {
            BlockPos lookedBase = lookedAt.getBlockPos();
            var lookedState = client.level.getBlockState(lookedBase);
            if (lookedState.is(Blocks.OBSIDIAN) || (allowBedrock.getValue() && lookedState.is(Blocks.BEDROCK))) {
                activeBase = lookedBase.immutable();
            }
        } else if (client.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal) {
            activeBase = crystal.blockPosition().below().immutable();
        }
        if (activeBase == null) return;

        BlockPos base = activeBase;
        var state = client.level.getBlockState(base);
        if (!state.is(Blocks.OBSIDIAN) && !(allowBedrock.getValue() && state.is(Blocks.BEDROCK))) return;
        if (client.player.getEyePosition().distanceTo(Vec3.atCenterOf(base)) > reach.getValue()) return;

        int maximumBurst = burstLimit.getValue().intValue();
        actionBudget = Math.min(maximumBurst * 2.0, actionBudget + cps.getValue() / 20.0);
        int actions = Math.min(maximumBurst, (int) actionBudget);
        if (actions <= 0) return;

        int crystalSlot = findCrystalSlot();
        if (crystalSlot < 0) return;
        if (autoSwitch.getValue() && client.player.getInventory().getSelectedSlot() != crystalSlot) {
            if (originalSlot < 0) originalSlot = client.player.getInventory().getSelectedSlot();
            switchSlot(crystalSlot);
        }
        if (!client.player.getMainHandItem().is(Items.END_CRYSTAL)) return;

        for (int i = 0; i < actions; i++) {
            EndCrystal crystal = findCrystal(base);
            if (crystal != null) {
                client.gameMode.attack(client.player, crystal);
                if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);
            } else if (client.level.getBlockState(base.above()).isAir()
                    && client.level.getBlockState(base.above(2)).isAir()) {
                BlockHitResult topFace = new BlockHitResult(
                        new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5),
                        Direction.UP, base, false);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, topFace);
                if (swing.getValue()) client.player.swing(InteractionHand.MAIN_HAND);
            }
            actionBudget -= 1.0;
        }

    }

    private void switchSlot(int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        if (client.getConnection() != null) client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void finishHold() {
        if (client.player != null && restoreSlot.getValue() && originalSlot >= 0) switchSlot(originalSlot);
        originalSlot = -1;
        activeBase = null;
    }

    @Override
    public void onDisable() {
        finishHold();
    }

    private int findCrystalSlot() {
        if (client.player.getMainHandItem().is(Items.END_CRYSTAL)) {
            return client.player.getInventory().getSelectedSlot();
        }
        if (!autoSwitch.getValue()) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (client.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)) return slot;
        }
        return -1;
    }

    private EndCrystal findCrystal(BlockPos base) {
        AABB box = new AABB(base.above()).inflate(0.35, 1.25, 0.35);
        return client.level.getEntitiesOfClass(EndCrystal.class, box, EndCrystal::isAlive)
                .stream().min(Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
    }
}
