package com.verstile.client.overlay;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.verstile.client.util.DesktopOverlaySupport;

/** Standalone transparent radar window; nothing is drawn into Minecraft's framebuffer. */
public final class ExternalRadarOverlay {
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int WDA_NONE = 0x00000000;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile OverlayWindow window;

    private interface CaptureApi extends StdCallLibrary {
        CaptureApi INSTANCE = Native.load("user32", CaptureApi.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean SetWindowDisplayAffinity(HWND hwnd, int affinity);
    }

    public record Target(double dx, double dz, double distance, String name) {}

    public record Frame(int gameX, int gameY, int gameWidth, int gameHeight,
                        int offsetX, int offsetY, int size, double range,
                        float opacity, float yaw, boolean showNames,
                        boolean captureProtection, int alignmentSeconds,
                        List<Target> targets) {}

    public void start() {
        active.set(true);
        if (!DesktopOverlaySupport.isAvailable()) {
            latest.set(null);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (window != null) return;
            window = new OverlayWindow();
            window.setVisible(false);
        });
    }

    public void stop() {
        active.set(false);
        latest.set(null);
        if (!DesktopOverlaySupport.isAvailable()) { window = null; return; }
        SwingUtilities.invokeLater(() -> {
            if (window != null) window.setVisible(false);
        });
    }

    public void hide() { latest.set(null); }
    public void update(Frame frame) {
        if (active.get()) latest.set(frame);
    }

    private final class OverlayWindow extends JWindow {
        private final Timer repaintTimer;
        private long nextAlignmentAt;
        private int lastSize = -1;
        private int lastOffsetX = Integer.MIN_VALUE;
        private int lastOffsetY = Integer.MIN_VALUE;
        private boolean lastCaptureProtection;
        private boolean flagsReady;

        private OverlayWindow() {
            setBackground(new Color(0, 0, 0, 0));
            setAlwaysOnTop(true);
            setFocusableWindowState(false);
            setAutoRequestFocus(false);
            repaintTimer = new Timer(16, event -> syncAndRepaint());
            repaintTimer.setCoalesce(true);
            repaintTimer.start();
        }

        private void syncAndRepaint() {
            if (!active.get()) {
                if (isVisible()) setVisible(false);
                return;
            }
            Frame frame = latest.get();
            if (frame == null || frame.gameWidth <= 0 || frame.gameHeight <= 0) {
                if (isVisible()) setVisible(false);
                return;
            }

            long now = System.currentTimeMillis();
            int maximum = Math.max(70, Math.min(frame.gameWidth, frame.gameHeight) - 12);
            int safeSize = Math.max(70, Math.min(frame.size, maximum));
            boolean editorMoved = frame.offsetX != lastOffsetX || frame.offsetY != lastOffsetY;
            if (now >= nextAlignmentAt || safeSize != lastSize || editorMoved) {
                int maxX = Math.max(0, frame.gameWidth - safeSize);
                int maxY = Math.max(0, frame.gameHeight - safeSize);
                int x = frame.gameX + Math.max(0, Math.min(maxX, frame.offsetX));
                int y = frame.gameY + Math.max(0, Math.min(maxY, frame.offsetY));
                setBounds(x, y, safeSize, safeSize);
                lastSize = safeSize;
                lastOffsetX = frame.offsetX;
                lastOffsetY = frame.offsetY;
                nextAlignmentAt = now + Math.max(1, frame.alignmentSeconds) * 1000L;
            }

            if (!isVisible()) {
                setVisible(true);
                flagsReady = false;
            }
            if (!flagsReady || frame.captureProtection != lastCaptureProtection) {
                applyWindowFlags(frame.captureProtection);
                flagsReady = true;
                lastCaptureProtection = frame.captureProtection;
            }
            repaint();
        }

        private void applyWindowFlags(boolean captureProtection) {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
            try {
                HWND hwnd = new HWND(Native.getComponentPointer(this));
                int styles = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
                styles |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW;
                User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, styles);
                CaptureApi.INSTANCE.SetWindowDisplayAffinity(hwnd,
                        captureProtection ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE);
            } catch (Throwable error) {
                System.err.println("[Verstile] Radar window flags unavailable: " + error.getMessage());
            }
        }

        @Override public void dispose() {
            repaintTimer.stop();
            super.dispose();
        }

        @Override public void paint(Graphics graphics) {
            super.paint(graphics);
            if (!active.get()) return;
            Frame frame = latest.get();
            if (frame == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setComposite(AlphaComposite.SrcOver);

                int size = getWidth();
                int center = size / 2;
                int margin = 5;
                int diameter = size - margin * 2;
                float alpha = Math.max(0.15f, Math.min(1.0f, frame.opacity));
                g.setColor(new Color(8 / 255f, 10 / 255f, 15 / 255f, alpha * 0.78f));
                g.fill(new Ellipse2D.Double(margin, margin, diameter, diameter));
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(new Color(0f, 1f, 0.80f, alpha * 0.30f));
                g.draw(new Ellipse2D.Double(center - diameter * 0.25, center - diameter * 0.25,
                        diameter * 0.5, diameter * 0.5));
                g.draw(new Line2D.Double(center, margin + 4, center, size - margin - 4));
                g.draw(new Line2D.Double(margin + 4, center, size - margin - 4, center));
                g.setStroke(new BasicStroke(2.2f));
                g.setColor(new Color(0f, 1f, 0.80f, alpha));
                g.draw(new Ellipse2D.Double(margin, margin, diameter, diameter));
                g.fillPolygon(new int[]{center, center - 5, center + 5},
                        new int[]{margin + 3, margin + 13, margin + 13}, 3);

                double radians = Math.toRadians(frame.yaw);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double usableRadius = diameter * 0.5 - 8.0;
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, size / 16)));
                for (Target target : frame.targets) {
                    double rotatedX = -(target.dx * cos + target.dz * sin);
                    double rotatedY = -(target.dz * cos - target.dx * sin);
                    int x = center + (int) Math.round(rotatedX / frame.range * usableRadius);
                    int y = center + (int) Math.round(rotatedY / frame.range * usableRadius);
                    double fromCenter = Math.hypot(x - center, y - center);
                    if (fromCenter > usableRadius && fromCenter > 0.0) {
                        x = center + (int) Math.round((x - center) / fromCenter * usableRadius);
                        y = center + (int) Math.round((y - center) / fromCenter * usableRadius);
                    }
                    g.setColor(new Color(0f, 0f, 0f, alpha * 0.75f));
                    g.fillOval(x - 4, y - 4, 8, 8);
                    g.setColor(new Color(1f, 0.20f, 0.28f, alpha));
                    g.fillOval(x - 3, y - 3, 6, 6);
                    if (frame.showNames && size >= 120) {
                        String label = target.name + " " + Math.round(target.distance) + "m";
                        g.setColor(new Color(1f, 1f, 1f, alpha));
                        g.drawString(label, x + 5, y - 4);
                    }
                }
                g.setColor(new Color(0.25f, 1f, 0.35f, alpha));
                g.fillOval(center - 3, center - 3, 6, 6);
            } finally {
                g.dispose();
            }
        }
    }
}
