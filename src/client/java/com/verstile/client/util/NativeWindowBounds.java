package com.verstile.client.util;

import com.sun.jna.Pointer;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.Locale;

/** Resolves the exact on-screen Minecraft client area without native borders/title bar. */
public final class NativeWindowBounds {
    private NativeWindowBounds() {}

    private interface ClientAreaApi extends StdCallLibrary {
        ClientAreaApi INSTANCE = Native.load("user32", ClientAreaApi.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean GetClientRect(HWND hwnd, RECT rect);
        boolean ClientToScreen(HWND hwnd, POINT point);
    }

    public record Bounds(int x, int y, int width, int height) {}

    public static Bounds minecraftClientArea(Minecraft client) {
        if (client.getWindow() == null) return new Bounds(0, 0, 1, 1);

        int width = Math.max(1, client.getWindow().getScreenWidth());
        int height = Math.max(1, client.getWindow().getScreenHeight());
        int[] x = new int[1];
        int[] y = new int[1];
        GLFW.glfwGetWindowPos(client.getWindow().handle(), x, y);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return new Bounds(x[0], y[0], width, height);
        }

        try {
            long nativeHandle = GLFWNativeWin32.glfwGetWin32Window(client.getWindow().handle());
            HWND hwnd = new HWND(Pointer.createConstant(nativeHandle));
            RECT clientRect = new RECT();
            POINT origin = new POINT(0, 0);
            if (ClientAreaApi.INSTANCE.GetClientRect(hwnd, clientRect)
                    && ClientAreaApi.INSTANCE.ClientToScreen(hwnd, origin)) {
                int nativeWidth = clientRect.right - clientRect.left;
                int nativeHeight = clientRect.bottom - clientRect.top;
                if (nativeWidth > 0 && nativeHeight > 0) {
                    return new Bounds(origin.x, origin.y, nativeWidth, nativeHeight);
                }
            }
        } catch (Throwable ignored) {
            // GLFW coordinates remain a safe fallback for unusual launchers.
        }
        return new Bounds(x[0], y[0], width, height);
    }
}
