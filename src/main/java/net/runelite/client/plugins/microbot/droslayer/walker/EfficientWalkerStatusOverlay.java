package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Read-only display: sample once per game tick, never plan routes from render(). */
@Singleton
final class EfficientWalkerStatusOverlay extends OverlayPanel
{
    private final Client client;
    private final EfficientWalker walker;
    private final EfficientWalkerConfig config;
    private WorldPoint destination;
    private long startedAt;
    private volatile String[] display;

    @Inject
    EfficientWalkerStatusOverlay(Client client, EfficientWalker walker, EfficientWalkerConfig config)
    {
        this.client = client;
        this.walker = walker;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(190, 0));
    }

    void reset()
    {
        display = null;
        destination = null;
        startedAt = 0;
    }

    void refresh()
    {
        if (!config.showStatusOverlay() || client.getGameState() != GameState.LOGGED_IN
            || client.getLocalPlayer() == null)
        {
            reset();
            return;
        }
        EfficientWalker.Status status = walker.getStatus();
        if (status == EfficientWalker.Status.IDLE || status == EfficientWalker.Status.ARRIVED
            || status == EfficientWalker.Status.BLOCKED)
        {
            reset();
            return;
        }
        WorldPoint target = walker.getLogicalDestination();
        long now = System.nanoTime();
        if (startedAt == 0 || !Objects.equals(destination, target))
        {
            startedAt = now;
            destination = target;
        }
        String phase = walker.getNavigationPhase();
        String label = phaseLabel(phase);
        String eta = "calculating...";
        if ("PREVIEW".equals(phase))
        {
            label = "Route ready";
            eta = "awaiting travel";
        }
        else if ("WALKING".equals(phase))
        {
            WorldPoint player = client.getLocalPlayer().getWorldLocation();
            EfficientWalker.TransportMarker transport = walker.getActiveTransportMarker();
            if (transport != null && player.distanceTo(transport.point) <= 3)
            {
                label = "Teleport".equalsIgnoreCase(transport.action) ? "Teleporting" : "Using transport";
            }
            int seconds = estimateSeconds(walker.getRemainingRoute(), player, client.getVarpValue(173) == 1);
            if (seconds >= 0) eta = "~" + duration(seconds);
        }
        display = new String[] {label, duration((now - startedAt) / 1_000_000_000L), eta};
    }

    static String phaseLabel(String phase)
    {
        switch (phase)
        {
            case "BANK_ANALYZING_WEIGHTED_ROUTE":
            case "BANK_ANALYZING_FAIRY_RING_CANDIDATE": return "Checking bank routes";
            case "BANK_WALKING_TO_BANK": return "Walking to bank";
            case "BANK_PROBING_VISIBLE_BANK":
            case "BANK_OPENING_BANK": return "Opening bank";
            case "BANK_WAITING_BANK_SNAPSHOT": return "Reading bank";
            case "BANK_WITHDRAWING": return "Withdrawing supplies";
            case "BANK_CLOSING_BANK": return "Closing bank";
            case "BANK_ANALYZING_TRANSPORT_REQUIREMENTS": return "Checking supplies";
            case "BANK_ESCAPING_INSTANCE": return "Leaving instance";
            case "WALKING": return "Walking";
            default: return "Planning route";
        }
    }

    /** Walking at one/two tiles per tick, with a rough eight seconds per transport hop.
     * Only estimate when the player is on the known path; teleport landing paths may be remote.
     */
    static int estimateSeconds(List<WorldPoint> path, WorldPoint player, boolean running)
    {
        if (path == null || path.isEmpty() || player == null) return -1;
        int nearest = -1;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++)
        {
            int candidate = player.distanceTo(path.get(i));
            if (candidate < distance)
            {
                distance = candidate;
                nearest = i;
            }
        }
        if (nearest < 0 || distance > 3) return -1;
        int tiles = distance;
        int hops = 0;
        for (int i = nearest + 1; i < path.size(); i++)
        {
            int step = path.get(i - 1).distanceTo(path.get(i));
            if (step <= 1) tiles += step;
            else hops++;
        }
        return Math.max(1, (int) Math.ceil(tiles * (running ? 0.3 : 0.6) + hops * 8.0));
    }

    static String duration(long seconds)
    {
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        String[] snapshot = display;
        if (!config.showStatusOverlay() || snapshot == null) return null;
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder().text("EfficientWalker")
            .color(new Color(195,202,211)).build());
        panelComponent.getChildren().add(LineComponent.builder().left(snapshot[0]).right(snapshot[1]).build());
        panelComponent.getChildren().add(LineComponent.builder().left("ETA").right(snapshot[2]).build());
        setDynamicFont(false);
        DroOverlayDropdown.panel(graphics,panelComponent);
        Dimension styledSize = super.render(graphics);
        if (styledSize != null) DroOverlayDropdown.border(graphics,styledSize.width,styledSize.height);
        return styledSize;
    }
}
