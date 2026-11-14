package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigItem;
import java.lang.reflect.Method;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.util.Text;
import net.runelite.client.util.HotkeyListener;

/** Optional Sailing lifecycle hosted by Walker; no standalone plugin or second walking engine. */
final class EfficientSailingModule implements AutoCloseable {
    private static final String TARGET = "<col=00ff00>Efficient Sailing</col>";
    private static final String SAIL = "Sail here";
    private final Client client;
    private final EfficientWalkerConfig config;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final KeyManager keyManager;
    private final OverlayManager overlayManager;
    private final WorldMapOverlay worldMapOverlay;
    private final EfficientWalker walker;
    private volatile EfficientSailingController navigator;
    private boolean started;
    private EfficientSailingOverlay statusOverlay;
    private EfficientSailingMapOverlay mapOverlay;
    private EfficientSailingSceneOverlay sceneOverlay;
    private final HotkeyListener startPauseHotkey;

    EfficientSailingModule(Client client, EfficientWalkerConfig config, PluginManager pluginManager,
            ConfigManager configManager, KeyManager keyManager, OverlayManager overlayManager,
            WorldMapOverlay worldMapOverlay, EfficientWalker walker) {
        this.client = client; this.config = config; this.pluginManager = pluginManager;
        this.configManager = configManager; this.keyManager = keyManager;
        this.overlayManager = overlayManager; this.worldMapOverlay = worldMapOverlay; this.walker = walker;
        startPauseHotkey = new HotkeyListener(() -> config.startPauseHotkey()) {
            @Override public void hotkeyPressed() {
                EfficientSailingController n = navigator;
                if (n != null) n.togglePause();
            }
        };
    }

    /** Public integration API. Check getStatus()==ARRIVED; sailTo acceptance is not completion. */
    public EfficientSailingController getNavigator() { return navigator; }
    /** Enabled/idle service sends no navigation input. Callers must suspend their own loop. */
    public SailingNavigationService getNavigationService() { return navigator; }

    void start() {
        if (started) return;
        migrateSettings(configManager, config);
        started = true;
        refreshEnabled();
    }
    static void migrateSettings(ConfigManager manager, EfficientSailingConfig config) {
        migrateSavedSettings(manager);
        migratePortSettings(manager, config);
    }
    private static void migrateSavedSettings(ConfigManager configManager) {
        if ("1".equals(configManager.getConfiguration(EfficientSailingConfig.GROUP, "sailingImportVersion"))) return;
        for (Method method : EfficientSailingConfig.class.getMethods()) {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null) continue;
            copyLegacySetting(configManager, item.keyName());
        }
        copyLegacySetting(configManager, "portSettingsVersion");
        configManager.setConfiguration(EfficientSailingConfig.GROUP, "sailingImportVersion", "1");
    }
    private static void copyLegacySetting(ConfigManager configManager, String key) {
        if (configManager.getConfiguration(EfficientSailingConfig.GROUP, key) != null) return;
        String old = configManager.getConfiguration(EfficientSailingConfig.LEGACY_GROUP, key);
        if (old != null) configManager.setConfiguration(EfficientSailingConfig.GROUP, key, old);
    }
    private void refreshEnabled() {
        if (!started) return;
        if (!config.enableSailing()) { stopNavigator(); return; }
        if (navigator != null) return;
        navigator = new EfficientSailingController(client, config, pluginManager, walker);
        statusOverlay = new EfficientSailingOverlay(navigator, config);
        mapOverlay = new EfficientSailingMapOverlay(navigator, config, worldMapOverlay, client);
        sceneOverlay = new EfficientSailingSceneOverlay(navigator, config, client);
        overlayManager.add(statusOverlay); overlayManager.add(mapOverlay); overlayManager.add(sceneOverlay);
        keyManager.registerKeyListener(startPauseHotkey);
        navigator.start();
    }
    private static void migratePortSettings(ConfigManager configManager, EfficientSailingConfig config) {
        if ("3".equals(configManager.getConfiguration(EfficientSailingConfig.GROUP, "portSettingsVersion"))) return;
        if (configManager.getConfiguration(EfficientSailingConfig.GROUP, "currentBoatPort") == null) {
            configManager.setConfiguration(EfficientSailingConfig.GROUP, "currentBoatPort",
                    BoatPort.fromLegacy(config.knownBoatPort()).name());
        }
        if (configManager.getConfiguration(EfficientSailingConfig.GROUP, "launchPort") == null) {
            String oldLaunch = configManager.getConfiguration(EfficientSailingConfig.GROUP, "preferredLaunchPort");
            if (oldLaunch == null || oldLaunch.trim().isEmpty()) {
                if (config.portMode() == EfficientSailingConfig.PortMode.FIXED) oldLaunch = config.fixedPort();
                else if (!config.useSummonBoatTablet() && !config.allowRecovery()) oldLaunch = config.knownBoatPort();
            }
            configManager.setConfiguration(EfficientSailingConfig.GROUP, "launchPort", LaunchPort.fromLegacy(oldLaunch).name());
        }
        configManager.setConfiguration(EfficientSailingConfig.GROUP, "portSettingsVersion", "3");
    }
    @Override public void close() {
        started = false;
        stopNavigator();
    }
    private void stopNavigator() {
        if (navigator == null) return;
        keyManager.unregisterKeyListener(startPauseHotkey);
        EfficientSailingController old = navigator; navigator = null;
        if (statusOverlay != null) overlayManager.remove(statusOverlay);
        if (mapOverlay != null) overlayManager.remove(mapOverlay);
        if (sceneOverlay != null) overlayManager.remove(sceneOverlay);
        sceneOverlay = null;
        statusOverlay = null; mapOverlay = null;
        if (old != null) old.close();
    }
    @Subscribe public void onGameTick(GameTick event) {
        EfficientSailingController n = navigator; if (n != null) n.onGameTick();
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        EfficientSailingController n = navigator;
        if (n != null && event.getGameState() != GameState.LOGGED_IN) n.onSceneLoading();
    }
    @Subscribe public void onChatMessage(ChatMessage event) {
        EfficientSailingController n = navigator;
        if (n != null && event.getType() == ChatMessageType.GAMEMESSAGE) n.onGameMessage(Text.removeTags(event.getMessage()));
    }
    void onWalkerUserCommand() {
        EfficientSailingController n = navigator;
        if (n != null) n.onWalkerUserCommand();
    }
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event) {
        EfficientSailingController n = navigator;
        String target = Text.removeTags(event.getMenuTarget());
        String option = Text.removeTags(event.getMenuOption());
        if (n != null && "Efficient Sailing".equals(target)) {
            if ("Cancel sailing".equals(option)) {
                event.consume(); n.cancel(); return;
            }
        }
        if (n != null && target != null && target.equals("Efficient Walker")
                && ("Test walk".equals(option) || "Dry-run walk".equals(option) || "Cancel walk".equals(option))) {
            n.onWalkerUserCommand();
        }
    }
    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if (EfficientSailingConfig.GROUP.equals(event.getGroup()) && "enableSailing".equals(event.getKey())) refreshEnabled();
        EfficientSailingController n = navigator;
        if (n != null && EfficientSailingConfig.GROUP.equals(event.getGroup())
                && "exportDiagnostics".equals(event.getKey())) n.exportDiagnostics();

    }
    @Subscribe public void onMenuOpened(MenuOpened event) {
        EfficientSailingController n = navigator;
        if (n == null || client.getGameState() != GameState.LOGGED_IN) return;
        Widget map = client.getWidget(MicrobotSailingFacade.WORLD_MAP_ROOT);
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return;
        boolean worldMap = map != null && !map.isHidden() && map.getBounds().contains(mouse.getX(), mouse.getY());
        if (!worldMap) return;
        if (n.isBusy()) {
            boolean controlsPresent = false;
            for (MenuEntry entry : client.getMenuEntries()) {
                if ("Cancel sailing".equals(entry.getOption()) && TARGET.equals(entry.getTarget())) controlsPresent = true;
            }
            if (!controlsPresent) {
                client.createMenuEntry(-1).setOption("Cancel sailing").setTarget(TARGET).setType(MenuAction.RUNELITE)
                        .setDeprioritized(true);
            }
            return;
        }
        for (MenuEntry e : client.getMenuEntries()) if (SAIL.equals(e.getOption()) && TARGET.equals(e.getTarget())) return;
        final WorldPoint target = mapPoint(mouse);
        if (target != null) client.createMenuEntry(-1).setOption(SAIL).setTarget(TARGET).setType(MenuAction.RUNELITE)
                .setDeprioritized(true).onClick(e -> {
                    if (navigator == n) n.sailTo(target);
                });
    }
    private WorldPoint mapPoint(Point mouse) {
        WorldMap map = client.getWorldMap();
        if (map == null || map.getWorldMapPosition() == null) return null;
        Point p = map.getWorldMapPosition();
        WorldPoint center = new WorldPoint(p.getX(), p.getY(), 0);
        Point graphics = worldMapOverlay.mapWorldPointToGraphicsPoint(center);
        float zoom = map.getWorldMapZoom();
        if (graphics == null || zoom <= 0) return null;
        return center.dx((int) ((mouse.getX() - graphics.getX()) / zoom))
                .dy((int) (-(mouse.getY() - graphics.getY()) / zoom));
    }
}
