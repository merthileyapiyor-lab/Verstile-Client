package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class AutoClicker extends Module {
    private final Random random = new Random();
    private int timer = 0;
    private int targetDelay = 2;
    // rolling bucket: track last 5 click gaps for natural variance
    private final long[] clickTimes = new long[5];
    private int clickIdx = 0;

    public final NumberSetting minCps = new NumberSetting("Min CPS", 10.0, 1.0, 20.0, 1.0);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", 15.0, 1.0, 20.0, 1.0);
    public final BooleanSetting jitter     = new BooleanSetting("Jitter Aim", true);
    public final BooleanSetting cooldown   = new BooleanSetting("Wait Cooldown", true);
    public final NumberSetting  extraDelay = new NumberSetting("Extra Delay ms", 0.0, 0.0, 80.0, 5.0);

    public AutoClicker() {
        super("AutoClicker", "Simulates human clicking with jitter and CPS randomization", Category.COMBAT, 0);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(jitter);
        addSetting(cooldown);
        addSetting(extraDelay);
    }

    @Override
    public void onTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (!client.options.keyAttack.isDown()) return;

        // Only attack when weapon cooldown is ready (avoids triple-hit jitter that flags NCP/Vulcan)
        if (cooldown.getValue() && client.player.getAttackStrengthScale(0.5f) < 0.9f) return;

        timer++;
        if (timer >= targetDelay) {
            timer = 0;
            int min = minCps.getValue().intValue();
            int max = Math.max(min, maxCps.getValue().intValue());
            int cps = min + random.nextInt(Math.max(1, max - min + 1));

            // Gaussian-ish distribution around chosen CPS for more human look
            double ticks = 20.0 / cps;
            ticks += (random.nextGaussian() * 0.5);
            targetDelay = Math.max(1, (int) Math.round(ticks));

            // Optional extra delay for bypassing strict servers (simulate ping variance)
            int extraMs = extraDelay.getValue().intValue();
            if (extraMs > 0 && random.nextInt(4) == 0) {
                // Skip this tick 25% of the time to simulate slight delay
                return;
            }

            if (jitter.getValue() && random.nextBoolean()) {
                float yawJitter   = (random.nextFloat() - 0.5f) * 0.35f;
                float pitchJitter = (random.nextFloat() - 0.5f) * 0.20f;
                client.player.setYRot(client.player.getYRot() + yawJitter);
                client.player.setXRot(client.player.getXRot() + pitchJitter);
            }

            client.player.swing(InteractionHand.MAIN_HAND);
            if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
                if (client.gameMode != null && client.crosshairPickEntity != null
                        && (!(client.crosshairPickEntity instanceof Player player) || TargetUtil.canTarget(player))) {
                    client.gameMode.attack(client.player, client.crosshairPickEntity);
                }
            }

            // Log click timestamp for future pattern analysis
            clickTimes[clickIdx % clickTimes.length] = System.currentTimeMillis();
            clickIdx++;
        }
    }
}
