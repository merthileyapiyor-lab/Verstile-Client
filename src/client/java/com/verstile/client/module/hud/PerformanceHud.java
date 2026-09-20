package com.verstile.client.module.hud;

import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PerformanceHud extends HudModule {
    public final BooleanSetting showFps = new BooleanSetting("Show FPS", true);
    public final BooleanSetting showCps = new BooleanSetting("Show CPS", true);
    public final NumberSetting color = new NumberSetting("Text RGB", 0xFFFFFF, 0, 0xFFFFFF, 1);
    private final Deque<Long> clicks = new ArrayDeque<>();
    private boolean attackWasDown;

    public PerformanceHud() {
        super("FPS / CPS", "Live frame-rate and left-click counter", 8, 72);
        addSetting(showFps);
        addSetting(showCps);
        addSetting(color);
    }

    @Override
    public void onTick() {
        boolean down = client.options.keyAttack.isDown();
        long now = System.currentTimeMillis();
        if (down && !attackWasDown) clicks.addLast(now);
        attackWasDown = down;
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) clicks.removeFirst();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        background(graphics, contentWidth(), contentHeight());
        String text = text();
        graphics.drawString(client.font, text, 6, 6, 0xFF000000 | color.getValue().intValue(), true);
    }

    private String text() {
        StringBuilder text = new StringBuilder();
        if (showFps.getValue()) text.append(client.getFps()).append(" FPS");
        if (showCps.getValue()) {
            if (!text.isEmpty()) text.append("  •  ");
            text.append(clicks.size()).append(" CPS");
        }
        return text.toString();
    }

    @Override public int contentWidth() { return Math.max(90, client.font.width(text()) + 12); }
    @Override public int contentHeight() { return 21; }
}
