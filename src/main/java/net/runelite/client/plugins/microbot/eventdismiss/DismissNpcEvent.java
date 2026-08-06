package net.runelite.client.plugins.microbot.eventdismiss;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DismissNpcEvent implements BlockingEvent {

    private final EventDismissConfig config;
    private final AtomicBoolean waitingForLamp = new AtomicBoolean(false);
    private final AtomicInteger lampWaitCounter = new AtomicInteger(0);
    private static final int MAX_LAMP_WAIT_TICKS = 50;

    public DismissNpcEvent(EventDismissConfig config) {
        this.config = config;
    }

    private Rs2NpcModel getRandomEventNpc() {
        var oldModel = Rs2Npc.getRandomEventNPC();
        if (oldModel == null) return null;
        return Microbot.getRs2NpcCache().query().where(n -> n.getNpc().equals(oldModel.getRuneliteNpc())).nearest();
    }

    @Override
    public boolean validate() {
        if (waitingForLamp.get()) {
            return true;
        }
        Rs2NpcModel npc = getRandomEventNpc();
        return npc != null && npc.hasLineOfSight();
    }

    @Override
    public boolean execute() {
        if (waitingForLamp.get()) {
            return handleLampWait();
        }

        Rs2NpcModel npc = getRandomEventNpc();
        if (npc == null)
            return true;

        String name = npc.getName();
        if (name == null)
            return true;

        Global.sleep(Rs2Random.between(1200, 3000));

        if (shouldAcceptLamp(name)) {
            if (Rs2Inventory.isFull()) {
                log.info("Inventory full — waiting for space to accept lamp from {}", name);
                return false;
            }
            return acceptLamp(npc);
        }

        dismiss(npc);
        return !validate();
    }

    private boolean shouldAcceptLamp(String npcName) {
        if ("Genie".equals(npcName)) {
            return config.genieAction() == EventAction.ACCEPT;
        }
        if ("Count Check".equals(npcName)) {
            return config.countCheckAction() == EventAction.ACCEPT;
        }
        return false;
    }

    private boolean acceptLamp(Rs2NpcModel npc) {
        npc.click("Talk-to");
        continueDialogueUntilClosed();

        if (Rs2Inventory.contains(ItemID.LAMP)) {
            log.info("Lamp received — using on {}", config.lampSkill());
            Global.sleep(600, 1200);
            if (LampUtility.useLamp(config.lampSkill())) {
                return true;
            }
        }

        log.info("Waiting for lamp to appear in inventory");
        waitingForLamp.set(true);
        lampWaitCounter.set(0);

        Global.sleep(600, 1200);

        if (Rs2Inventory.contains(ItemID.LAMP)) {
            if (LampUtility.useLamp(config.lampSkill())) {
                resetLampWaitState();
                return true;
            }
        }

        return false;
    }

    private boolean handleLampWait() {
        if (lampWaitCounter.incrementAndGet() > MAX_LAMP_WAIT_TICKS) {
            log.warn("Lamp wait timeout");
            resetLampWaitState();
            return true;
        }

        if (Rs2Inventory.contains(ItemID.LAMP)) {
            log.info("Lamp appeared — using on {}", config.lampSkill());
            if (LampUtility.useLamp(config.lampSkill())) {
                resetLampWaitState();
                return true;
            }
        }

        return false;
    }

    private void resetLampWaitState() {
        waitingForLamp.set(false);
        lampWaitCounter.set(0);
    }

    private void continueDialogueUntilClosed() {
        Rs2Dialogue.sleepUntilInDialogue();

        while (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                Global.sleep(600, 1200);
            } else {
                Global.sleep(300, 600);
            }
        }

        Rs2Dialogue.sleepUntilNotInDialogue();
    }

    private void dismiss(Rs2NpcModel npc) {
        npc.click("Dismiss");
        Global.sleepUntil(() -> getRandomEventNpc() == null);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.LOWEST;
    }
}
