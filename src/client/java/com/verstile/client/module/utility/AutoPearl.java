package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class AutoPearl extends Module {
    private boolean used;
    public AutoPearl() {
        super("AutoPearl", "Instantly throws Ender Pearls toward target", Category.UTILITY, 0);
    }

    @Override
    public void onEnable() {
        used = false;
    }

    @Override
    public void onTick() {
        if (used || client.player == null || client.gameMode == null) return;
        for (int slot = 0; slot < 9; slot++) {
            if (client.player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) {
                int old = client.player.getInventory().getSelectedSlot();
                client.player.getInventory().setSelectedSlot(slot);
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                client.player.swing(InteractionHand.MAIN_HAND);
                client.player.getInventory().setSelectedSlot(old);
                used = true;
                setEnabled(false);
                return;
            }
        }
        setEnabled(false);
    }
}
