package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;

public class KnockbackDelay extends Module {
    public final NumberSetting delayTicks = new NumberSetting("Jump Delay Ticks", 2.0, 0.0, 10.0, 1.0);
    public final BooleanSetting requireGround = new BooleanSetting("Require Ground", true);
    private int timer = -1;
    private int previousHurtTime;
    private int groundWait;

    public KnockbackDelay() {
        super("KnockbackDelay", "Delays the post-hit jump reset by a configurable number of ticks", Category.COMBAT, 0);
        addSetting(delayTicks); addSetting(requireGround);
    }

    @Override public void onEnable() { timer = -1; previousHurtTime = 0; groundWait = 0; }

    @Override public void onTick() {
        if (client.player == null) return;
        if (client.player.hurtTime > previousHurtTime) {
            timer = delayTicks.getValue().intValue();
            groundWait = 10;
        }
        previousHurtTime = client.player.hurtTime;
        if (timer > 0) timer--;
        else if (timer == 0) {
            if ((!requireGround.getValue() || client.player.onGround()) && !client.player.isInWater()) {
                client.player.jumpFromGround();
                timer = -1;
                groundWait = 0;
            } else if (groundWait-- <= 0) {
                timer = -1;
            }
        }
    }
}
