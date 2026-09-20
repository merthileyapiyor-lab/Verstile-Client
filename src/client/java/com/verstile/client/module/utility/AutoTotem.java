package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {
    public AutoTotem() {
        super("AutoTotem", "Automatically equips Totem of Undying to off-hand slot", Category.UTILITY, 0);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        if (!client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 36; i++) {
                if (client.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                    int windowSlot = i < 9 ? i + 36 : i;
                    client.gameMode.handleInventoryMouseClick(
                        client.player.containerMenu.containerId,
                        windowSlot,
                        0,
                        ClickType.PICKUP,
                        client.player
                    );
                    client.gameMode.handleInventoryMouseClick(
                        client.player.containerMenu.containerId,
                        45,
                        0,
                        ClickType.PICKUP,
                        client.player
                    );
                    client.gameMode.handleInventoryMouseClick(
                        client.player.containerMenu.containerId,
                        windowSlot,
                        0,
                        ClickType.PICKUP,
                        client.player
                    );
                    break;
                }
            }
        }
    }
}
