package com.verstile.client.util;

import com.verstile.client.friend.FriendManager;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.combat.AntiBot;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TargetUtil {
    private static final Map<UUID, Boolean> CACHE = new HashMap<>();
    private static long cachedTick = Long.MIN_VALUE;
    private TargetUtil() {}

    public static boolean canTarget(Player player) {
        if (player == null || player.isSpectator() || FriendManager.isFriend(player)) return false;
        long tick = player.level().getGameTime();
        if (tick != cachedTick) { cachedTick = tick; CACHE.clear(); }
        Boolean cached = CACHE.get(player.getUUID());
        if (cached != null) return cached;
        Module module = ModuleManager.getInstance().getModuleByName("AntiBot");
        boolean allowed = !(module instanceof AntiBot antiBot) || !antiBot.isEnabled() || !antiBot.isBot(player);
        CACHE.put(player.getUUID(), allowed);
        return allowed;
    }
}
