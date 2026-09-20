package com.example.autopvp.combat;

import com.example.autopvp.AutoPvpMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class CombatAssistant {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random random = new Random();
    
    private static int attackDelayTicks = 0;
    private static int potDelayTicks = 0;
    private static int wTapTicks = -1;
    
    // Rotation smoothing parameters
    private static float currentYaw = 0.0f;
    private static float currentPitch = 0.0f;
    private static boolean isRotating = false;

    public static LivingEntity targetEntity = null;

    public static void tick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        // Decrement tickers
        if (attackDelayTicks > 0) attackDelayTicks--;
        if (potDelayTicks > 0) potDelayTicks--;
        if (wTapTicks >= 0) {
            wTapTicks--;
            if (wTapTicks == 0) {
                player.setSprinting(true);
            }
        }

        // Check if any combat feature is enabled
        boolean combatEnabled = AutoPvpMod.config.crystalPvp || 
                                AutoPvpMod.config.macePvp || 
                                AutoPvpMod.config.swordPvp || 
                                AutoPvpMod.config.axePvp || 
                                AutoPvpMod.config.spearPvp || 
                                AutoPvpMod.config.potPvp;

        // Automations (runs independently of combat targeting)
        if (AutoPvpMod.config.totemCycling) {
            handleAutoTotem();
        }

        // If no combat features are active, do not scan, rotate, or attack
        if (!combatEnabled) {
            targetEntity = null;
            isRotating = false;
            return;
        }

        // 1. Target Selection
        findTarget();

        if (targetEntity != null) {
            // Smoothly look at the target before interacting (Legit Rotations)
            smoothLookAtTarget(player, targetEntity);

            if (AutoPvpMod.config.potPvp) {
                handleAutoPot();
            }

            // Decide active combat mode based on settings and items
            if (AutoPvpMod.config.crystalPvp && hasCrystalsInInventory()) {
                handleCrystalPvp();
            } else if (AutoPvpMod.config.macePvp && player.getMainHandItem().is(Items.MACE)) {
                handleMacePvp();
            } else if (AutoPvpMod.config.spearPvp && player.getMainHandItem().is(Items.TRIDENT)) {
                handleSpearPvp();
            } else if (AutoPvpMod.config.swordPvp || AutoPvpMod.config.axePvp) {
                handleMeleePvp();
            }
        } else {
            isRotating = false;
        }
    }

    private static void findTarget() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // Query nearby targets, restricting maximum reach to a legit 6 blocks (combat limits)
        List<LivingEntity> nearby = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(6.0),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && player.hasLineOfSight(e)
        ).stream()
        .sorted(Comparator.comparingDouble(player::distanceTo))
        .toList();

        targetEntity = nearby.isEmpty() ? null : nearby.get(0);
    }

    private static void smoothLookAtTarget(LocalPlayer player, LivingEntity target) {
        Vec3 eyes = player.getEyePosition(1.0f);
        Vec3 targetPos = target.getBoundingBox().getCenter();
        
        double diffX = targetPos.x - eyes.x;
        double diffY = targetPos.y - eyes.y;
        double diffZ = targetPos.z - eyes.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));

        if (!isRotating) {
            currentYaw = player.getYRot();
            currentPitch = player.getXRot();
            isRotating = true;
        }

        float speed = 15.0f + random.nextFloat() * 10.0f;
        
        currentYaw = lerpAngle(currentYaw, targetYaw, speed);
        currentPitch = lerpAngle(currentPitch, targetPitch, speed);

        player.setYRot(currentYaw);
        player.setXRot(currentPitch);
    }

    private static float lerpAngle(float start, float end, float maxStep) {
        float diff = wrapDegrees(end - start);
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return start + diff;
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static void handleAutoTotem() {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            int totemSlot = -1;
            for (int i = 0; i < 36; i++) {
                if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                    totemSlot = i;
                    break;
                }
            }

            if (totemSlot != -1) {
                int windowSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
                
                mc.gameMode.handleInventoryMouseClick(
                        player.containerMenu.containerId, windowSlot, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player
                );
                mc.gameMode.handleInventoryMouseClick(
                        player.containerMenu.containerId, 45, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player
                );
                mc.gameMode.handleInventoryMouseClick(
                        player.containerMenu.containerId, windowSlot, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player
                );
            }
        }
    }

    private static void handleAutoPot() {
        LocalPlayer player = mc.player;
        if (player == null || potDelayTicks > 0) return;

        if (player.getHealth() < 12.0f) {
            int potSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).is(Items.SPLASH_POTION)) {
                    potSlot = i;
                    break;
                }
            }

            if (potSlot != -1) {
                int oldSlot = player.getInventory().getSelectedSlot();
                switchSlot(potSlot);
                
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        player.getYRot(), 85.0f + random.nextFloat() * 5.0f, player.onGround(), player.horizontalCollision
                ));

                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                player.swing(InteractionHand.MAIN_HAND);

                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision
                ));
                switchSlot(oldSlot);

                potDelayTicks = 20 + random.nextInt(10); 
            }
        }
    }

    private static boolean hasCrystalsInInventory() {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.END_CRYSTAL)) return true;
        }
        return false;
    }

    private static void handleCrystalPvp() {
        LocalPlayer player = mc.player;
        if (player == null || targetEntity == null) return;

        double dist = player.distanceTo(targetEntity);
        if (dist > 4.0) return;

        List<EndCrystal> crystals = mc.level.getEntitiesOfClass(EndCrystal.class, targetEntity.getBoundingBox().inflate(3.0))
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .toList();

        if (!crystals.isEmpty() && attackDelayTicks == 0) {
            EndCrystal crystal = crystals.get(0);
            if (player.distanceTo(crystal) > 2.2f) {
                mc.gameMode.attack(player, crystal);
                player.swing(InteractionHand.MAIN_HAND);
                attackDelayTicks = 2 + random.nextInt(3);
                return;
            }
        }

        BlockPos targetPos = targetEntity.blockPosition().below();
        if (mc.level.getBlockState(targetPos).isAir()) {
            int obbySlot = findItemInHotbar(Items.OBSIDIAN);
            if (obbySlot != -1) {
                switchSlot(obbySlot);
                placeBlock(targetPos);
            }
        } else if (mc.level.getBlockState(targetPos).is(Blocks.OBSIDIAN)) {
            int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
            if (crystalSlot != -1) {
                switchSlot(crystalSlot);
                placeBlock(targetPos);
            }
        }
    }

    private static void handleMacePvp() {
        LocalPlayer player = mc.player;
        if (player == null || targetEntity == null) return;

        double threshold = 1.3 + (random.nextDouble() * 0.4);
        if (player.fallDistance > threshold && player.distanceTo(targetEntity) <= 3.8f) {
            if (player.getAttackStrengthScale(0.5f) >= 0.90f) {
                performAttack(targetEntity);
            }
        }
    }

    private static void handleSpearPvp() {
        LocalPlayer player = mc.player;
        if (player == null || targetEntity == null) return;

        double dist = player.distanceTo(targetEntity);
        if (dist >= 3.2 && dist <= 5.8) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STAB,
                    BlockPos.ZERO,
                    Direction.DOWN
            ));
            player.swing(InteractionHand.MAIN_HAND);
            attackDelayTicks = 8 + random.nextInt(5);
        } else if (dist < 3.2) {
            handleMeleePvp();
        }
    }

    private static void handleMeleePvp() {
        LocalPlayer player = mc.player;
        if (player == null || targetEntity == null || attackDelayTicks > 0) return;

        double dist = player.distanceTo(targetEntity);
        if (dist > 3.0) return;

        if (AutoPvpMod.config.axePvp && targetEntity.isUsingItem() && targetEntity.getUseItem().is(Items.SHIELD)) {
            int axeSlot = findAxeSlot();
            if (axeSlot != -1) {
                switchSlot(axeSlot);
            }
        } else if (AutoPvpMod.config.swordPvp) {
            int swordSlot = findSwordSlot();
            if (swordSlot != -1) {
                switchSlot(swordSlot);
            }
        }

        if (player.getAttackStrengthScale(0.5f) >= 0.92f) {
            if (AutoPvpMod.config.critChaining && player.onGround() && !player.isInWater() && !player.onClimbable()) {
                if (random.nextFloat() > 0.15f) {
                    player.jumpFromGround();
                }
                return;
            }

            performAttack(targetEntity);

            if (AutoPvpMod.config.wTap) {
                player.setSprinting(false);
                wTapTicks = 2 + random.nextInt(2);
            }
        }
    }

    private static void performAttack(LivingEntity target) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        if (target.getDeltaMovement().horizontalDistanceSqr() > 0.05 && random.nextFloat() < 0.05) {
            player.swing(InteractionHand.MAIN_HAND);
            attackDelayTicks = 8 + random.nextInt(4);
            return;
        }

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        attackDelayTicks = 9 + random.nextInt(4);
    }

    private static int findItemInHotbar(net.minecraft.world.item.Item item) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static int findAxeSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    private static int findSwordSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(ItemTags.SWORDS)) return i;
        }
        return -1;
    }

    private static void switchSlot(int slot) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        player.getInventory().setSelectedSlot(slot);
    }

    private static void placeBlock(BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return;

        BlockHitResult hit = new BlockHitResult(
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
        );

        mc.getConnection().send(new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hit,
                0
        ));
        player.swing(InteractionHand.MAIN_HAND);
    }
}
