package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.NumberSetting;

public class FastPlace extends Module {
    public final NumberSetting delay = new NumberSetting("Right Click Delay", 0.0, 0.0, 4.0, 1.0);
    public FastPlace() {
        super("FastPlace", "Removes delay between placing blocks", Category.UTILITY, 0);
        addSetting(delay);
    }
}
