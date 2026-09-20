package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;

public class Nametags extends Module {
    public final BooleanSetting showHealth = new BooleanSetting("Show Health", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", true);
    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    public final NumberSetting range = new NumberSetting("Range", 64.0, 8.0, 160.0, 4.0);

    public Nametags() {
        super("Nametags", "Renders readable nametags and health through walls", Category.RENDER, 0);
        addSetting(showHealth);
        addSetting(showDistance);
        addSetting(players);
        addSetting(mobs);
        addSetting(range);
    }
}
