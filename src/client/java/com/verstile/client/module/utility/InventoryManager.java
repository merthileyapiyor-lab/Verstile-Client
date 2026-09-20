package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class InventoryManager extends Module {
    public final BooleanSetting sortWeapon = new BooleanSetting("Sort Weapon", true);
    public final BooleanSetting sortBlocks = new BooleanSetting("Sort Blocks", true);
    public final NumberSetting weaponSlot = new NumberSetting("Weapon Slot", 1.0, 1.0, 9.0, 1.0);
    public final NumberSetting blockSlot = new NumberSetting("Block Slot", 9.0, 1.0, 9.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay Ticks", 8.0, 2.0, 40.0, 1.0);
    private int cooldown;

    public InventoryManager() {
        super("InventoryManager", "Sorts weapons and the largest block stack into chosen hotbar slots", Category.UTILITY, 0);
        addSetting(sortWeapon); addSetting(sortBlocks); addSetting(weaponSlot); addSetting(blockSlot); addSetting(delay);
    }

    @Override public void onTick() {
        if (client.player == null || client.gameMode == null || client.screen != null || cooldown-- > 0) return;
        if (sortWeapon.getValue() && moveWeapon(weaponSlot.getValue().intValue() - 1)) return;
        if (sortBlocks.getValue()) moveLargestBlockStack(blockSlot.getValue().intValue() - 1);
    }

    private boolean moveWeapon(int hotbar) {
        if (client.player.getInventory().getItem(hotbar).is(ItemTags.SWORDS)) return false;
        for (int slot = 9; slot < 36; slot++) {
            if (client.player.getInventory().getItem(slot).is(ItemTags.SWORDS)) return swap(slot, hotbar);
        }
        return false;
    }

    private boolean moveLargestBlockStack(int hotbar) {
        ItemStack current = client.player.getInventory().getItem(hotbar);
        int best = -1, count = current.getItem() instanceof BlockItem ? current.getCount() : 0;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BlockItem && stack.getCount() > count) { best = slot; count = stack.getCount(); }
        }
        return best >= 0 && swap(best, hotbar);
    }

    private boolean swap(int inventorySlot, int hotbar) {
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId,
                inventorySlot, hotbar, ClickType.SWAP, client.player);
        cooldown = delay.getValue().intValue();
        return true;
    }
}
