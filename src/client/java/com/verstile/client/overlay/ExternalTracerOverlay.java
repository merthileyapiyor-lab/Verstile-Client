package com.verstile.client.overlay;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import com.verstile.client.util.DesktopOverlaySupport;

/** Transparent 60 FPS native overlay; no tracer geometry is written to Minecraft's framebuffer. */
public final class ExternalTracerOverlay {
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private volatile OverlayWindow window;

    public void start() {
        if (!DesktopOverlaySupport.isAvailable()) {
            latest.set(null);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (window != null) return;
            window = new OverlayWindow();
            window.setVisible(true);
            window.applyClickThroughFlags();
        });
    }

    public void stop() {
        latest.set(null);
        if (!DesktopOverlaySupport.isAvailable()) { window = null; return; }
        SwingUtilities.invokeLater(() -> {
            if (window != null) { window.dispose(); window = null; }
        });
    }

    public void hide() { latest.set(null); }
    public void update(Frame frame) { latest.set(frame); }

    public record Target(int id, double x, double y, double z, double width, double height,
                         double distance, String name, int rgb) {}
    public record Frame(int x, int y, int width, int height,
                        double cameraX, double cameraY, double cameraZ,
                        float yaw, float pitch, int fov,
                        float lineWidth, float opacity, float smoothing,
                        boolean labels, boolean fromCrosshair, boolean boxes3d, boolean tracers,
                        List<Target> targets) {}

    private static final class SmoothTarget {
        final int id;
        double x, y, z, width, height, distance;
        final String name;
        int rgb;
        SmoothTarget(Target target) {
            id = target.id;
            x = target.x; y = target.y; z = target.z; width = target.width;
            height = target.height; distance = target.distance; name = target.name; rgb = target.rgb;
        }
        void approach(Target target, double amount) {
            x += (target.x - x) * amount;
            y += (target.y - y) * amount;
            z += (target.z - z) * amount;
            width += (target.width - width) * amount;
            height += (target.height - height) * amount;
            distance += (target.distance - distance) * amount;
            rgb = target.rgb;
        }
    }

    private final class OverlayWindow extends JWindow {
        private final Timer repaintTimer;
        private final Map<Integer, SmoothTarget> smoothedTargets = new HashMap<>();
        private boolean cameraReady;
        private double cameraX, cameraY, cameraZ, cameraYaw, cameraPitch;

        private OverlayWindow() {
            setBackground(new Color(0, 0, 0, 0));
            setAlwaysOnTop(true);
            setFocusableWindowState(false);
            setAutoRequestFocus(false);
            repaintTimer = new Timer(16, event -> syncAndRepaint());
            repaintTimer.setCoalesce(true);
            repaintTimer.start();
        }

        private void applyClickThroughFlags() {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            HWND hwnd = new HWND(Native.getComponentPointer(this));
            int styles = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
            styles |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW;
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, styles);
        }

        private void syncAndRepaint() {
            Frame frame = latest.get();
            if (frame == null || frame.width <= 0 || frame.height <= 0) {
                smoothedTargets.clear();
                cameraReady = false;
                if (isVisible()) setVisible(false);
                return;
            }
            if (!isVisible()) { setVisible(true); applyClickThroughFlags(); }
            if (getX() != frame.x || getY() != frame.y || getWidth() != frame.width || getHeight() != frame.height) {
                setBounds(frame.x, frame.y, frame.width, frame.height);
            }
            smooth(frame);
            repaint();
        }

        private void smooth(Frame frame) {
            double amount = Math.max(0.04, Math.min(1.0, frame.smoothing));
            if (!cameraReady) {
                cameraX = frame.cameraX; cameraY = frame.cameraY; cameraZ = frame.cameraZ;
                cameraYaw = frame.yaw; cameraPitch = frame.pitch; cameraReady = true;
            } else {
                cameraX += (frame.cameraX - cameraX) * amount;
                cameraY += (frame.cameraY - cameraY) * amount;
                cameraZ += (frame.cameraZ - cameraZ) * amount;
                cameraYaw += wrapDegrees(frame.yaw - cameraYaw) * amount;
                cameraPitch += (frame.pitch - cameraPitch) * amount;
            }
            Set<Integer> live = new HashSet<>();
            for (Target target : frame.targets) {
                live.add(target.id);
                smoothedTargets.computeIfAbsent(target.id, ignored -> new SmoothTarget(target)).approach(target, amount);
            }
            smoothedTargets.keySet().removeIf(id -> !live.contains(id));
        }

        @Override public void dispose() { repaintTimer.stop(); super.dispose(); }

        @Override
        public void paint(Graphics graphics) {
            super.paint(graphics);
            Frame frame = latest.get();
            if (frame == null || !cameraReady) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setComposite(AlphaComposite.SrcOver);
                Projection projection = new Projection(frame.width, frame.height, frame.fov,
                        cameraX, cameraY, cameraZ, cameraYaw, cameraPitch);
                double startX = frame.width * 0.5;
                double startY = frame.fromCrosshair ? frame.height * 0.5 : frame.height - 3.0;
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

                for (SmoothTarget target : smoothedTargets.values()) {
                    Point2D.Double center = projection.project(target.x, target.y + target.height * 0.52, target.z);
                    if (center == null || center.x < -160 || center.x > frame.width + 160 || center.y < -160 || center.y > frame.height + 160) continue;
                    Color color;
                    if (target.rgb >= 0) {
                        color = new Color((target.rgb >> 16) & 0xFF, (target.rgb >> 8) & 0xFF,
                                target.rgb & 0xFF, Math.round(frame.opacity * 255.0f));
                    } else {
                        float proximity = (float) Math.max(0.0, Math.min(1.0, 1.0 - target.distance / 96.0));
                        color = new Color(1.0f, 0.22f + 0.68f * (1.0f - proximity), 0.18f, frame.opacity);
                    }

                    if (frame.tracers) {
                        g.setStroke(new BasicStroke(frame.lineWidth + 2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(new Color(0f, 0f, 0f, frame.opacity * 0.5f));
                        g.draw(new Line2D.Double(startX, startY, center.x, center.y));
                        g.setStroke(new BasicStroke(frame.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(color);
                        g.draw(new Line2D.Double(startX, startY, center.x, center.y));
                        g.fillOval((int) center.x - 3, (int) center.y - 3, 6, 6);
                    }

                    Point2D.Double labelPoint = center;
                    if (frame.boxes3d) {
                        Point2D.Double top = draw3dBox(g, projection, target, color, frame.opacity, frame.lineWidth);
                        if (top != null) labelPoint = top;
                    }
                    if (frame.labels) drawLabel(g, target, (int) labelPoint.x, (int) labelPoint.y, frame.opacity);
                }
            } finally { g.dispose(); }
        }

        private Point2D.Double draw3dBox(Graphics2D g, Projection projection, SmoothTarget target,
                                          Color color, float opacity, float lineWidth) {
            double half = Math.max(0.2, target.width * 0.56);
            double y0 = target.y, y1 = target.y + target.height;
            double[][] world = {
                    {target.x - half, y0, target.z - half}, {target.x + half, y0, target.z - half},
                    {target.x + half, y0, target.z + half}, {target.x - half, y0, target.z + half},
                    {target.x - half, y1, target.z - half}, {target.x + half, y1, target.z - half},
                    {target.x + half, y1, target.z + half}, {target.x - half, y1, target.z + half}
            };
            Point2D.Double[] point = new Point2D.Double[8];
            for (int i = 0; i < 8; i++) {
                point[i] = projection.project(world[i][0], world[i][1], world[i][2]);
                if (point[i] == null) return null;
            }

            Polygon topFace = polygon(point[4], point[5], point[6], point[7]);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(opacity * 34)));
            g.fillPolygon(topFace);
            int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
            g.setStroke(new BasicStroke(lineWidth + 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0, 0, 0, Math.round(opacity * 150)));
            for (int[] edge : edges) g.draw(new Line2D.Double(point[edge[0]], point[edge[1]]));
            g.setStroke(new BasicStroke(Math.max(1.0f, lineWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(color);
            for (int[] edge : edges) g.draw(new Line2D.Double(point[edge[0]], point[edge[1]]));
            double topX = 0, topY = Double.MAX_VALUE;
            for (int i = 4; i < 8; i++) { topX += point[i].x; topY = Math.min(topY, point[i].y); }
            return new Point2D.Double(topX / 4.0, topY);
        }

        private Polygon polygon(Point2D.Double... points) {
            Polygon polygon = new Polygon();
            for (Point2D.Double point : points) polygon.addPoint((int) Math.round(point.x), (int) Math.round(point.y));
            return polygon;
        }

        private void drawLabel(Graphics2D g, SmoothTarget target, int x, int y, float opacity) {
            String text = target.name + "  " + Math.round(target.distance) + "m";
            int textWidth = g.getFontMetrics().stringWidth(text);
            int left = x - textWidth / 2 - 6, top = y - 24;
            g.setColor(new Color(8 / 255f, 10 / 255f, 14 / 255f, opacity * 0.84f));
            g.fillRoundRect(left, top, textWidth + 12, 18, 8, 8);
            g.setColor(new Color(1f, 1f, 1f, opacity));
            g.drawString(text, x - textWidth / 2, top + 13);
        }
    }

    private static final class Projection {
        final int width, height;
        final double cameraX, cameraY, cameraZ, sinYaw, cosYaw, sinPitch, cosPitch, focal;
        Projection(int width, int height, int fov, double cameraX, double cameraY, double cameraZ, double yaw, double pitch) {
            this.width = width; this.height = height; this.cameraX = cameraX; this.cameraY = cameraY; this.cameraZ = cameraZ;
            double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
            sinYaw = Math.sin(yawRadians); cosYaw = Math.cos(yawRadians);
            sinPitch = Math.sin(pitchRadians); cosPitch = Math.cos(pitchRadians);
            focal = (height * 0.5) / Math.tan(Math.toRadians(Math.max(30, fov)) * 0.5);
        }
        Point2D.Double project(double x, double y, double z) {
            double dx = x - cameraX, dy = y - cameraY, dz = z - cameraZ;
            // Minecraft camera yaw: 0 = South (+Z), 90 = West (-X), 180 = North (-Z), 270 = East (+X)
            double right = -dx * cosYaw + dz * sinYaw;
            double forwardFlat = -dx * sinYaw - dz * cosYaw;
            double up = dy * cosPitch + forwardFlat * sinPitch;
            double forward = forwardFlat * cosPitch - dy * sinPitch;
            if (forward <= 0.08) return null;
            return new Point2D.Double(width * 0.5 + right * focal / forward, height * 0.5 - up * focal / forward);
        }
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }
}
