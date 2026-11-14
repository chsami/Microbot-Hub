package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.gameval.InterfaceID;

/** Shared nonblocking login gate for Dro gameplay loops; offline lifecycle ticks remain enabled. */
public final class DroLoginReadiness
{
    private static final Window WINDOW = new Window();
    private static long nextWaitLogAt;

    private DroLoginReadiness() { }

    public static boolean isDroScript(Class<?> type)
    {
        return type.getName().startsWith("net.runelite.client.plugins.microbot.dro");
    }

    public static void onGameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGGING_IN
                || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            WINDOW.reset();
        }
    }

    public static boolean shouldWaitForLogin()
    {
        if (Microbot.getClientThread() == null) return true;
        Snapshot snapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (client == null) return new Snapshot(null, false);
            GameState state = client.getGameState();
            boolean sceneReady = state == GameState.LOGGED_IN && client.getLocalPlayer() != null
                    && client.getTopLevelWorldView() != null
                    && client.getTopLevelWorldView().getScene() != null
                    && client.getWidget(InterfaceID.Chatbox.UNIVERSE) != null;
            // Empty inventory and equipment containers may be absent after death.
            // A loaded game UI, rather than carried items, authorizes recovery.
            return new Snapshot(state, sceneReady);
        }).orElse(new Snapshot(null, false));
        long now = System.currentTimeMillis();
        if (snapshot.state == null)
        {
            WINDOW.reset();
            return true; // An unavailable client-thread snapshot cannot authorize gameplay.
        }
        if (snapshot.state == GameState.LOADING)
        {
            WINDOW.isReady(false, now);
            Microbot.status = "Waiting for game scene";
            return true;
        }
        if (snapshot.state != GameState.LOGGED_IN)
        {
            WINDOW.reset();
            return false; // Keep logout-break timers and AutoLogin handoffs ticking.
        }
        boolean waiting = !WINDOW.isReady(snapshot.sceneReady, now);
        if (waiting)
        {
            Microbot.status = "Waiting for game UI after login";
            if (now >= nextWaitLogAt)
            {
                nextWaitLogAt = now + 15_000L;
                Microbot.log("[DroLoginReadiness] Gameplay held: loadedPlayerSceneUi=" + snapshot.sceneReady
                    + " gameState=" + snapshot.state);
            }
        }
        return waiting;
    }

    private static final class Snapshot
    {
        private final GameState state;
        private final boolean sceneReady;

        private Snapshot(GameState state, boolean sceneReady)
        {
            this.state = state;
            this.sceneReady = sceneReady;
        }
    }

    static final class Window
    {
        private static final long UI_SETTLE_MS = 6_500L;
        private long readyAt;
        private boolean observingReadyScene;
        private boolean settled;

        synchronized boolean isReady(boolean sceneReady, long now)
        {
            if (!sceneReady)
            {
                // Ordinary region loads pause actions, but must not impose a new
                // login delay once this session has settled (especially in combat).
                if (!settled) reset();
                return false;
            }
            if (!observingReadyScene)
            {
                observingReadyScene = true;
                readyAt = now + UI_SETTLE_MS;
            }
            settled = now >= readyAt;
            return settled;
        }

        synchronized void reset()
        {
            observingReadyScene = false;
            settled = false;
            readyAt = 0L;
        }
    }
}
