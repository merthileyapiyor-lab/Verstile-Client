package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoAnchor extends Module {
    public final NumberSetting delayTicks = new NumberSetting("Action Delay Ticks", 2.0, 1.0, 10.0, 1.0);
    public final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", true);
    public final BooleanSetting requireUseHeld = new BooleanSetting("Require Right Click", true);
    private int cooldown;

    public AutoAnchor() {
        super("AutoAnchor", "Charges and activates the Respawn Anchor under the crosshair", Category.UTILITY, 0);
        addSetting(delayTicks); addSetting(restoreSlot); addSetting(requireUseHeld);
    }

    @Override public void onTick() {
        if (client.player == null || client.level == null || client.gameMode == null || cooldown-- > 0) return;
        if (requireUseHeld.getValue() && !client.options.keyUse.isDown()) return;
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var state = client.level.getBlockState(hit.getBlockPos());
        if (!state.is(Blocks.RESPAWN_ANCHOR)) return;
        int charge = state.getValue(RespawnAnchorBlock.CHARGE);
        int desiredSlot = charge < 1 ? find(Items.GLOWSTONE) : findNonGlowstone();
        if (desiredSlot < 0) return;
        int old = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(desiredSlot);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        client.player.swing(InteractionHand.MAIN_HAND);
        if (restoreSlot.getValue()) client.player.getInventory().setSelectedSlot(old);
        cooldown = delayTicks.getValue().intValue();
    }

    private int find(Item item) {
        for (int slot = 0; slot < 9; slot++) if (client.player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }

    private int findNonGlowstone() {
        for (int slot = 0; slot < 9; slot++) {
            if (!client.player.getInventory().getItem(slot).isEmpty() && !client.player.getInventory().getItem(slot).is(Items.GLOWSTONE)) return slot;
        }
        return -1;
    }
}
