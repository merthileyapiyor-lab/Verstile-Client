package com.verstile.client.gui;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.profile.AutoConfig;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.setting.Setting;
import com.verstile.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import com.verstile.client.util.ClientVersion;
import com.verstile.client.util.DesktopOverlaySupport;

public class VerstileGuiScreen extends Screen {
    private static final int PREFERRED_WIDTH = 700;
    private static final int PREFERRED_HEIGHT = 400;
    private static final int LEFT_COLUMN_WIDTH = 280;
    private static Category rememberedCategory = Category.COMBAT;
    private static String rememberedModule = "";
    private Category currentCategory = rememberedCategory;
    private Module selectedModule = null;
    private Module listeningModule = null;

    private NumberSetting activeSlider = null;
    private int sliderTrackX = 0;
    private int sliderTrackWidth = 0;

    // Scroll offsets for smooth navigation
    private int moduleScroll = 0;
    private int settingsScroll = 0;
    private EditBox searchBox;

    public VerstileGuiScreen() {
        super(Component.literal("Verstile Client"));
    }

    @Override
    protected void init() {
        int w = guiWidth();
        int h = guiHeight();
        int px = (this.width - w) / 2;
        int py = (this.height - h) / 2;
        searchBox = new EditBox(this.font, px + 10, py + 46, LEFT_COLUMN_WIDTH - 20, 20,
                Component.literal("Search modules"));
        searchBox.setHint(Component.literal("Search modules..."));
        searchBox.setMaxLength(48);
        searchBox.setResponder(value -> {
            moduleScroll = 0;
            List<Module> filtered = visibleModules();
            if (selectedModule == null || !filtered.contains(selectedModule)) {
                selectedModule = filtered.isEmpty() ? null : filtered.get(0);
            }
        });
        addRenderableWidget(searchBox);

        List<Module> mods = ModuleManager.getInstance().getModulesByCategory(currentCategory);
        if (selectedModule == null && !rememberedModule.isBlank()) {
            selectedModule = mods.stream().filter(module -> module.getName().equals(rememberedModule)).findFirst().orElse(null);
        }
        if (selectedModule == null && !mods.isEmpty()) {
            selectedModule = mods.get(0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = guiWidth();
        int h = guiHeight();
        int px = (this.width - w) / 2;
        int py = (this.height - h) / 2;

        // Dark background overlay
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        // Rounded Prestige-style window and soft shadow.
        roundRect(graphics, px - 4, py - 4, w + 8, h + 8, 13, 0x50000000);
        roundRect(graphics, px, py, w, h, 10, 0xF00D0F14);
        roundRect(graphics, px, py, w, 34, 10, 0xF8151820);

        // Top accent line (Glowing Cyan)
        roundRect(graphics, px + 2, py + 1, w - 4, 2, 1, 0xFF9C4DFF);

        // Title text & Logo
        roundRect(graphics, px + 14, py + 11, 9, 9, 3, 0xFF9C4DFF);
        graphics.drawString(this.font, "VERSTILE", px + 28, py + 7, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "v" + ClientVersion.current(), px + 28, py + 19, 0xFF858B9A, false);

        // Client is the normal Minecraft screen; Injection is the external,
        // capture-excluded desktop window.
        drawModeSelector(graphics, px, py, mouseX, mouseY);

        // Category Navigation Tabs (Right side of header)
        Category[] cats = Category.values();
        int tabHeight = 22;
        int tabStartX = categoryStartX(px, w);

        roundRect(graphics, px + 207, py + 6, 48, 22, 5, 0xFF191D26);
        graphics.drawCenteredString(font, "FRIENDS", px + 231, py + 13, 0xFFC98BFF);

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            String tabLabel = cat.getIcon() + " " + cat.getName();
            int tabWidth = categoryTabWidth(cat);
            int tx = tabStartX;
            int ty = py + 6;
            boolean isCurrent = (cat == currentCategory);
            boolean hovered = mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= ty && mouseY <= ty + tabHeight;

            int tabBg = isCurrent ? 0xFF9C4DFF : (hovered ? 0xFF252A36 : 0xFF191D26);
            int tabTextColor = isCurrent ? 0xFF0A0C10 : (hovered ? 0xFFFFFFFF : 0xFF8E95A5);

            roundRect(graphics, tx, ty, tabWidth, tabHeight, 5, tabBg);
            int textW = this.font.width(tabLabel);
            graphics.drawString(this.font, tabLabel, tx + (tabWidth - textW) / 2, ty + 7, tabTextColor, false);
            tabStartX += tabWidth + 4;
        }

        // Section Dividers
        int leftColWidth = LEFT_COLUMN_WIDTH;
        int contentY = py + 40;
        int contentHeight = h - 48;
        graphics.fill(px + leftColWidth, contentY, px + leftColWidth + 1, contentY + contentHeight, 0xFF1F232D);

        // ==================== LEFT COLUMN: MODULE LIST ====================
        List<Module> modules = visibleModules();
        int modY = contentY + 34;
        int cardHeight = 26;

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            int cy = modY + i * (cardHeight + 4) - moduleScroll;
            if (cy + cardHeight < contentY || cy > py + h - 10) continue;

            int cx = px + 10;
            int cw = leftColWidth - 20;

            boolean isSelected = (mod == selectedModule);
            boolean hovered = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + cardHeight;

            int cardBg = isSelected ? 0xFF1E232E : (hovered ? 0xFF171A22 : 0xFF13151C);
            roundRect(graphics, cx, cy, cw, cardHeight, 6, cardBg);

            // Left selection pill
            if (isSelected) {
                roundRect(graphics, cx, cy + 3, 3, cardHeight - 6, 2, 0xFF9C4DFF);
            }

            // Module status indicator & name
            int nameColor = mod.isEnabled() ? 0xFFC98BFF : (hovered ? 0xFFE0E0E0 : 0xFF99A0B0);
            graphics.drawString(this.font, mod.getName(), cx + 10, cy + 9, nameColor, false);

            // Keybind Pill
            String keyStr = (listeningModule == mod) ? "..." : getKeyLabel(mod.getKeyBind());
            int keyW = Math.max(22, this.font.width(keyStr) + 8);
            int kx = cx + cw - keyW - 40;
            int ky = cy + 5;
            roundRect(graphics, kx, ky, keyW, 16, 5, 0xFF1E222C);
            graphics.drawString(this.font, keyStr, kx + 4, ky + 4, 0xFFFFAA00, false);

            // Toggle Pill [ON / OFF]
            int tx = cx + cw - 32;
            int ty = cy + 5;
            int toggleBg = mod.isEnabled() ? 0xFF9C4DFF : 0xFF2A2E3A;
            int toggleText = mod.isEnabled() ? 0xFF0A0C10 : 0xFF7A8090;
            String tStr = mod.isEnabled() ? "ON" : "OFF";
            roundRect(graphics, tx, ty, 28, 16, 6, toggleBg);
            graphics.drawString(this.font, tStr, tx + 7, ty + 4, toggleText, false);
        }

        // ==================== RIGHT COLUMN: SETTINGS INSPECTOR ====================
        int rightX = px + leftColWidth + 12;
        int rightWidth = w - leftColWidth - 24;

            if (selectedModule != null) {
            // Header card
            graphics.drawString(this.font, selectedModule.getName().toUpperCase(), rightX, contentY + 8, 0xFFFFFFFF, false);
            graphics.drawString(this.font, selectedModule.getDescription(), rightX, contentY + 22, 0xFF7A8292, false);

            graphics.fill(rightX, contentY + 36, rightX + rightWidth, contentY + 37, 0xFF1F232D);

            int setY = contentY + 46;
            List<Setting<?>> settings = selectedModule.getSettings();

            if (settings.isEmpty()) {
                graphics.drawString(this.font, "No configurable settings for this module.", rightX, setY + 10, 0xFF555B66, false);
            }

            for (Setting<?> s : settings) {
                if (setY + 30 > py + h - 12) break;

                if (s instanceof BooleanSetting) {
                    BooleanSetting boolSetting = (BooleanSetting) s;
                    boolean hovered = mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= setY && mouseY <= setY + 24;

                    // Setting Card
                    roundRect(graphics, rightX, setY, rightWidth, 24, 6, hovered ? 0xFF171A22 : 0xFF12141A);
                    graphics.drawString(this.font, boolSetting.getName(), rightX + 8, setY + 8, 0xFFD0D4DC, false);

                    // Toggle Button
                    int bx = rightX + rightWidth - 42;
                    int by = setY + 4;
                    int btnColor = boolSetting.getValue() ? 0xFF9C4DFF : 0xFF262B36;
                    int txtColor = boolSetting.getValue() ? 0xFF0A0C10 : 0xFF7A8090;
                    roundRect(graphics, bx, by, 36, 16, 6, btnColor);
                    graphics.drawString(this.font, boolSetting.getValue() ? "YES" : "NO", bx + 10, by + 4, txtColor, false);

                    setY += 30;
                } else if (s instanceof ModeSetting modeSetting) {
                    boolean hovered = mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= setY && mouseY <= setY + 24;
                    roundRect(graphics, rightX, setY, rightWidth, 24, 6, hovered ? 0xFF171A22 : 0xFF12141A);
                    graphics.drawString(this.font, modeSetting.getName(), rightX + 8, setY + 8, 0xFFD0D4DC, false);
                    String value = modeSetting.getValue();
                    int valueWidth = this.font.width(value);
                    roundRect(graphics, rightX + rightWidth - valueWidth - 18, setY + 4, valueWidth + 12, 16, 5, 0xFF30213E);
                    graphics.drawString(this.font, value, rightX + rightWidth - valueWidth - 12, setY + 8, 0xFFC98BFF, false);
                    setY += 30;
                } else if (s instanceof NumberSetting) {
                    NumberSetting numSetting = (NumberSetting) s;

                    // Setting Card
                    roundRect(graphics, rightX, setY, rightWidth, 34, 6, 0xFF12141A);

                    // Label & Value
                    graphics.drawString(this.font, numSetting.getName(), rightX + 8, setY + 5, 0xFFD0D4DC, false);
                    String valText = String.format("%.1f", numSetting.getValue());
                    graphics.drawString(this.font, valText, rightX + rightWidth - this.font.width(valText) - 8, setY + 5, 0xFFC98BFF, false);

                    // Slider Track
                    int trackX = rightX + 8;
                    int trackY = setY + 19;
                    int trackW = rightWidth - 16;
                    int trackH = 8;

                    roundRect(graphics, trackX, trackY, trackW, trackH, 4, 0xFF1E222A);

                    // Fill progress
                    double pct = (numSetting.getValue() - numSetting.getMin()) / (numSetting.getMax() - numSetting.getMin());
                    pct = Math.max(0.0, Math.min(1.0, pct));
                    int fillW = (int) (trackW * pct);

                    roundRect(graphics, trackX, trackY, fillW, trackH, 4, 0xFF9C4DFF);

                    // Thumb Knob
                    int knobX = trackX + fillW;
                    roundRect(graphics, knobX - 3, trackY - 2, 6, trackH + 4, 3, 0xFFFFFFFF);

                    setY += 40;
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (listeningModule != null) {
            listeningModule.setKeyBind(-(event.button() + 1));
            listeningModule = null;
            return true;
        }

        int w = guiWidth();
        int h = guiHeight();
        int px = (this.width - w) / 2;
        int py = (this.height - h) / 2;

        // GUI mode switcher in the top bar.
        int modeX = px + 84;
        int modeY = py + 6;
        int modeW = 120;
        if (mx >= modeX && mx <= modeX + modeW && my >= modeY && my <= modeY + 22) {
            boolean injection = mx >= modeX + modeW / 2.0;
            if (!injection) {
                GuiMode.select(GuiMode.CLIENT);
                AutoConfig.save();
                return true;
            }
            if (!DesktopOverlaySupport.isAvailable()) {
                GuiMode.select(GuiMode.CLIENT);
                AutoConfig.save();
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.literal("§b[Verstile] §fFallback GUI activated"), false);
                }
                return true;
            }
            GuiMode.select(GuiMode.INJECTION);
            AutoConfig.save();
            if (minecraft != null) minecraft.setScreen(null);
            ExternalClickGui.getInstance().toggle();
            return true;
        }

        // 1. Check Category Tabs
        Category[] cats = Category.values();
        int tabHeight = 22;
        int tabStartX = categoryStartX(px, w);

        if (mx >= px + 207 && mx <= px + 255 && my >= py + 6 && my <= py + 28) {
            if (minecraft != null) minecraft.setScreen(new FriendsScreen(this));
            return true;
        }

        for (int i = 0; i < cats.length; i++) {
            int tabWidth = categoryTabWidth(cats[i]);
            int tx = tabStartX;
            int ty = py + 6;
            if (mx >= tx && mx <= tx + tabWidth && my >= ty && my <= ty + tabHeight) {
                currentCategory = cats[i];
                List<Module> mList = ModuleManager.getInstance().getModulesByCategory(currentCategory);
                selectedModule = mList.isEmpty() ? null : mList.get(0);
                if (searchBox != null) searchBox.setValue("");
                rememberSelection();
                return true;
            }
            tabStartX += tabWidth + 4;
        }

        // 2. Check Module Cards
        int leftColWidth = LEFT_COLUMN_WIDTH;
        int contentY = py + 40;
        List<Module> modules = visibleModules();
        int modY = contentY + 34;
        int cardHeight = 26;

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            int cy = modY + i * (cardHeight + 4) - moduleScroll;
            if (cy + cardHeight < contentY || cy > py + h - 10) continue;

            int cx = px + 10;
            int cw = leftColWidth - 20;

            if (mx >= cx && mx <= cx + cw && my >= cy && my <= cy + cardHeight) {
                // Check Keybind click
                String keyStr = (listeningModule == mod) ? "..." : getKeyLabel(mod.getKeyBind());
                int keyW = Math.max(22, this.font.width(keyStr) + 8);
                int kx = cx + cw - keyW - 40;
                int ky = cy + 5;
                if (mx >= kx && mx <= kx + keyW && my >= ky && my <= ky + 16) {
                    listeningModule = mod;
                    return true;
                }

                // Check Toggle click
                int tx = cx + cw - 32;
                int ty = cy + 5;
                if (mx >= tx && mx <= tx + 28 && my >= ty && my <= ty + 16) {
                    mod.toggle();
                    return true;
                }

                // Otherwise select module for inspection
                selectedModule = mod;
                rememberSelection();
                return true;
            }
        }

        // 3. Check Settings Inspector
        if (selectedModule != null) {
            int rightX = px + leftColWidth + 12;
            int rightWidth = w - leftColWidth - 24;
            int setY = contentY + 46;

            for (Setting<?> s : selectedModule.getSettings()) {
                if (setY + 30 > py + h - 12) break;

                if (s instanceof BooleanSetting) {
                    BooleanSetting bs = (BooleanSetting) s;
                    if (mx >= rightX && mx <= rightX + rightWidth && my >= setY && my <= setY + 24) {
                        bs.toggle();
                        return true;
                    }
                    setY += 30;
                } else if (s instanceof ModeSetting ms) {
                    if (mx >= rightX && mx <= rightX + rightWidth && my >= setY && my <= setY + 24) {
                        ms.cycle();
                        return true;
                    }
                    setY += 30;
                } else if (s instanceof NumberSetting) {
                    NumberSetting ns = (NumberSetting) s;
                    int trackX = rightX + 8;
                    int trackY = setY + 19;
                    int trackW = rightWidth - 16;
                    int trackH = 8;

                    if (mx >= trackX - 4 && mx <= trackX + trackW + 4 && my >= trackY - 6 && my <= trackY + trackH + 6) {
                        activeSlider = ns;
                        sliderTrackX = trackX;
                        sliderTrackWidth = trackW;
                        updateSlider(mx);
                        return true;
                    }
                    setY += 40;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int w = guiWidth();
        int h = guiHeight();
        int px = (this.width - w) / 2;
        int py = (this.height - h) / 2;
        int contentY = py + 40;
        int contentBottom = py + h - 10;
        int leftColWidth = LEFT_COLUMN_WIDTH;
        if (mouseX >= px + 8 && mouseX <= px + leftColWidth - 2
                && mouseY >= contentY && mouseY <= contentBottom) {
            int cardHeight = 26;
            int visibleHeight = Math.max(1, contentBottom - (contentY + 34));
            int totalHeight = visibleModules().size() * (cardHeight + 4);
            int maxScroll = Math.max(0, totalHeight - visibleHeight);
            moduleScroll = Math.max(0, Math.min(maxScroll,
                    moduleScroll - (int) Math.round(scrollY * 28.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeSlider != null) {
            updateSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeSlider = null;
        return super.mouseReleased(event);
    }

    private void updateSlider(double mouseX) {
        if (activeSlider == null || sliderTrackWidth <= 0) return;
        double pct = Math.max(0.0, Math.min(1.0, (mouseX - sliderTrackX) / (double) sliderTrackWidth));
        double val = activeSlider.getMin() + pct * (activeSlider.getMax() - activeSlider.getMin());
        activeSlider.setValueClamped(val);
    }

    private void rememberSelection() {
        rememberedCategory = currentCategory;
        rememberedModule = selectedModule == null ? "" : selectedModule.getName();
    }

    private int guiWidth() {
        return Math.min(PREFERRED_WIDTH, Math.max(530, this.width - 20));
    }

    private int guiHeight() {
        return Math.min(PREFERRED_HEIGHT, Math.max(340, this.height - 20));
    }

    private int categoryTabWidth(Category category) {
        return Math.max(42, this.font.width(category.getIcon() + " " + category.getName()) + 14);
    }

    private int categoryStartX(int px, int width) {
        int totalWidth = 0;
        Category[] categories = Category.values();
        for (Category category : categories) totalWidth += categoryTabWidth(category);
        totalWidth += Math.max(0, categories.length - 1) * 4;
        return px + width - totalWidth - 10;
    }

    private List<Module> visibleModules() {
        List<Module> modules = ModuleManager.getInstance().getModulesByCategory(currentCategory);
        if (searchBox == null || searchBox.getValue().isBlank()) return modules;
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return modules.stream().filter(module ->
                module.getName().toLowerCase(Locale.ROOT).contains(query)
                        || module.getDescription().toLowerCase(Locale.ROOT).contains(query)
                        || module.getCategory().getName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    @Override
    public void onClose() {
        rememberSelection();
        AutoConfig.save();
        super.onClose();
    }

    private void drawModeSelector(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
        int x = px + 84;
        int y = py + 6;
        int width = 120;
        int half = width / 2;
        boolean clientMode = GuiMode.selected() == GuiMode.CLIENT;
        boolean hoverClient = mouseX >= x && mouseX < x + half && mouseY >= y && mouseY <= y + 22;
        boolean hoverInjection = mouseX >= x + half && mouseX <= x + width && mouseY >= y && mouseY <= y + 22;
        roundRect(graphics, x, y, width, 22, 7, 0xFF11131A);
        roundRect(graphics, x + 2, y + 2, half - 3, 18, 5,
                clientMode ? 0xFF9C4DFF : (hoverClient ? 0xFF2A2534 : 0xFF191C24));
        roundRect(graphics, x + half + 1, y + 2, half - 3, 18, 5,
                !clientMode ? 0xFF9C4DFF : (hoverInjection ? 0xFF2A2534 : 0xFF191C24));
        int clientColor = clientMode ? 0xFFFFFFFF : 0xFF969AAA;
        int injectionColor = !clientMode ? 0xFFFFFFFF : 0xFF969AAA;
        graphics.drawString(font, "CLIENT", x + 10, y + 7, clientColor, false);
        graphics.drawString(font, "INJECT", x + half + 11, y + 7, injectionColor, false);
    }

    private static void roundRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double exactInset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            int inset = (int) Math.ceil(exactInset);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
            int edge = inset - 1;
            if (edge >= 0) {
                int smoothColor = scaleAlpha(color, Math.max(0.0, Math.min(1.0, inset - exactInset)));
                graphics.fill(x + edge, y + row, x + edge + 1, y + row + 1, smoothColor);
                graphics.fill(x + width - edge - 1, y + row, x + width - edge, y + row + 1, smoothColor);
                graphics.fill(x + edge, y + height - row - 1, x + edge + 1, y + height - row, smoothColor);
                graphics.fill(x + width - edge - 1, y + height - row - 1,
                        x + width - edge, y + height - row, smoothColor);
            }
        }
    }

    private static int scaleAlpha(int color, double coverage) {
        int alpha = (color >>> 24) & 0xFF;
        int smoothAlpha = Math.max(0, Math.min(255, (int) Math.round(alpha * coverage)));
        return (color & 0x00FFFFFF) | (smoothAlpha << 24);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listeningModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
                listeningModule.setKeyBind(0);
            } else {
                listeningModule.setKeyBind(key);
            }
            listeningModule = null;
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE && searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private String getKeyLabel(int key) {
        if (key == 0) return "-";
        if (key < 0) {
            int button = -key - 1;
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "M1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "M2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "M3";
                default -> "M" + (button + 1);
            };
        }
        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null) return n.toUpperCase();
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSH";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSH";
        if (key == GLFW.GLFW_KEY_SPACE) return "SPC";
        return "K" + key;
    }
}
