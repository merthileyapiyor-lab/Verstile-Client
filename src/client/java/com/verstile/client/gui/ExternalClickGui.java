package com.verstile.client.gui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.profile.ProfileManager;
import com.verstile.client.profile.AutoConfig;
import com.verstile.client.friend.FriendManager;
import com.verstile.client.friend.AutoTeammateManager;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.setting.Setting;
import com.verstile.client.util.NativeWindowBounds;
import com.verstile.client.util.DesktopOverlaySupport;
import com.verstile.client.util.ClientVersion;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.IllegalComponentStateException;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Container;
import java.awt.AWTEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExternalClickGui {
    private static final ExternalClickGui INSTANCE = new ExternalClickGui();
    private static final Color BACKGROUND = new Color(8, 9, 13, 238);
    private static final Color PANEL = new Color(15, 16, 22, 235);
    private static final Color ROW = new Color(24, 25, 32, 225);
    private static final Color ROW_HOVER = new Color(48, 40, 61, 240);
    private static final Color PURPLE = new Color(151, 47, 224);
    private static final Color PURPLE_LIGHT = new Color(194, 105, 255);
    private static final Color TEXT = new Color(230, 230, 235);
    private static final Color MUTED = new Color(145, 145, 160);
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private JFrame frame;
    private JPanel centerCards;
    private java.awt.CardLayout centerLayout;
    private JPanel inspector;
    private JTextField searchField;
    private final DefaultListModel<Module> searchModel = new DefaultListModel<>();
    private JList<Module> searchList;
    private final List<JList<Module>> categoryLists = new ArrayList<>();
    private Module selectedModule;
    private Module bindingModule;
    private JLabel footerStatus;
    private KeyEventDispatcher keyDispatcher;
    private AWTEventListener bindMouseDispatcher;
    private Point dragOffset;
    private FakeCursorPane fakeCursorPane;
    private boolean restoreFullscreenOnClose;
    private final AtomicBoolean fallbackRequested = new AtomicBoolean();

    private interface CaptureApi extends StdCallLibrary {
        CaptureApi INSTANCE = Native.load("user32", CaptureApi.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean SetWindowDisplayAffinity(HWND hwnd, int affinity);
    }

    public static ExternalClickGui getInstance() {
        return INSTANCE;
    }

    public void toggle() {
        System.setProperty("java.awt.headless", "false");
        if (!DesktopOverlaySupport.isAvailable()) {
            fallbackRequested.set(true);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                if (frame != null && frame.isDisplayable()) close();
                else requestOpen();
            } catch (HeadlessException error) {
                // Never take the client down if an unusual launcher forces a
                // headless JVM. The normal Windows/Fabric path is made desktop
                // capable in VerstileClient before AWT is initialized.
                System.err.println("[Verstile] External GUI unavailable: " + error.getMessage());
                fallbackRequested.set(true);
            } catch (Throwable error) {
                System.err.println("[Verstile] External GUI failed safely: " + error);
                error.printStackTrace();
                fallbackRequested.set(true);
            }
        });
    }

    public boolean consumeFallbackRequest() {
        return fallbackRequested.compareAndSet(true, false);
    }

    public boolean isOpen() {
        return frame != null && frame.isDisplayable();
    }

    private void requestOpen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            boolean wasFullscreen = mc.getWindow() != null && mc.getWindow().isFullscreen();
            if (wasFullscreen) prepareWindowedOverlay(mc);
            SwingUtilities.invokeLater(() -> open(mc, wasFullscreen));
        });
    }

    private void open(Minecraft mc, boolean wasFullscreen) {
        restoreFullscreenOnClose = wasFullscreen;
        frame = new JFrame("Verstile Client Control Center");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setBackground(BACKGROUND);
        frame.setContentPane(buildRoot());
        setGameWindowBounds(mc);
        try {
            frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 30, 30));
        } catch (Throwable ignored) { }
        installKeyboardDispatcher();
        // Create the native peer first, apply WDA_EXCLUDEFROMCAPTURE, and only
        // then show it. This prevents a one-frame/one-second AnyDesk leak.
        frame.addNotify();
        boolean protectedFromCapture = applyCaptureExclusion();
        frame.setVisible(true);
        installFakeCursor();
        footerStatus.setText(protectedFromCapture
                ? "STREAM-PROOF ACTIVE  •  Left: toggle  Right: settings  Middle: bind"
                : "Capture exclusion unavailable  •  Left: toggle  Right: settings  Middle: bind");
        frame.requestFocus();
    }

    private void prepareWindowedOverlay(Minecraft mc) {
        if (mc.getWindow() == null) return;
        mc.getWindow().toggleFullScreen();
        long handle = mc.getWindow().handle();
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            GLFW.glfwSetWindowPos(handle, 0, 0);
            GLFW.glfwSetWindowSize(handle, mode.width(), mode.height());
        }
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createLineBorder(new Color(75, 35, 105), 1));
        root.add(buildTopBar(), BorderLayout.NORTH);

        centerLayout = new java.awt.CardLayout();
        centerCards = new JPanel(centerLayout);
        centerCards.setOpaque(false);
        centerCards.add(buildModulesPage(), "modules");
        centerCards.add(buildProfilesPage(), "profiles");
        centerCards.add(buildFriendsPage(), "friends");
        restoreSelectedModule();
        root.add(centerCards, BorderLayout.CENTER);

        footerStatus = new JLabel(" ");
        footerStatus.setOpaque(true);
        footerStatus.setBackground(new Color(5, 6, 9));
        footerStatus.setForeground(MUTED);
        footerStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        root.add(footerStatus, BorderLayout.SOUTH);
        return root;
    }

    private void restoreSelectedModule() {
        if (selectedModule == null) return;
        for (JList<Module> list : categoryLists) {
            for (int index = 0; index < list.getModel().getSize(); index++) {
                if (list.getModel().getElementAt(index) != selectedModule) continue;
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
                showInspector(selectedModule);
                return;
            }
        }
        // A profile or a future module reload can replace module instances.
        // Restore by stable module name when identity no longer matches.
        Module restored = ModuleManager.getInstance().getModuleByName(selectedModule.getName());
        if (restored != null) {
            selectedModule = restored;
            showInspector(restored);
        }
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(7, 8, 11));
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, PURPLE));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 8));
        JLabel title = new JLabel("VERSTILE  •  CLIENT CONTROL");
        title.setForeground(TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        JLabel version = new JLabel("v" + ClientVersion.current());
        version.setForeground(MUTED);
        version.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        brand.add(title);
        brand.add(version);
        top.add(brand, BorderLayout.WEST);

        MouseAdapter windowDrag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || frame == null) return;
                Point pointer = event.getLocationOnScreen();
                Point window = frame.getLocationOnScreen();
                dragOffset = new Point(pointer.x - window.x, pointer.y - window.y);
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (dragOffset == null || frame == null) return;
                frame.setLocation(event.getXOnScreen() - dragOffset.x, event.getYOnScreen() - dragOffset.y);
            }

            @Override public void mouseReleased(MouseEvent event) {
                dragOffset = null;
            }
        };
        top.addMouseListener(windowDrag);
        top.addMouseMotionListener(windowDrag);
        title.addMouseListener(windowDrag);
        title.addMouseMotionListener(windowDrag);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        JButton modules = navButton("MODULES");
        JButton profiles = navButton("PROFILES");
        JButton friends = navButton("FRIENDS");
        JButton guiMode = navButton("CLIENT / INJECTION");
        JButton close = navButton("✕");
        modules.addActionListener(event -> centerLayout.show(centerCards, "modules"));
        profiles.addActionListener(event -> centerLayout.show(centerCards, "profiles"));
        friends.addActionListener(event -> centerLayout.show(centerCards, "friends"));
        guiMode.addActionListener(event -> switchToClientGui());
        close.addActionListener(event -> close());
        nav.add(modules);
        nav.add(profiles);
        nav.add(friends);
        nav.add(guiMode);
        nav.add(close);
        top.add(nav, BorderLayout.EAST);
        return top;
    }

    private JPanel buildFriendsPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBackground(PANEL);
        page.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel help = new JLabel("FRIENDS  •  Combat modules never target names in this list");
        help.setForeground(TEXT);
        page.add(help, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        FriendManager.list().forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setBackground(ROW); list.setForeground(TEXT); list.setFixedCellHeight(28);
        page.add(scroll(list), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(6, 0));
        controls.setOpaque(false);
        JTextField name = new JTextField();
        styleTextField(name);
        name.setToolTipText("Minecraft username");
        JButton add = styledButton("ADD");
        JButton remove = styledButton("REMOVE SELECTED");
        JButton autoTeam = styledButton("AUTO TEAMMATE: " + (AutoTeammateManager.isEnabled() ? "ON" : "OFF") + "  •  BEDWARS");
        add.addActionListener(event -> {
            if (FriendManager.add(name.getText())) { model.addElement(name.getText().trim()); name.setText(""); }
        });
        remove.addActionListener(event -> {
            String selected = list.getSelectedValue();
            if (selected != null && FriendManager.remove(selected)) model.removeElement(selected);
        });
        autoTeam.addActionListener(event -> {
            AutoTeammateManager.toggle();
            autoTeam.setText("AUTO TEAMMATE: " + (AutoTeammateManager.isEnabled() ? "ON" : "OFF") + "  •  BEDWARS");
        });
        JPanel buttons = new JPanel(); buttons.setOpaque(false); buttons.add(add); buttons.add(remove); buttons.add(autoTeam);
        controls.add(name, BorderLayout.CENTER); controls.add(buttons, BorderLayout.EAST);
        page.add(controls, BorderLayout.SOUTH);
        return page;
    }

    private void switchToClientGui() {
        GuiMode.select(GuiMode.CLIENT);
        AutoConfig.save();
        close();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new VerstileGuiScreen()));
    }

    private JPanel buildModulesPage() {
        JPanel page = new JPanel(new BorderLayout(6, 0));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel columns = new JPanel(new GridLayout(1, Category.values().length, 4, 0));
        columns.setOpaque(false);
        categoryLists.clear();
        for (Category category : Category.values()) columns.add(buildCategoryColumn(category));

        JPanel side = buildSearchAndInspector();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, columns, side);
        split.setResizeWeight(0.78);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerLocation(0.78);
        page.add(split, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildCategoryColumn(Category category) {
        JPanel column = new JPanel(new BorderLayout());
        column.setBackground(PANEL);
        column.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, new Color(35, 36, 45)));
        JLabel header = new JLabel(category.getDisplayName().toUpperCase() + "  ▾", SwingConstants.CENTER);
        header.setOpaque(true);
        header.setBackground(PURPLE);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        header.setBorder(BorderFactory.createEmptyBorder(6, 3, 6, 3));
        column.add(header, BorderLayout.NORTH);

        DefaultListModel<Module> model = new DefaultListModel<>();
        ModuleManager.getInstance().getModulesByCategory(category).forEach(model::addElement);
        JList<Module> list = moduleList(model, false);
        categoryLists.add(list);
        JScrollPane scroll = scroll(list);
        column.add(scroll, BorderLayout.CENTER);
        return column;
    }

    private JPanel buildSearchAndInspector() {
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.setPreferredSize(new Dimension(285, 500));
        side.setBackground(PANEL);

        JPanel searchPanel = new JPanel(new BorderLayout(0, 4));
        searchPanel.setOpaque(false);
        JLabel title = new JLabel("SEARCH");
        title.setOpaque(true);
        title.setBackground(PURPLE);
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        searchPanel.add(title, BorderLayout.NORTH);
        searchField = new JTextField();
        styleTextField(searchField);
        searchField.setToolTipText("Search every module");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterSearch(); }
            public void removeUpdate(DocumentEvent e) { filterSearch(); }
            public void changedUpdate(DocumentEvent e) { filterSearch(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchList = moduleList(searchModel, true);
        JScrollPane results = scroll(searchList);
        results.setPreferredSize(new Dimension(270, 145));
        searchPanel.add(results, BorderLayout.SOUTH);
        side.add(searchPanel, BorderLayout.NORTH);

        inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.setBackground(PANEL);
        showInspector(null);
        side.add(scroll(inspector), BorderLayout.CENTER);
        return side;
    }

    private JList<Module> moduleList(DefaultListModel<Module> model, boolean showCategory) {
        JList<Module> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(PANEL);
        list.setForeground(TEXT);
        list.setFixedCellHeight(28);
        list.setCellRenderer(new ModuleRenderer(showCategory));
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Module module = list.getModel().getElementAt(index);
                if (SwingUtilities.isLeftMouseButton(event)) toggleModule(module);
                else if (SwingUtilities.isRightMouseButton(event)) selectModule(module);
                else if (SwingUtilities.isMiddleMouseButton(event)) beginBinding(module);
            }
        });
        return list;
    }

    private void toggleModule(Module module) {
        Minecraft.getInstance().execute(module::toggle);
        new Timer(80, event -> { repaintModuleLists(); ((Timer) event.getSource()).stop(); }).start();
    }

    private void selectModule(Module module) {
        selectedModule = module;
        showInspector(module);
    }

    private void beginBinding(Module module) {
        bindingModule = module;
        selectedModule = module;
        showInspector(module);
        footerStatus.setText("Press a key for " + module.getName() + "  •  ESC/BACKSPACE clears");
    }

    private void showInspector(Module module) {
        if (inspector == null) return;
        inspector.removeAll();
        JLabel heading = new JLabel(module == null ? "RIGHT CLICK A MODULE" : module.getName().toUpperCase());
        heading.setForeground(module != null && module.isEnabled() ? PURPLE_LIGHT : TEXT);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 15));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        inspector.add(heading);
        if (module == null) {
            JLabel hint = new JLabel("<html>Left click toggles.<br>Right click opens settings.<br>Middle click starts keybind.</html>");
            hint.setForeground(MUTED);
            hint.setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));
            inspector.add(hint);
        } else {
            JLabel description = new JLabel("<html><body style='width:230px'>" + escapeHtml(module.getDescription()) + "</body></html>");
            description.setForeground(MUTED);
            description.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
            description.setAlignmentX(Component.LEFT_ALIGNMENT);
            inspector.add(description);

            JButton bind = styledButton(bindingModule == module ? "PRESS A KEY..." : "BIND: " + keyLabel(module.getKeyBind()));
            bind.addActionListener(event -> beginBinding(module));
            bind.setAlignmentX(Component.LEFT_ALIGNMENT);
            inspector.add(bind);
            inspector.add(Box.createVerticalStrut(7));
            for (Setting<?> setting : module.getSettings()) inspector.add(settingControl(setting));
        }
        inspector.revalidate();
        inspector.repaint();
    }

    private JPanel settingControl(Setting<?> setting) {
        JPanel row = new JPanel(new BorderLayout(6, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        JLabel name = new JLabel(setting.getName());
        name.setForeground(TEXT);
        row.add(name, BorderLayout.WEST);
        if (setting instanceof BooleanSetting bool) {
            JCheckBox box = new JCheckBox();
            box.setOpaque(false);
            box.setSelected(bool.getValue());
            box.addActionListener(event -> bool.setValue(box.isSelected()));
            row.add(box, BorderLayout.EAST);
        } else if (setting instanceof ModeSetting mode) {
            JComboBox<String> combo = new JComboBox<>(mode.getModes().toArray(String[]::new));
            combo.setSelectedItem(mode.getValue());
            combo.setBackground(new Color(30, 22, 38));
            combo.setForeground(TEXT);
            combo.addActionListener(event -> mode.setValue((String) combo.getSelectedItem()));
            row.add(combo, BorderLayout.EAST);
        } else if (setting instanceof NumberSetting number) {
            JPanel sliderPanel = new JPanel(new BorderLayout(4, 0));
            sliderPanel.setOpaque(false);
            int sliderValue = (int) Math.round((number.getValue() - number.getMin()) / (number.getMax() - number.getMin()) * 1000.0);
            JSlider slider = new JSlider(0, 1000, Math.max(0, Math.min(1000, sliderValue)));
            slider.setOpaque(false);
            JLabel value = new JLabel(formatNumber(number.getValue()));
            value.setForeground(PURPLE_LIGHT);
            value.setPreferredSize(new Dimension(48, 18));
            value.setHorizontalAlignment(SwingConstants.RIGHT);
            slider.addChangeListener(event -> {
                double raw = number.getMin() + slider.getValue() / 1000.0 * (number.getMax() - number.getMin());
                number.setValueClamped(raw);
                value.setText(formatNumber(number.getValue()));
            });
            sliderPanel.add(slider, BorderLayout.CENTER);
            sliderPanel.add(value, BorderLayout.EAST);
            row.add(sliderPanel, BorderLayout.SOUTH);
        }
        return row;
    }

    private JPanel buildProfilesPage() {
        JPanel page = new JPanel(new BorderLayout(10, 10));
        page.setBackground(BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(35, 80, 35, 80));
        JLabel heading = new JLabel("PROFILES", SwingConstants.CENTER);
        heading.setForeground(PURPLE_LIGHT);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 25));
        page.add(heading, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> profiles = new JList<>(model);
        profiles.setBackground(PANEL);
        profiles.setForeground(TEXT);
        profiles.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        profiles.setFixedCellHeight(32);
        page.add(scroll(profiles), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        JTextField name = new JTextField("default", 18);
        styleTextField(name);
        JButton save = styledButton("SAVE / OVERWRITE");
        JButton load = styledButton("LOAD");
        JButton delete = styledButton("DELETE");
        JLabel status = new JLabel(" ");
        status.setForeground(MUTED);
        save.addActionListener(event -> {
            try { ProfileManager.save(name.getText()); refreshProfiles(model); status.setText("Saved: " + name.getText()); }
            catch (Exception error) { status.setText(error.getMessage()); }
        });
        load.addActionListener(event -> {
            String selected = profiles.getSelectedValue();
            if (selected == null) selected = name.getText();
            String profile = selected;
            Minecraft.getInstance().execute(() -> {
                try {
                    ProfileManager.load(profile);
                    SwingUtilities.invokeLater(() -> { status.setText("Loaded: " + profile); repaintModuleLists(); showInspector(selectedModule); });
                } catch (Exception error) { SwingUtilities.invokeLater(() -> status.setText(error.getMessage())); }
            });
        });
        delete.addActionListener(event -> {
            String selected = profiles.getSelectedValue();
            if (selected == null) return;
            try { ProfileManager.delete(selected); refreshProfiles(model); status.setText("Deleted: " + selected); }
            catch (Exception error) { status.setText(error.getMessage()); }
        });
        profiles.addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && profiles.getSelectedValue() != null) name.setText(profiles.getSelectedValue()); });
        actions.add(name);
        actions.add(save);
        actions.add(load);
        actions.add(delete);
        actions.add(status);
        page.add(actions, BorderLayout.SOUTH);
        refreshProfiles(model);
        return page;
    }

    private void refreshProfiles(DefaultListModel<String> model) {
        model.clear();
        try { ProfileManager.list().forEach(model::addElement); }
        catch (Exception ignored) {}
    }

    private void filterSearch() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        searchModel.clear();
        if (query.isEmpty()) return;
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.getName().toLowerCase(Locale.ROOT).contains(query)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(query)
                    || module.getCategory().getName().toLowerCase(Locale.ROOT).contains(query)) searchModel.addElement(module);
        }
    }

    private void installKeyboardDispatcher() {
        keyDispatcher = event -> {
            if (frame == null || !frame.isActive() || event.getID() != KeyEvent.KEY_PRESSED) return false;
            if (bindingModule != null) {
                int glfw = awtToGlfw(event.getKeyCode());
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE || event.getKeyCode() == KeyEvent.VK_BACK_SPACE || event.getKeyCode() == KeyEvent.VK_DELETE) glfw = 0;
                if (glfw != Integer.MIN_VALUE) bindingModule.setKeyBind(glfw);
                Module bound = bindingModule;
                bindingModule = null;
                footerStatus.setText("Bound " + bound.getName() + " to " + keyLabel(bound.getKeyBind()));
                showInspector(bound);
                repaintModuleLists();
                return true;
            }
            // ESC closes reliably. Treating Shift as close would also consume the
            // same physical Right-Shift press that just opened this native window.
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) { close(); return true; }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        bindMouseDispatcher = event -> {
            if (!(event instanceof MouseEvent mouse) || mouse.getID() != MouseEvent.MOUSE_PRESSED
                    || frame == null || !frame.isActive() || bindingModule == null) return;
            Component source = mouse.getComponent();
            if (source == null || !SwingUtilities.isDescendingFrom(source, frame)) return;
            bindingModule.setKeyBind(-Math.max(1, mouse.getButton()));
            Module bound = bindingModule;
            bindingModule = null;
            footerStatus.setText("Bound " + bound.getName() + " to " + keyLabel(bound.getKeyBind()));
            showInspector(bound);
            repaintModuleLists();
            mouse.consume();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(bindMouseDispatcher, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void close() {
        if (keyDispatcher != null) KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        if (bindMouseDispatcher != null) Toolkit.getDefaultToolkit().removeAWTEventListener(bindMouseDispatcher);
        keyDispatcher = null;
        bindMouseDispatcher = null;
        bindingModule = null;
        if (fakeCursorPane != null) fakeCursorPane.stop();
        fakeCursorPane = null;
        if (frame != null) frame.dispose();
        frame = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            long handle = mc.getWindow().handle();
            if (restoreFullscreenOnClose) {
                restoreFullscreenOnClose = false;
                mc.execute(() -> {
                    GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                    mc.getWindow().toggleFullScreen();
                    GLFW.glfwFocusWindow(handle);
                });
            } else {
                GLFW.glfwFocusWindow(handle);
            }
        }
    }

    private void setGameWindowBounds(Minecraft mc) {
        NativeWindowBounds.Bounds bounds = NativeWindowBounds.minecraftClientArea(mc);
        int gameWidth = Math.max(640, bounds.width());
        int gameHeight = Math.max(480, bounds.height());
        int gameX = bounds.x();
        int gameY = bounds.y();
        int width = Math.min(gameWidth - 24, Math.max(640, (int) Math.round(gameWidth * 0.66)));
        int height = Math.min(gameHeight - 36, Math.max(390, (int) Math.round(gameHeight * 0.66)));
        int x = gameX + (gameWidth - width) / 2;
        int y = gameY + (gameHeight - height) / 2;
        frame.setBounds(x, y, width, height);
    }

    private void installFakeCursor() {
        try {
            BufferedImage transparent = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Cursor hidden = Toolkit.getDefaultToolkit().createCustomCursor(transparent, new Point(0, 0), "verstile-hidden");
            applyCursorRecursively(frame, hidden);
            fakeCursorPane = new FakeCursorPane(hidden);
            frame.setGlassPane(fakeCursorPane);
            fakeCursorPane.setVisible(true);
            int centerX = frame.getX() + frame.getWidth() / 2;
            int centerY = frame.getY() + frame.getHeight() / 2;
            new Robot().mouseMove(centerX, centerY);
        } catch (Throwable error) {
            System.err.println("[Verstile] Fake GUI cursor unavailable: " + error.getMessage());
        }
    }

    private void applyCursorRecursively(Component component, Cursor cursor) {
        component.setCursor(cursor);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) applyCursorRecursively(child, cursor);
        }
    }

    private boolean applyCaptureExclusion() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        try {
            HWND hwnd = new HWND(Native.getComponentPointer(frame));
            return CaptureApi.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
        } catch (Throwable ignored) { return false; }
    }

    private void repaintModuleLists() {
        categoryLists.forEach(JList::repaint);
        if (searchList != null) searchList.repaint();
    }

    private JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(null);
        pane.getViewport().setBackground(PANEL);
        pane.getVerticalScrollBar().setUnitIncrement(18);
        return pane;
    }

    private JButton navButton(String text) {
        JButton button = styledButton(text);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        return button;
    }

    private JButton styledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(28, 22, 35));
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(75, 42, 96)), BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void styleTextField(JTextField field) {
        field.setBackground(new Color(5, 6, 9));
        field.setForeground(TEXT);
        field.setCaretColor(PURPLE_LIGHT);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(68, 68, 75)), BorderFactory.createEmptyBorder(7, 7, 7, 7)));
    }

    private String keyLabel(int key) {
        if (key == 0) return "NONE";
        if (key < 0) {
            int button = -key - 1;
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE 1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE 2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE 3";
                default -> "MOUSE " + (button + 1);
            };
        }
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> "KEY " + key;
        };
    }

    private int awtToGlfw(int key) {
        if ((key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z) || (key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9)) return key;
        if (key >= KeyEvent.VK_F1 && key <= KeyEvent.VK_F12) return GLFW.GLFW_KEY_F1 + (key - KeyEvent.VK_F1);
        return switch (key) {
            case KeyEvent.VK_SPACE -> GLFW.GLFW_KEY_SPACE;
            case KeyEvent.VK_TAB -> GLFW.GLFW_KEY_TAB;
            case KeyEvent.VK_ENTER -> GLFW.GLFW_KEY_ENTER;
            case KeyEvent.VK_SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case KeyEvent.VK_CONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case KeyEvent.VK_ALT -> GLFW.GLFW_KEY_LEFT_ALT;
            case KeyEvent.VK_UP -> GLFW.GLFW_KEY_UP;
            case KeyEvent.VK_DOWN -> GLFW.GLFW_KEY_DOWN;
            case KeyEvent.VK_LEFT -> GLFW.GLFW_KEY_LEFT;
            case KeyEvent.VK_RIGHT -> GLFW.GLFW_KEY_RIGHT;
            case KeyEvent.VK_HOME -> GLFW.GLFW_KEY_HOME;
            case KeyEvent.VK_END -> GLFW.GLFW_KEY_END;
            case KeyEvent.VK_PAGE_UP -> GLFW.GLFW_KEY_PAGE_UP;
            case KeyEvent.VK_PAGE_DOWN -> GLFW.GLFW_KEY_PAGE_DOWN;
            case KeyEvent.VK_INSERT -> GLFW.GLFW_KEY_INSERT;
            default -> Integer.MIN_VALUE;
        };
    }

    private String formatNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class FakeCursorPane extends JComponent {
        private final AWTEventListener mouseListener;
        private Point pointer = new Point(-100, -100);

        private FakeCursorPane(Cursor hiddenCursor) {
            setOpaque(false);
            setCursor(hiddenCursor);
            // Polling MouseInfo every 16 ms forces a full glass-pane repaint
            // and can starve the native mouse queue. Track actual mouse
            // events instead; repaint only the old and new cursor rectangles.
            mouseListener = event -> {
                if (!(event instanceof MouseEvent mouse)
                        || (mouse.getID() != MouseEvent.MOUSE_MOVED && mouse.getID() != MouseEvent.MOUSE_DRAGGED)
                        || !isShowing()) return;
                try {
                    Point screen = mouse.getLocationOnScreen();
                    SwingUtilities.convertPointFromScreen(screen, this);
                    updatePointer(screen);
                } catch (IllegalComponentStateException ignored) { }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        private void updatePointer(Point next) {
            if (next.equals(pointer)) return;
            Point old = pointer;
            pointer = next;
            repaint(Math.min(old.x, next.x) - 4, Math.min(old.y, next.y) - 4,
                    Math.abs(old.x - next.x) + 24, Math.abs(old.y - next.y) + 28);
        }

        private void stop() { Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener); }

        @Override public boolean contains(int x, int y) {
            return false;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int x = pointer.x;
                int y = pointer.y;
                int[] cursorX = {x, x, x + 6, x + 10, x + 13, x + 9, x + 5};
                int[] cursorY = {y, y + 19, y + 14, y + 22, y + 20, y + 12, y + 12};
                g.setColor(new Color(0, 0, 0, 190));
                int[] shadowX = cursorX.clone();
                int[] shadowY = cursorY.clone();
                for (int i = 0; i < shadowX.length; i++) { shadowX[i] += 2; shadowY[i] += 2; }
                g.fillPolygon(shadowX, shadowY, shadowX.length);
                g.setColor(new Color(245, 240, 255));
                g.fillPolygon(cursorX, cursorY, cursorX.length);
                g.setColor(PURPLE_LIGHT);
                g.drawPolygon(cursorX, cursorY, cursorX.length);
            } finally {
                g.dispose();
            }
        }
    }

    private final class ModuleRenderer extends DefaultListCellRenderer {
        private final boolean showCategory;
        private ModuleRenderer(boolean showCategory) { this.showCategory = showCategory; }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            Module module = (Module) value;
            label.setText((showCategory ? module.getCategory().getName() + "  •  " : "") + module.getName()
                    + (module.getKeyBind() == 0 ? "" : "   [" + keyLabel(module.getKeyBind()) + "]"));
            label.setOpaque(true);
            label.setBackground(selected ? ROW_HOVER : (index % 2 == 0 ? PANEL : ROW));
            label.setForeground(module.isEnabled() ? PURPLE_LIGHT : TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 5));
            label.setFont(new Font("Segoe UI", module.isEnabled() ? Font.BOLD : Font.PLAIN, 13));
            return label;
        }
    }
}
