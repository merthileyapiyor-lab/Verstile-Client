package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.tags.ItemTags;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MurderMystery extends Module {
    public final NumberSetting scanRange = new NumberSetting("Scan Range", 48.0, 8.0, 128.0, 4.0);
    public final BooleanSetting actionBar = new BooleanSetting("Action Bar Alerts", true);
    private final Set<UUID> warnedMurderers = new HashSet<>();
    private final Set<UUID> warnedDetectives = new HashSet<>();

    public MurderMystery() {
        super("MurderMystery", "Identifies visible sword and bow holders in minigames", Category.UTILITY, 0);
        addSetting(scanRange); addSetting(actionBar);
    }

    @Override public void onEnable() { warnedMurderers.clear(); warnedDetectives.clear(); }

    @Override public void onTick() {
        if (client.player == null || client.level == null) return;
        for (Player player : client.level.players()) {
            if (player == client.player || client.player.distanceTo(player) > scanRange.getValue()) continue;
            var stack = player.getMainHandItem();
            var item = stack.getItem();
            if (stack.is(ItemTags.SWORDS) && warnedMurderers.add(player.getUUID())) alert("§cMurderer: §f" + player.getScoreboardName());
            if ((item instanceof BowItem || item instanceof CrossbowItem) && warnedDetectives.add(player.getUUID())) alert("§bDetective: §f" + player.getScoreboardName());
        }
    }

    private void alert(String message) {
        client.player.displayClientMessage(Component.literal("§5[Verstile] " + message), actionBar.getValue());
    }
}
