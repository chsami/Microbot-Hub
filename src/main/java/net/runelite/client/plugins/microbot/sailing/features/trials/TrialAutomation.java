package net.runelite.client.plugins.microbot.sailing.features.trials;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.*;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/** Trial interactions take precedence over navigation until the HUD acknowledges them. */
@Slf4j
final class TrialAutomation {
    private long nextInteraction;
    private long nextDiagnostic;
    private Boolean pendingRum;
    private int pendingDeliveries;
    private int attempts;
    private long nextCamera;
    private long nextPitch;
    private long cameraGeneration;
    private int pitch = 270;
    private int yawKey;
    private int pitchKey;
    private long nextTrim;

    static int nearestIndex(List<WorldPoint> points, WorldPoint position) {
        if (position == null || points == null || points.isEmpty()) return 0;
        int best = 0;
        for (int i = 1; i < points.size(); i++) {
            if (position.distanceTo(points.get(i)) < position.distanceTo(points.get(best))) best = i;
        }
        return best;
    }

    static boolean rumAcknowledged(boolean before, int deliveries, TrialInfo info) {
        return before != info.HasRum || info.CollectedPrimaryObjectives > deliveries;
    }

    boolean handleRum(TrialInfo info, WorldPoint position) {
        if (info.Location != TrialLocations.TemporTantrum) return false;
        long now = System.currentTimeMillis();
        if (pendingRum != null) {
            if (rumAcknowledged(pendingRum, pendingDeliveries, info)) {
                log.info("Trial rum interaction confirmed: carrying={}, delivered={}",
                        info.HasRum, info.CollectedPrimaryObjectives);
                pendingRum = null;
                attempts = 0;
                return false;
            }
            if (now < nextInteraction) return true;
            if (attempts >= 4) {
                diagnostic("Rum interaction not confirmed; reposition the boat or restart Trials.");
                return true;
            }
        }
        if (!info.HasRum && info.TotalPrimaryObjectivesNeeded > 0
                && info.CollectedPrimaryObjectives >= info.TotalPrimaryObjectivesNeeded) return false;
        int id = info.HasRum ? ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT
                : ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT;
        String action = info.HasRum ? "Deliver-rum" : "Collect-rum";
        Rs2TileObjectModel boat = Microbot.getClientThread().invoke(() ->
                Microbot.getRs2TileObjectCache().query()
                        .where(o -> hasObjectAction(o.getId(), action))
                        .where(o -> seaLocation(o.getWorldView(), o.getWorldLocation()) != null
                                && position.distanceTo(seaLocation(o.getWorldView(), o.getWorldLocation())) <= 18)
                        .first());
        if (boat == null) return pendingRum != null;
        if (now < nextInteraction) return true;
        stopCamera();
        nextInteraction = now + 2400;
        pendingRum = info.HasRum;
        pendingDeliveries = info.CollectedPrimaryObjectives;
        attempts++;
        log.info("Trial rum: action={}, object={}, worldView={}, attempt={}",
                action, boat.getId(), boat.getWorldView().getId(), attempts);
        boat.click(action);
        return true;
    }

    private static boolean hasObjectAction(int id, String action) {
        ObjectComposition definition = Microbot.getClient().getObjectDefinition(id);
        if (definition != null && definition.getImpostorIds() != null) definition = definition.getImpostor();
        return definition != null && definition.getActions() != null
                && Arrays.stream(definition.getActions()).anyMatch(action::equalsIgnoreCase);
    }

    private static WorldPoint seaLocation(WorldView view, WorldPoint fallback) {
        if (view == null) return null;
        if (view.getId() == -1) return fallback;
        WorldView top = Microbot.getClient().getTopLevelWorldView();
        if (top == null) return null;
        WorldEntity entity = top.worldEntities().byIndex(view.getId());
        if (entity == null || entity.getLocalLocation() == null) return null;
        return WorldPoint.fromLocalInstance(Microbot.getClient(), entity.getLocalLocation());
    }

    boolean trim(boolean needed) {
        if (!needed || System.currentTimeMillis() < nextTrim) return false;
        Rs2TileObjectModel sails = Microbot.getClientThread().invoke(() ->
                Microbot.getRs2TileObjectCache().query().fromWorldView()
                        .where(o -> hasObjectAction(o.getId(), "Trim")).first());
        if (sails == null) return false;
        nextTrim = System.currentTimeMillis() + 1800;
        log.info("Trial trim: object={}, worldView={}", sails.getId(), sails.getWorldView().getId());
        return sails.click("Trim");
    }

    static String rumAction(String[] actions, boolean carrying) {
        if (actions == null) return null;
        for (String action : actions) {
            if (action == null) continue;
            String text = action.toLowerCase(Locale.ROOT);
            if (carrying ? text.startsWith("deliver") || text.startsWith("hand-in")
                    : text.startsWith("collect") || text.startsWith("take-rum")) return action;
        }
        return null;
    }

    void startSelected(TrialRanks rank) {
        if (rank == null || "Unknown".equalsIgnoreCase(rank.name())) {
            diagnostic("Choose Unranked, Swordfish, Shark or Marlin in Target Rank.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextInteraction || !Microbot.isLoggedIn()) return;
        // Search only the trial selection interface, never unrelated dialogue/widgets.
        Widget choice = Microbot.getClientThread().invoke(() -> findRank(
                Microbot.getClient().getWidget(InterfaceID.SailingBtSelection.UNIVERSE), rank.name()));
        if (choice != null) {
            nextInteraction = now + 3000;
            log.info("Starting selected trial rank: {}", rank);
            Rs2Widget.clickWidget(choice);
            pendingRum = null;
            attempts = 0;
            return;
        }
        boolean selectionOpen = Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
            Widget widget = Microbot.getClient().getWidget(InterfaceID.SailingBtSelection.UNIVERSE);
            return widget != null && !widget.isHidden();
        });
        if (selectionOpen) {
            diagnostic("Selected trial rank is not actionable: " + rank + ". No other rank will be started.");
            return;
        }
        var npc = Microbot.getClientThread().invoke(() -> Microbot.getRs2NpcCache().query()
                .where(n -> n.getId() == 15094 || "Rum-dashed Ralph".equalsIgnoreCase(n.getName()))
                .first());
        if (npc == null) return;
        String action = Microbot.getClientThread().invoke(() -> {
            NPCComposition composition = npc.getNpc().getTransformedComposition();
            if (composition == null || composition.getActions() == null) return null;
            for (String a : composition.getActions()) {
                if ("Start-trial".equalsIgnoreCase(a)) return a;
            }
            return null;
        });
        if (action == null) {
            diagnostic("Open Ralph's trial selection to start the configured rank; no trial-selection action found.");
            return;
        }
        nextInteraction = now + 3000;
        NewMenuEntry entry = Microbot.getClientThread().invoke(() -> {
            String[] actions = npc.getNpc().getTransformedComposition().getActions();
            int index = Arrays.asList(actions).indexOf(action);
            MenuAction[] types = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
                    MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};
            if (index < 0 || index >= types.length) return null;
            return new NewMenuEntry().param0(0).param1(0).identifier(npc.getIndex())
                    .type(types[index]).option(action).target("Rum-dashed Ralph")
                    .actor(npc.getNpc()).worldViewId(npc.getWorldView().getId());
        });
        if (entry != null) {
            log.info("Trial start: action={}, npc={}, worldView={}", action, npc.getId(), npc.getWorldView().getId());
            Microbot.doInvoke(entry, Rs2UiHelper.getActorClickbox(npc.getNpc()));
        }
    }

    static Widget findRank(Widget root, String rank) {
        if (root == null || root.isHidden()) return null;
        String text = net.runelite.client.util.Text.removeTags(root.getText() == null ? "" : root.getText()).trim();
        if (text.equalsIgnoreCase(rank) && root.getWidth() > 0 && root.getHeight() > 0) return root;
        String label = (root.getName() + " " + root.getText()).toLowerCase(Locale.ROOT);
        String[] actions = root.getActions();
        if (actions != null) {
            for (String action : actions) {
                if (action != null && (action.toLowerCase(Locale.ROOT).contains(rank.toLowerCase(Locale.ROOT))
                        || label.contains(rank.toLowerCase(Locale.ROOT)))
                        && !action.toLowerCase(Locale.ROOT).contains("reward")) return root;
            }
        }
        for (Widget[] children : new Widget[][] {root.getDynamicChildren(), root.getStaticChildren(), root.getNestedChildren()}) {
            if (children == null) continue;
            for (Widget child : children) {
                Widget found = findRank(child, rank);
                if (found != null) return found;
            }
        }
        return null;
    }

    void followCamera(WorldPoint position, WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now < nextCamera || position.distanceTo(target) < 4) return;
        nextCamera = now + 1500;
        Microbot.getClientThread().invokeLater(() -> {
            stopCameraOnClientThread();
            if (!Microbot.isLoggedIn()) return;
            if (now >= nextPitch) {
                int oldPitch = Rs2Camera.getPitch();
                do { pitch = ThreadLocalRandom.current().nextInt(220, 311); }
                while (Math.abs(pitch - oldPitch) < 20);
                nextPitch = now + ThreadLocalRandom.current().nextLong(5000, 9001);
            }
            int angle = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(
                    target.getY() - position.getY(), target.getX() - position.getX()))) - 90, 360);
            int yawDirection = Integer.signum(Rs2Camera.getAngleTo(angle));
            int pitchDirection = Integer.signum(pitch - Rs2Camera.getPitch());
            int targetPitch = pitch;
            long generation = cameraGeneration;
            if (Math.abs(Rs2Camera.getAngleTo(angle)) > 15) {
                yawKey = yawDirection > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
                Rs2Keyboard.keyHold(yawKey);
            }
            if (Math.abs(targetPitch - Rs2Camera.getPitch()) > 4) {
                pitchKey = pitchDirection > 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
                Rs2Keyboard.keyHold(pitchKey);
            }
            Microbot.getClientThread().invokeLater(() -> {
                if (generation != cameraGeneration) return true;
                if (!Microbot.isLoggedIn() || System.currentTimeMillis() - now > 1400) {
                    stopCameraOnClientThread();
                    return true;
                }
                int yaw = Rs2Camera.getAngleTo(angle);
                int tilt = targetPitch - Rs2Camera.getPitch();
                if (yawKey != 0 && (Math.abs(yaw) <= 5 || Integer.signum(yaw) != yawDirection)) {
                    Rs2Keyboard.keyRelease(yawKey);
                    yawKey = 0;
                }
                if (pitchKey != 0 && (Math.abs(tilt) <= 4 || Integer.signum(tilt) != pitchDirection)) {
                    Rs2Keyboard.keyRelease(pitchKey);
                    pitchKey = 0;
                }
                return yawKey == 0 && pitchKey == 0;
            });
        });
    }

    void stopCamera() {
        Microbot.getClientThread().invokeLater(this::stopCameraOnClientThread);
    }

    private void stopCameraOnClientThread() {
        cameraGeneration++;
        if (yawKey != 0) Rs2Keyboard.keyRelease(yawKey);
        if (pitchKey != 0) Rs2Keyboard.keyRelease(pitchKey);
        yawKey = pitchKey = 0;
    }

    void stop() {
        stopCamera();
        pendingRum = null;
        attempts = 0;
        nextInteraction = 0;
    }

    private void diagnostic(String message) {
        long now = System.currentTimeMillis();
        if (now < nextDiagnostic) return;
        nextDiagnostic = now + 15000;
        log.warn("Trials: {}", message);
    }
}
