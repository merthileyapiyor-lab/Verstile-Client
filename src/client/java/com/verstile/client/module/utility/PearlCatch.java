package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class PearlCatch extends Module {
    public final NumberSetting fallDistance = new NumberSetting("Fall Distance", 7.0, 2.0, 30.0, 0.5);
    public final NumberSetting cooldownTicks = new NumberSetting("Cooldown Ticks", 30.0, 5.0, 100.0, 5.0);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    private int cooldown;

    public PearlCatch() {
        super("PearlCatch", "Throws an Ender Pearl automatically during a dangerous fall", Category.UTILITY, 0);
        addSetting(fallDistance); addSetting(cooldownTicks); addSetting(restoreSlot);
    }

    @Override public void onTick() {
        if (client.player == null || client.gameMode == null || cooldown-- > 0) return;
        if (client.player.fallDistance < fallDistance.getValue() || client.player.onGround()) return;
        int slot = find(Items.ENDER_PEARL);
        if (slot < 0) return;
        int old = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(slot);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        if (restoreSlot.getValue()) client.player.getInventory().setSelectedSlot(old);
        cooldown = cooldownTicks.getValue().intValue();
    }

    private int find(Item item) {
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }
}
