package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Swaps an elytra into the chest slot before a long fall and restores a
 * chestplate after landing. It only uses normal inventory clicks, so the
 * server receives the same inventory actions as a manual swap.
 */
public final class AutoElytra extends Module {
    public final NumberSetting fallDistance = new NumberSetting("Fall Distance", 2.0, 0.5, 8.0, 0.5);
    public final NumberSetting minDurability = new NumberSetting("Min Durability %", 10.0, 1.0, 100.0, 1.0);
    public final NumberSetting swapDelay = new NumberSetting("Swap Delay Ticks", 8.0, 1.0, 20.0, 1.0);
    public final BooleanSetting restoreChestplate = new BooleanSetting("Restore Chestplate", true);
    public final BooleanSetting onlyWhileJumping = new BooleanSetting("Only While Jumping", false);
    public final BooleanSetting combatDive = new BooleanSetting("Combat Dive", true);
    public final BooleanSetting combatPlayers = new BooleanSetting("Combat Players", true);
    public final BooleanSetting combatMobs = new BooleanSetting("Combat Mobs", true);
    public final NumberSetting combatRange = new NumberSetting("Target Range", 32.0, 6.0, 64.0, 1.0);
    public final NumberSetting combatFov = new NumberSetting("Target FOV", 40.0, 10.0, 120.0, 5.0);
    public final NumberSetting maceDistance = new NumberSetting("Mace Distance", 4.0, 2.5, 6.0, 0.1);
    public final NumberSetting fireworkDelay = new NumberSetting("Firework Delay", 12.0, 5.0, 40.0, 1.0);

    private long lastSwapTick = Long.MIN_VALUE;
    private int rocketCooldown;
    private int attackDelay;
    private LivingEntity diveTarget;

    public AutoElytra() {
        super("AutoElytra", "Automatically swaps an elytra for a chestplate before falling", Category.UTILITY, 0);
        addSetting(fallDistance);
        addSetting(minDurability);
        addSetting(swapDelay);
        addSetting(restoreChestplate);
        addSetting(onlyWhileJumping);
        addSetting(combatDive); addSetting(combatPlayers); addSetting(combatMobs);
        addSetting(combatRange); addSetting(combatFov); addSetting(maceDistance); addSetting(fireworkDelay);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        // Never issue player-inventory slot clicks while another container is open.
        if (client.player.containerMenu != client.player.inventoryMenu) return;
        if (rocketCooldown > 0) rocketCooldown--;
        if (handleCombatDive()) return;
        long tick = client.level.getGameTime();
        if (tick - lastSwapTick < swapDelay.getValue().longValue()) return;

        ItemStack chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA)) {
            if (restoreChestplate.getValue() && client.player.onGround()) {
                int chestplate = findChestplate();
                if (chestplate >= 0 && swap(chestplate, tick)) lastSwapTick = tick;
            }
            return;
        }

        if (!shouldEquip()) return;
        int elytra = findElytra();
        if (elytra >= 0 && swap(elytra, tick)) lastSwapTick = tick;
    }

    private boolean handleCombatDive() {
        if (!combatDive.getValue()) return false;

        if (diveTarget != null) {
            if (!diveTarget.isAlive() || client.player.distanceTo(diveTarget) > maceDistance.getValue() + 2.0) {
                diveTarget = null; attackDelay = 0; return false;
            }
            if (attackDelay-- > 0) return true;
            int maceSlot = findHotbar(Items.MACE);
            if (maceSlot >= 0) {
                selectHotbar(maceSlot);
                client.gameMode.attack(client.player, diveTarget);
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            diveTarget = null;
            return true;
        }

        if (!client.player.isFallFlying()) return false;
        LivingEntity target = findCombatTarget();
        if (target == null) return false;
        double distance = client.player.distanceTo(target);
        if (distance > maceDistance.getValue()) {
            if (rocketCooldown == 0) {
                int rocketSlot = findHotbar(Items.FIREWORK_ROCKET);
                if (rocketSlot >= 0) {
                    int old = client.player.getInventory().getSelectedSlot();
                    selectHotbar(rocketSlot);
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                    client.player.swing(InteractionHand.MAIN_HAND);
                    selectHotbar(old);
                    rocketCooldown = fireworkDelay.getValue().intValue();
                }
            }
            return true;
        }

        int chestplate = findChestplate();
        if (chestplate < 0 || findHotbar(Items.MACE) < 0) return false;
        if (swap(chestplate, client.level.getGameTime())) {
            lastSwapTick = client.level.getGameTime();
            diveTarget = target;
            attackDelay = Math.max(1, swapDelay.getValue().intValue() / 2);
            return true;
        }
        return false;
    }

    private LivingEntity findCombatTarget() {
        double range = combatRange.getValue();
        var look = client.player.getViewVector(1.0f);
        var eyes = client.player.getEyePosition();
        return client.level.getEntitiesOfClass(LivingEntity.class, client.player.getBoundingBox().inflate(range), entity -> {
                    if (entity == client.player || !entity.isAlive() || entity instanceof ArmorStand) return false;
                    if (entity instanceof Player player && (!combatPlayers.getValue() || !TargetUtil.canTarget(player))) return false;
                    if (!(entity instanceof Player) && !combatMobs.getValue()) return false;
                    var direction = entity.getEyePosition().subtract(eyes).normalize();
                    double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(direction)))));
                    return angle <= combatFov.getValue() * 0.5 && client.player.hasLineOfSight(entity);
                }).stream().min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);
    }

    private int findHotbar(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }

    private void selectHotbar(int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        if (client.getConnection() != null) client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private boolean shouldEquip() {
        if (client.player.onGround() || client.player.isInWater() || client.player.isFallFlying()) return false;
        if (onlyWhileJumping.getValue() && !client.options.keyJump.isDown()) return false;
        return client.player.fallDistance >= fallDistance.getValue()
                || client.player.getDeltaMovement().y < -0.12;
    }

    private int findElytra() {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(Items.ELYTRA) && durabilityPercent(stack) >= minDurability.getValue()) return slot;
        }
        return -1;
    }

    private int findChestplate() {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && client.player.isEquippableInSlot(stack, EquipmentSlot.CHEST)) return slot;
        }
        return -1;
    }

    private double durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem()) return 100.0;
        return 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
    }

    private boolean swap(int inventorySlot, long tick) {
        int windowSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int container = client.player.containerMenu.containerId;
        client.gameMode.handleInventoryMouseClick(container, windowSlot, 0, ClickType.PICKUP, client.player);
        client.gameMode.handleInventoryMouseClick(container, 6, 0, ClickType.PICKUP, client.player);
        client.gameMode.handleInventoryMouseClick(container, windowSlot, 0, ClickType.PICKUP, client.player);
        return true;
    }

    @Override
    public void onDisable() {
        lastSwapTick = Long.MIN_VALUE;
        rocketCooldown = 0;
        attackDelay = 0;
        diveTarget = null;
    }
}
