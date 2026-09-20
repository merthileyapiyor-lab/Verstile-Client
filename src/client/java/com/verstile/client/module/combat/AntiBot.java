package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Shared player filter used by combat modules. */
public final class AntiBot extends Module {
    public final BooleanSetting requireTabList = new BooleanSetting("Require Tab List", true);
    public final BooleanSetting rejectInvisible = new BooleanSetting("Reject Invisible", true);
    public final BooleanSetting rejectDead = new BooleanSetting("Reject Dead", true);
    public final BooleanSetting rejectInvalidNames = new BooleanSetting("Reject Invalid Names", true);
    public final NumberSetting minimumAge = new NumberSetting("Minimum Age Ticks", 10.0, 0.0, 100.0, 5.0);

    public AntiBot() {
        super("AntiBot", "Prevents combat modules from targeting suspicious fake players", Category.COMBAT, 0);
        addSetting(requireTabList); addSetting(rejectInvisible); addSetting(rejectDead);
        addSetting(rejectInvalidNames); addSetting(minimumAge);
    }

    public boolean isBot(Player player) {
        if (player == null) return true;
        if (rejectDead.getValue() && (!player.isAlive() || player.isRemoved())) return true;
        if (rejectInvisible.getValue() && player.isInvisible()) return true;
        if (rejectInvalidNames.getValue() && !player.getScoreboardName().matches("[A-Za-z0-9_]{1,16}")) return true;
        if (player.tickCount < minimumAge.getValue().intValue()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (!requireTabList.getValue() || mc.getConnection() == null) return false;
        var info = mc.getConnection().getPlayerInfo(player.getUUID());
        if (info != null) return false;
        // Proxy/cracked networks can remap entity UUIDs while keeping the real
        // player in the tab list. Fall back to an exact profile-name match so
        // legitimate players are not rejected on Pika/Donut-style servers.
        return mc.getConnection().getOnlinePlayers().stream().noneMatch(listed -> {
            String listedName = listed.getProfile().name();
            return listedName != null && listedName.equalsIgnoreCase(player.getScoreboardName());
        });
    }
}
