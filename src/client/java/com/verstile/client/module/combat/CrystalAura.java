package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class CrystalAura extends Module {
    public final ModeSetting rotation = new ModeSetting("Rotation", "SILENT", "SILENT", "BLATANT");
    public final NumberSetting cps = new NumberSetting("Crystal CPS", 20.0, 1.0, 50.0, 1.0);
    public final NumberSetting targetRange = new NumberSetting("Target Range", 7.0, 3.0, 12.0, 0.5);
    public final NumberSetting interactRange = new NumberSetting("Interact Range", 4.5, 3.0, 6.0, 0.1);
    public final NumberSetting searchRange = new NumberSetting("Obsidian Search", 5.0, 2.0, 8.0, 1.0);
    public final NumberSetting targetMemory = new NumberSetting("Hit Memory Ticks", 40.0, 5.0, 100.0, 5.0);
    public final BooleanSetting triggerOnHit = new BooleanSetting("Trigger On Hit", true);
    public final BooleanSetting triggerAbove = new BooleanSetting("Trigger One Level Above", true);
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target All Mobs", true);
    public final BooleanSetting allowBedrock = new BooleanSetting("Allow Bedrock", true);
    public final BooleanSetting autoSwitchCrystal = new BooleanSetting("Auto Switch Crystal", true);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting auraSwing = new BooleanSetting("Aura Swing", true);

    public final BooleanSetting autoObsidian = new BooleanSetting("Auto Obsidian", true);
    public final BooleanSetting placeSwing = new BooleanSetting("Place Swing", true);
    public final NumberSetting placeDelay = new NumberSetting("Obsidian Delay Ticks", 3.0, 0.0, 10.0, 1.0);

    private LivingEntity rememberedTarget;
    private int memoryTicks;
    private boolean wasAttackPressed;
    private boolean wasUsePressed;
    private int placeCooldown;
    private double actionBudget;

    public CrystalAura() {
        super("CrystalAura", "Silent crystal placement and breaking around attacked or elevated targets", Category.COMBAT, 0);
        addSetting(rotation);
        addSetting(cps);
        addSetting(targetRange);
        addSetting(interactRange);
        addSetting(searchRange);
        addSetting(targetMemory);
        addSetting(triggerOnHit);
        addSetting(triggerAbove);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(allowBedrock);
        addSetting(autoSwitchCrystal);
        addSetting(restoreSlot);
        addSetting(auraSwing);
        addSetting(autoObsidian);
        addSetting(placeSwing);
        addSetting(placeDelay);
    }

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (placeCooldown > 0) placeCooldown--;
        handleAutoObsidian(mc);
        updateHitTarget(mc);

        LivingEntity target = validRememberedTarget(mc) ? rememberedTarget : findElevatedTarget(mc);
        if (target == null) {
            actionBudget = 0.0;
            return;
        }
        BlockPos base = findBestBase(mc, target);
        if (base == null) return;
        int crystalSlot = findHotbarSlot(mc, Items.END_CRYSTAL);
        if (crystalSlot < 0) return;

        actionBudget = Math.min(5.0, actionBudget + cps.getValue() / 20.0);
        int actions = Math.min(3, (int) actionBudget);
        if (actions <= 0) return;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (autoSwitchCrystal.getValue()) switchSlot(mc, crystalSlot);
        if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return;

        float[] aim = rotations(mc.player.getEyePosition(), new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5));
        float oldYaw = mc.player.getYRot(), oldPitch = mc.player.getXRot();
        applyRotation(mc, aim[0], aim[1]);
        for (int action = 0; action < actions; action++) {
            EndCrystal crystal = findCrystal(mc, base);
            if (crystal != null) {
                mc.gameMode.attack(mc.player, crystal);
                if (auraSwing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
            } else if (mc.level.getBlockState(base.above()).isAir() && mc.level.getBlockState(base.above(2)).isAir()) {
                BlockHitResult top = new BlockHitResult(
                        new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5),
                        Direction.UP, base, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, top);
                if (auraSwing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
            }
            actionBudget -= 1.0;
        }
        if (rotation.is("SILENT")) sendRotation(mc, oldYaw, oldPitch);
        if (restoreSlot.getValue() && autoSwitchCrystal.getValue()) switchSlot(mc, previousSlot);
    }

    private void updateHitTarget(Minecraft mc) {
        boolean attackPressed = mc.options.keyAttack.isDown();
        if (triggerOnHit.getValue() && attackPressed && !wasAttackPressed
                && mc.crosshairPickEntity instanceof LivingEntity living && isAllowedTarget(mc, living)) {
            rememberedTarget = living;
            memoryTicks = targetMemory.getValue().intValue();
        }
        wasAttackPressed = attackPressed;
        if (memoryTicks > 0) memoryTicks--;
        if (!validRememberedTarget(mc)) rememberedTarget = null;
    }

    private boolean validRememberedTarget(Minecraft mc) {
        return rememberedTarget != null && memoryTicks > 0 && rememberedTarget.isAlive()
                && mc.player.distanceTo(rememberedTarget) <= targetRange.getValue()
                && isAllowedTarget(mc, rememberedTarget);
    }

    private LivingEntity findElevatedTarget(Minecraft mc) {
        if (!triggerAbove.getValue()) return null;
        return mc.level.getEntitiesOfClass(LivingEntity.class,
                        mc.player.getBoundingBox().inflate(targetRange.getValue()),
                        living -> isAllowedTarget(mc, living)
                                && living.getY() >= mc.player.getY() + 0.75
                                && living.getY() <= mc.player.getY() + 2.25)
                .stream().min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);
    }

    private boolean isAllowedTarget(Minecraft mc, LivingEntity living) {
        if (living == mc.player || !living.isAlive() || living instanceof ArmorStand) return false;
        if (living instanceof Player player) return targetPlayers.getValue() && TargetUtil.canTarget(player);
        return targetMobs.getValue();
    }

    private BlockPos findBestBase(Minecraft mc, LivingEntity target) {
        int radius = searchRange.getValue().intValue();
        BlockPos center = mc.player.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    var state = mc.level.getBlockState(pos);
                    if (!state.is(Blocks.OBSIDIAN) && !(allowBedrock.getValue() && state.is(Blocks.BEDROCK))) continue;
                    if (!mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()) continue;
                    double playerDistance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
                    if (playerDistance > interactRange.getValue()) continue;
                    // Prefer a nearby base, but gently bias toward one close enough to damage the selected target.
                    double score = playerDistance + Math.max(0.0, target.position().distanceTo(Vec3.atCenterOf(pos)) - 4.5) * 2.0;
                    if (score < bestScore) { bestScore = score; best = pos.immutable(); }
                }
            }
        }
        return best;
    }

    private EndCrystal findCrystal(Minecraft mc, BlockPos base) {
        AABB box = new AABB(base.above()).inflate(0.4, 1.3, 0.4);
        return mc.level.getEntitiesOfClass(EndCrystal.class, box, EndCrystal::isAlive)
                .stream().min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);
    }

    private void applyRotation(Minecraft mc, float yaw, float pitch) {
        if (rotation.is("SILENT")) sendRotation(mc, yaw, pitch);
        else {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void sendRotation(Minecraft mc, float yaw, float pitch) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                mc.player.onGround(), mc.player.horizontalCollision));
    }

    private float[] rotations(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    private void handleAutoObsidian(Minecraft mc) {
        boolean usePressed = mc.options.keyUse.isDown();
        if (!autoObsidian.getValue() || mc.screen != null) { wasUsePressed = usePressed; return; }
        if (!usePressed || wasUsePressed || placeCooldown > 0) { wasUsePressed = usePressed; return; }
        wasUsePressed = true;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.player.getEyePosition().distanceTo(hit.getLocation()) > interactRange.getValue()) return;
        int obsidianSlot = findHotbarSlot(mc, Items.OBSIDIAN);
        if (obsidianSlot < 0) return;
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        switchSlot(mc, obsidianSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (placeSwing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
        placeCooldown = placeDelay.getValue().intValue();
        if (restoreSlot.getValue()) switchSlot(mc, previousSlot);
    }

    private int findHotbarSlot(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player.getMainHandItem().is(item)) return mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) if (mc.player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }

    private void switchSlot(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void clearState() {
        rememberedTarget = null;
        memoryTicks = 0;
        wasAttackPressed = false;
        wasUsePressed = false;
        placeCooldown = 0;
        actionBudget = 0.0;
    }

    @Override
    public void onDisable() {
        clearState();
    }
}
