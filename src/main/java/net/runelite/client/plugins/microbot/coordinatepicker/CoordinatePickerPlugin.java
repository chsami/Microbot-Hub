package net.runelite.client.plugins.microbot.coordinatepicker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Coordinate Picker",
        description = "Allows you to right-click tiles to add coordinates to a list",
        tags = {"coordinates", "tiles", "picker", "utilities"},
        authors = {"Microbot"},
        version = CoordinatePickerPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://via.placeholder.com/64x64.png?text=CP",
        cardUrl = "https://via.placeholder.com/300x200.png?text=Coordinate+Picker",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class CoordinatePickerPlugin extends Plugin {
    static final String version = "1.0.0";

    @Inject
    private Client client;

    @Inject
    private CoordinatePickerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CoordinatePickerOverlay coordinatePickerOverlay;

    @Inject
    private CoordinatePickerTileOverlay coordinatePickerTileOverlay;

    @Inject
    private CoordinatePickerScript coordinatePickerScript;

    private List<WorldPoint> pickedCoordinates = new ArrayList<>();

    @Provides
    CoordinatePickerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CoordinatePickerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(coordinatePickerOverlay);
            overlayManager.add(coordinatePickerTileOverlay);
        }
        coordinatePickerScript.run();
        log.info("Coordinate Picker Plugin started");
    }

    @Override
    protected void shutDown() {
        coordinatePickerScript.shutdown();
        overlayManager.remove(coordinatePickerOverlay);
        overlayManager.remove(coordinatePickerTileOverlay);
        log.info("Coordinate Picker Plugin stopped");
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        // Only add our menu option for tile-based actions (Walk is the most common)
        if (event.getOption().equals("Walk here") ||
            event.getOption().equals("Cancel")) {
            
            // Add our custom menu entry
            client.createMenuEntry(-1)
                    .setOption("Add coordinate")
                    .setTarget("")
                    .setType(MenuAction.RUNELITE)
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1());
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() == MenuAction.RUNELITE && 
            "Add coordinate".equals(event.getMenuOption())) {
            
            // Get the world coordinates of the clicked tile
            WorldPoint worldPoint = WorldPoint.fromScene(
                    client.getTopLevelWorldView(),
                    event.getParam0(),
                    event.getParam1(),
                    client.getTopLevelWorldView().getPlane()
            );

            if (worldPoint != null) {
                addCoordinate(worldPoint);
            }
        }
    }

    public void addCoordinate(WorldPoint worldPoint) {
        if (!pickedCoordinates.contains(worldPoint)) {
            // Check max coordinates limit
            if (pickedCoordinates.size() >= config.maxCoordinates()) {
                log.info("Maximum coordinates limit reached: {}", config.maxCoordinates());
                return;
            }
            
            pickedCoordinates.add(worldPoint);
            log.info("Added coordinate: {}", worldPoint);
        } else {
            log.info("Coordinate already exists: {}", worldPoint);
        }
    }

    public void removeCoordinate(WorldPoint worldPoint) {
        if (pickedCoordinates.remove(worldPoint)) {
            log.info("Removed coordinate: {}", worldPoint);
        }
    }

    public void clearCoordinates() {
        pickedCoordinates.clear();
        log.info("Cleared all coordinates");
    }

    public List<WorldPoint> getPickedCoordinates() {
        return new ArrayList<>(pickedCoordinates);
    }

    public String getCoordinatesAsList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pickedCoordinates.size(); i++) {
            WorldPoint wp = pickedCoordinates.get(i);
            sb.append(String.format("new WorldPoint(%d, %d, %d)", wp.getX(), wp.getY(), wp.getPlane()));
            if (i < pickedCoordinates.size() - 1) {
                sb.append(",\n");
            }
        }
        return sb.toString();
    }

    public String getCoordinatesAsString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pickedCoordinates.size(); i++) {
            WorldPoint wp = pickedCoordinates.get(i);
            sb.append(String.format("Coordinate %d: x=%d, y=%d, plane=%d", i + 1, wp.getX(), wp.getY(), wp.getPlane()));
            if (i < pickedCoordinates.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
