package net.runelite.client.plugins.microbot.sailing.features.trials;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRanks;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

@Slf4j
final class TrialAutomation {
    private long nextInteraction;
    private long nextDiagnostic;
    private long nextCamera;
    private long nextPitch;
    private long cameraGeneration;
    private int pitch = 270;
    private int yawKey;
    private int pitchKey;

    void startSelected(TrialRanks rank) {
        if (rank == null || "Unknown".equalsIgnoreCase(rank.name())) {
            diagnostic("Choose Unranked, Swordfish, Shark or Marlin in Target Rank.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextInteraction || !Microbot.isLoggedIn()) return;
        Widget choice = Microbot.getClientThread().invoke(() -> findRank(
                Microbot.getClient().getWidget(InterfaceID.SailingBtSelection.UNIVERSE), rank.name()));
        if (choice != null) {
            nextInteraction = now + 3000;
            log.info("Starting selected trial rank: {}", rank);
            Rs2Widget.clickWidget(choice);
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
        nextCamera = now + 4000;
        Microbot.getClientThread().invokeLater(() -> {
            stopCameraOnClientThread();
            if (!Microbot.isLoggedIn()) return;
            if (now >= nextPitch) {
                int oldPitch = Rs2Camera.getPitch();
                do { pitch = ThreadLocalRandom.current().nextInt(253, 283); }
                while (Math.abs(pitch - oldPitch) < 10);
                nextPitch = now + ThreadLocalRandom.current().nextLong(16000, 23001);
            }
            int angle = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(
                    target.getY() - position.getY(), target.getX() - position.getX()))) - 90, 360);
            int yawDirection = Integer.signum(Rs2Camera.getAngleTo(angle));
            int pitchDirection = Integer.signum(pitch - Rs2Camera.getPitch());
            int targetPitch = pitch;
            long generation = cameraGeneration;
            if (Math.abs(Rs2Camera.getAngleTo(angle)) > 48) {
                yawKey = yawDirection > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
                Rs2Keyboard.keyHold(yawKey);
            }
            if (Math.abs(targetPitch - Rs2Camera.getPitch()) > 4) {
                pitchKey = pitchDirection > 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
                Rs2Keyboard.keyHold(pitchKey);
            }
            Microbot.getClientThread().invokeLater(() -> {
                if (generation != cameraGeneration) return true;
                if (!Microbot.isLoggedIn() || System.currentTimeMillis() - now > 400) {
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
        nextInteraction = 0;
    }

    private void diagnostic(String message) {
        long now = System.currentTimeMillis();
        if (now < nextDiagnostic) return;
        nextDiagnostic = now + 15000;
        log.warn("Trials: {}", message);
    }
}
