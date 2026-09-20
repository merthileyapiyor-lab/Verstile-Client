package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.PlacementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

public class Scaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "GODBRIDGE", "GODBRIDGE", "SILENT", "BLATANT");
    public final ModeSetting rotationMode = new ModeSetting("Rotation Mode", "SILENT", "NONE", "LEGIT", "SILENT", "BLATANT");
    public final NumberSetting placeDelay = new NumberSetting("Place Delay", 0.0, 0.0, 6.0, 1.0);
    public final NumberSetting ahead = new NumberSetting("Forward Prediction", 1.5, 0.0, 3.5, 0.25);
    public final NumberSetting placements = new NumberSetting("Attempts Per Tick", 2.0, 1.0, 4.0, 1.0);
    public final NumberSetting jumpInterval = new NumberSetting("Jump Interval", 8.0, 2.0, 20.0, 1.0);
    public final NumberSetting reach = new NumberSetting("Placement Reach", 4.5, 3.0, 5.0, 0.1);
    public final BooleanSetting keepHeight = new BooleanSetting("Keep Height", true);
    public final BooleanSetting allowInCreative = new BooleanSetting("Allow In Creative", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", false);
    public final BooleanSetting autoJump = new BooleanSetting("Godbridge Auto Jump", true);
    public final BooleanSetting autoSprint = new BooleanSetting("Godbridge Sprint", true);
    public final BooleanSetting tower = new BooleanSetting("Tower", true);
    public final BooleanSetting safeWalk = new BooleanSetting("Safe Walk", true);
    private int baseY;
    private int cooldown;
    private int bridgeTicks;
    private boolean sprintBeforeEnable;
    private int slotReadyDelay;

    public Scaffold() {
        super("Scaffold", "Server-side placement against the face of an existing block", Category.MOVEMENT, 0);
        addSetting(mode);
        addSetting(rotationMode);
        addSetting(placeDelay);
        addSetting(ahead);
        addSetting(placements);
        addSetting(jumpInterval);
        addSetting(reach);
        addSetting(keepHeight);
        addSetting(allowInCreative);
        addSetting(swing);
        addSetting(restoreSlot);
        addSetting(autoJump);
        addSetting(autoSprint);
        addSetting(tower);
        addSetting(safeWalk);
    }

    @Override
    public void onEnable() {
        if (client.player != null) {
            baseY = (int) Math.floor(client.player.getBoundingBox().minY) - 1;
            sprintBeforeEnable = client.player.isSprinting();
        }
        cooldown = 0;
        bridgeTicks = 0;
        slotReadyDelay = 0;
    }

    @Override
    public void onDisable() {
        if (client.player != null) client.player.setSprinting(sprintBeforeEnable);
        cooldown = 0;
        bridgeTicks = 0;
        slotReadyDelay = 0;
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (!allowInCreative.getValue() && client.player.isCreative()) return;
        int blockSlot = PlacementUtil.findBlockSlot(client);
        if (blockSlot < 0) return;
        if (client.player.getInventory().getSelectedSlot() != blockSlot) {
            client.player.getInventory().setSelectedSlot(blockSlot);
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSetCarriedItemPacket(blockSlot));
            }
            // Let the server acknowledge the held block before interaction.
            slotReadyDelay = 1;
            return;
        }
        if (slotReadyDelay-- > 0) return;
        boolean moving = Math.abs(client.player.zza) > 0.01 || Math.abs(client.player.xxa) > 0.01;
        boolean towering = tower.getValue() && client.options.keyJump.isDown() && !moving;
        if (mode.is("GODBRIDGE") && moving) {
            bridgeTicks++;
            if (autoSprint.getValue()) client.player.setSprinting(true);
            if (autoJump.getValue() && client.player.onGround()
                    && bridgeTicks % Math.max(2, jumpInterval.getValue().intValue()) == 0) client.player.jumpFromGround();
        }
        if (cooldown-- > 0) return;

        Vec3 velocity = client.player.getDeltaMovement();
        double directionX = velocity.x, directionZ = velocity.z;
        if (moving) {
            double yaw = Math.toRadians(client.player.getYRot());
            directionX = -Math.sin(yaw) * client.player.zza + Math.cos(yaw) * client.player.xxa;
            directionZ = Math.cos(yaw) * client.player.zza + Math.sin(yaw) * client.player.xxa;
        }
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength > 0.001) {
            directionX /= directionLength;
            directionZ /= directionLength;
        } else {
            directionX = 0.0;
            directionZ = 0.0;
        }
        int y = keepHeight.getValue() ? baseY : (int) Math.floor(client.player.getBoundingBox().minY) - 1;
        int attempts = mode.is("BLATANT") ? placements.getValue().intValue() : 1;
        Set<BlockPos> targets = new LinkedHashSet<>();
        // Always try the block directly below the player first. The old order
        // started with the farthest predicted block, which usually had no
        // neighbour to right-click and made Scaffold appear broken.
        targets.add(BlockPos.containing(client.player.getX(), y, client.player.getZ()));
        // Leading-edge placement makes bridging start before the player's
        // centre has fully crossed into the next block.
        targets.add(BlockPos.containing(client.player.getX() + directionX * 0.85, y,
                client.player.getZ() + directionZ * 0.85));
        int predictionSteps = Math.max(1, (int) Math.ceil(ahead.getValue() / 0.35));
        for (int step = 1; step <= predictionSteps; step++) {
            double prediction = ahead.getValue() * step / predictionSteps;
            targets.add(BlockPos.containing(client.player.getX() + directionX * prediction, y,
                    client.player.getZ() + directionZ * prediction));
        }

        int placed = 0;
        for (BlockPos target : targets) {
            String effectiveRotation = mode.is("BLATANT") ? "LEGIT"
                    : mode.is("SILENT") || mode.is("GODBRIDGE") ? "SILENT" : rotationMode.getValue();
            if (PlacementUtil.place(client, target, effectiveRotation, swing.getValue(),
                    restoreSlot.getValue(), reach.getValue())) {
                placed++;
                if (placed >= attempts) break;
            }
        }
        if (placed > 0) {
            cooldown = placeDelay.getValue().intValue();
            if (towering && client.player.onGround()) client.player.jumpFromGround();
        } else if (safeWalk.getValue() && moving && client.player.onGround()) {
            BlockPos edge = BlockPos.containing(client.player.getX() + directionX * 0.55, y,
                    client.player.getZ() + directionZ * 0.55);
            if (client.level.getBlockState(edge).canBeReplaced()
                    && PlacementUtil.findSupport(client, edge, reach.getValue()) == null) {
                Vec3 motion = client.player.getDeltaMovement();
                client.player.setDeltaMovement(0.0, motion.y, 0.0);
            }
        }
    }
}
