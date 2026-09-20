package com.verstile.client;

import com.verstile.client.gui.ExternalClickGui;
import com.verstile.client.gui.VerstileGuiScreen;
import com.verstile.client.gui.GuiMode;
import com.verstile.client.gui.ChangelogScreen;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.utility.RemoteUpdate;
import com.verstile.client.profile.AutoConfig;
import com.verstile.client.util.DesktopOverlaySupport;
import com.verstile.client.util.ClientVersion;
import com.verstile.client.update.ChangelogManager;
import com.verstile.client.friend.AutoTeammateManager;
import com.verstile.client.friend.FriendManager;
import com.verstile.client.gui.CrashReportScreen;
import com.verstile.client.update.CrashReportManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class VerstileClient implements ClientModInitializer {
    private static final String HEADLESS_ARGUMENT = "-Djava.awt.headless=false";
    private static final Path HEADLESS_IGNORE_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("verstile").resolve("ignore-headless-warning");
    private boolean wasGuiKeyPressed = false;
    private final Map<Module, Boolean> keyStateMap = new HashMap<>();
    private int autoSaveTicks;
    private boolean desktopOverlaysAvailable;
    private boolean headlessRestartNoticePending;
    private boolean changelogChecked;
    private boolean previousMiddleClick;
    private boolean crashReportChecked;

    @Override
    public void onInitializeClient() {
        // Minecraft/Fabric development launches can inherit java.awt.headless=true.
        // Both the capture-excluded ClickGUI and the tracer overlay are real native
        // desktop windows, so opt into the Windows desktop toolkit before AWT is
        // initialized for the first time.
        System.setProperty("java.awt.headless", "false");
        System.out.println("[Verstile] Initializing Verstile Client for Fabric 1.21.11...");
        desktopOverlaysAvailable = DesktopOverlaySupport.isAvailable();
        headlessRestartNoticePending = !desktopOverlaysAvailable && !Files.isRegularFile(HEADLESS_IGNORE_FILE);
        System.out.println("[Verstile] Desktop overlays available: " + desktopOverlaysAvailable);
        ModuleManager.getInstance();
        AutoConfig.load();
        CrashReportManager.beginSession();
        // Large modpacks may initialize AWT in headless mode before Verstile.
        // Never leave a persisted INJECTION selection that cannot open.
        if (!desktopOverlaysAvailable && GuiMode.selected() == GuiMode.INJECTION) {
            GuiMode.select(GuiMode.CLIENT);
            AutoConfig.save();
            System.out.println("[Verstile] Injection unavailable; CLIENT fallback persisted.");
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("velocity")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("Usage: /velocity issue <message>"));
                            return 0;
                        })
                        .then(ClientCommandManager.argument("report", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String report = StringArgumentType.getString(context, "report");
                                    Module remote = ModuleManager.getInstance().getModuleByName("RemoteUpdate");
                                    if (remote instanceof RemoteUpdate updater) {
                                        updater.reportVelocity(report);
                                        context.getSource().sendFeedback(Component.literal("Velocity report queued."));
                                    }
                                    return 1;
                                })));
            dispatcher.register(ClientCommandManager.literal("verstile_ignore_headless")
                    .executes(context -> {
                        headlessRestartNoticePending = false;
                        try {
                            Files.createDirectories(HEADLESS_IGNORE_FILE.getParent());
                            Files.writeString(HEADLESS_IGNORE_FILE, "ignored", StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                            context.getSource().sendFeedback(Component.literal("Verstile headless warning ignored."));
                        } catch (Exception error) {
                            context.getSource().sendError(Component.literal("Could not save Ignore preference."));
                        }
                        return 1;
                    }));
            dispatcher.register(ClientCommandManager.literal("friend")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal("Friends: "
                                + String.join(", ", FriendManager.list())));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("list").executes(context -> {
                        context.getSource().sendFeedback(Component.literal("Friends: "
                                + String.join(", ", FriendManager.list())));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                boolean added = FriendManager.add(name);
                                context.getSource().sendFeedback(Component.literal(added ? "Friend added: " + name
                                        : "Could not add friend: " + name));
                                return added ? 1 : 0;
                            })))
                    .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                boolean removed = FriendManager.remove(name);
                                context.getSource().sendFeedback(Component.literal(removed ? "Friend removed: " + name
                                        : "Manual friend not found: " + name));
                                return removed ? 1 : 0;
                            }))));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AutoConfig.save());
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() == null) return;

            if (headlessRestartNoticePending && client.player != null) {
                headlessRestartNoticePending = false;
                Component warning = Component.literal("[Verstile] Disabling Headless Please Restart\n")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("[Copy Argument]")
                                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.CopyToClipboard(HEADLESS_ARGUMENT))))
                        .append(Component.literal("  "))
                        .append(Component.literal("[Ignore]")
                                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.RunCommand("/verstile_ignore_headless"))));
                client.player.displayClientMessage(warning, false);
            }

            // Writing the complete profile every second caused regular frame-time
            // spikes on slower disks. Thirty seconds is still frequent enough,
            // and the shutdown hook always writes the final state.
            if (++autoSaveTicks >= 600) {
                autoSaveTicks = 0;
                AutoConfig.save();
            }

            long windowHandle = client.getWindow().handle();

            boolean middleClick = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            if (middleClick && !previousMiddleClick && client.screen == null
                    && client.crosshairPickEntity instanceof net.minecraft.world.entity.player.Player player
                    && player != client.player) {
                boolean nowFriend = FriendManager.toggle(player.getScoreboardName());
                if (client.player != null) client.player.displayClientMessage(Component.literal("§d[Friends] §f"
                        + player.getScoreboardName() + (nowFriend ? " added" : " removed")), false);
            }
            previousMiddleClick = middleClick;

            // Toggle ClickGUI Screen on Right Shift
            boolean guiKeyState = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean verstileScreen = client.screen instanceof VerstileGuiScreen
                    || client.screen instanceof com.verstile.client.gui.FriendsScreen;
            if (guiKeyState && !wasGuiKeyPressed && !verstileScreen) {
                openClickGui(client);
            }
            wasGuiKeyPressed = guiKeyState;

            // If Swing/JNA cannot create a native overlay, fall back to the
            // original in-game screen instead of leaving fullscreen or focus
            // in a broken state.
            if (ExternalClickGui.getInstance().consumeFallbackRequest() && client.screen == null) {
                openFallbackGui(client);
            }

            // Handle per-module keybind toggling
            if (client.screen == null && client.player != null) {
                for (Module mod : ModuleManager.getInstance().getModules()) {
                    int key = mod.getKeyBind();
                    if (key != 0) {
                        boolean isPressed = key < 0
                                ? GLFW.glfwGetMouseButton(windowHandle, -key - 1) == GLFW.GLFW_PRESS
                                : GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
                        boolean wasPressed = keyStateMap.getOrDefault(mod, false);

                        if (mod.isHoldBind()) {
                            mod.setEnabled(isPressed);
                        } else if (isPressed && !wasPressed) {
                            mod.toggle();
                            System.out.println("[Verstile] Toggled " + mod.getName() + " -> " + (mod.isEnabled() ? "ENABLED" : "DISABLED"));
                        }
                        keyStateMap.put(mod, isPressed);
                    }
                }
            }

            // Tick active modules
            if (client.player != null && client.level != null) {
                AutoTeammateManager.tick(client);
                ModuleManager.getInstance().onTick();
            } else {
                // Remote updates must also be checked from the title screen.
                // Older builds only reached the updater after joining a world,
                // which made a normal restart appear to do nothing.
                ModuleManager.getInstance().onBackgroundTick();
            }

            // Give the updater first chance to show an update/restart prompt. If
            // it leaves the title screen untouched, show this version's notes
            // exactly once and remember the acknowledgement in config/verstile.
            if (!crashReportChecked && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                crashReportChecked = true;
                Path pendingCrash = CrashReportManager.pendingReport();
                if (pendingCrash != null) {
                    net.minecraft.client.gui.screens.Screen parent = client.screen;
                    client.setScreen(new CrashReportScreen(parent, pendingCrash, report -> {
                        Module remote = ModuleManager.getInstance().getModuleByName("RemoteUpdate");
                        if (remote instanceof RemoteUpdate updater) updater.reportCrash(report);
                        CrashReportManager.markHandled(report);
                    }, CrashReportManager::markHandled));
                    return;
                }
            }
            if (!changelogChecked && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                changelogChecked = true;
                if (ChangelogManager.shouldShow()) {
                    net.minecraft.client.gui.screens.Screen parent = client.screen;
                    client.setScreen(new ChangelogScreen(parent, ClientVersion.current(),
                            ChangelogManager.lines(), ChangelogManager::markSeen));
                }
            }
        });

        // External render modules publish their data once per client tick and
        // interpolate inside their transparent windows. Publishing again from
        // HudRenderCallback duplicated entity scans at the game's full FPS.
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            ModuleManager.getInstance().renderWorld();
        });

        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> {
            ModuleManager.getInstance().onRender();
            if (net.minecraft.client.Minecraft.getInstance().screen == null) {
                ModuleManager.getInstance().renderHud(graphics);
            }
        });
    }

    private void openClickGui(net.minecraft.client.Minecraft client) {
        // Eğer menü ekranı açıksa kapat ki GUI'nin önünde kalmasın
        boolean onMenu = client.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                || client.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
        if (onMenu) client.setScreen(null);

        if (GuiMode.selected() == GuiMode.INJECTION && desktopOverlaysAvailable) {
            ExternalClickGui.getInstance().toggle();
        } else if (GuiMode.selected() == GuiMode.INJECTION) {
            GuiMode.select(GuiMode.CLIENT);
            AutoConfig.save();
            openFallbackGui(client);
        } else {
            client.setScreen(new VerstileGuiScreen());
        }
    }

    private void openFallbackGui(net.minecraft.client.Minecraft client) {
        client.setScreen(new VerstileGuiScreen());
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("§b[Verstile] §fFallback GUI activated"), false);
        } else {
            System.out.println("[Verstile] Fallback GUI activated");
        }
    }
}
