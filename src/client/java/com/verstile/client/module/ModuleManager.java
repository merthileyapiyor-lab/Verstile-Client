package com.verstile.client.module;

import com.verstile.client.module.combat.*;
import com.verstile.client.module.hud.*;
import com.verstile.client.module.movement.*;
import com.verstile.client.module.render.*;
import com.verstile.client.module.utility.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<>();
    private final RemoteUpdate remoteUpdate;

    private ModuleManager() {
        // Combat Modules
        register(new AimAssist());
        register(new AntiBot());
        register(new AutoClicker());
        register(new FastPlace());
        register(new Reach());
        register(new Velocity());
        register(new Triggerbot());
        register(new SilentAura());
        register(new CrystalAura());
        register(new CrystalSpam());
        register(new CrystalPearl());
        register(new HitSelect());
        register(new KnockbackDelay());
        register(new WTap());

        // Render & Visual Modules
        register(new ESP());
        register(new Chams());
        register(new Nametags());
        register(new StorageESP());
        register(new SpawnerESP());
        register(new Tracer());
        register(new Projectiles());
        register(new Explosions());
        register(new HealthPrediction());
        register(new Radar());
        register(new Xray());

        // Independently configurable, draggable HUD elements
        register(new HudEditorModule());
        register(new ArrayListHud());
        register(new TargetHud());
        register(new PerformanceHud());
        register(new CoordinatesHud());
        register(new ActiveProfileHud());
        register(new PotionEffectsHud());
        register(new PingHud());
        register(new FriendsHud());

        // Movement Modules
        register(new Sprint());
        register(new Fly());
        register(new FreeCam());
        register(new Speed());
        register(new Scaffold());
        register(new Blink());
        register(new JumpReset());
        register(new SafeWalk());
        register(new MLG());
        register(new Clutch());
        register(new NoFall());

        // Utility & World Modules
        register(new InventoryManager());
        register(new AutoTotem());
        register(new AutoElytra());
        register(new AutoAnchor());
        register(new AutoMace());
        register(new AutoPearl());
        register(new PearlCatch());
        register(new AntiFireball());
        register(new SpearLunge());
        register(new MurderMystery());
        register(new AutoRespawn());
        register(new AntiCrasher());
        remoteUpdate = new RemoteUpdate();
        register(remoteUpdate);
        register(new Panic());
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> categoryModules = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) {
                categoryModules.add(m);
            }
        }
        return categoryModules;
    }

    public Module getModuleByName(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
            if (m instanceof MLG && name.equalsIgnoreCase("MLG")) {
                return m;
            }
        }
        return null;
    }

    public void onTick() {
        for (Module m : modules) {
            if (m == remoteUpdate) continue;
            if (m.isEnabled()) {
                try {
                    m.onTick();
                } catch (RuntimeException error) {
                    // One optional module must never take down the whole game.
                    System.err.println("[Verstile] Disabled " + m.getName() + " after tick failure: " + error);
                    error.printStackTrace();
                    m.setEnabled(false);
                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                        net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§c[Verstile] §f"
                                        + m.getName() + " disabled after an internal error"), false);
                    }
                }
            }
        }
        // Remote updates are a background service. Its GUI toggle is cosmetic
        // and intentionally cannot disable or duplicate the updater.
        remoteUpdate.onTick();
    }

    /** Runs services that are safe before a player/world exists. */
    public void onBackgroundTick() {
        remoteUpdate.onTick();
    }

    public void onRender() {
        for (Module module : modules) {
            if (module.isEnabled()) module.onRender();
        }
    }

    public void renderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        for (Module module : modules) {
            if (module instanceof HudModule hud && !(hud instanceof HudEditorModule)) {
                hud.renderHud(graphics, false);
            }
        }
    }

    public void renderWorld() {
        for (Module module : modules) {
            if (module.isEnabled() && module instanceof WorldRenderModule renderer) renderer.renderWorld();
        }
    }
}
