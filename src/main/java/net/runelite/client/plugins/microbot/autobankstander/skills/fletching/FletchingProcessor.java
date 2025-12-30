package net.runelite.client.plugins.microbot.autobankstander.skills.fletching;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class FletchingProcessor implements BankStandingProcessor {

    private final FletchingMode mode;
    private final DartType dart;
    private final BoltType bolt;
    private final ArrowType arrow;
    private final JavelinType javelin;
    private final BowType bow;
    private final CrossbowType crossbow;
    private final ShieldType shield;

    private boolean currentlyMaking;
    private boolean stackableSession;
    private int itemA;
    private int itemB;

    private boolean waitingForMakeX = false;
    private long lastMakeXAttempt = 0;

    public FletchingProcessor(
            FletchingMode mode,
            DartType dart,
            BoltType bolt,
            ArrowType arrow,
            JavelinType javelin,
            BowType bow,
            CrossbowType crossbow,
            ShieldType shield
    ) {
        this.mode = mode;
        this.dart = dart;
        this.bolt = bolt;
        this.arrow = arrow;
        this.javelin = javelin;
        this.bow = bow;
        this.crossbow = crossbow;
        this.shield = shield;
    }

    /* ===================== VALIDATION ===================== */

    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.FLETCHING);

        switch (mode) {
            case DARTS:
                return dart != null && level >= dart.getLevelRequired();
            case BOLTS:
                return bolt != null && level >= bolt.getLevelRequired();
            case ARROWS:
                return arrow != null && level >= arrow.getLevelRequired();
            case JAVELINS:
                return javelin != null && level >= javelin.getLevelRequired();
            case BOWS:
                return bow != null && level >= bow.getLevelRequired();
            case CROSSBOWS:
                return crossbow != null && level >= crossbow.getLevelRequired();
            case SHIELDS:
                return shield != null && level >= shield.getLevelRequired();
            default:
                return false;
        }
    }

    /* ===================== BANKING ===================== */

    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, 3000);

        resetSession();

        switch (mode) {
            case DARTS:
                return withdrawAll(dart.getTipId(), dart.getFeatherId());
            case BOLTS:
                return withdrawAll(bolt.getMaterialOneId(), bolt.getMaterialTwoId());
            case ARROWS:
                return withdrawAll(arrow.getMaterialOneId(), arrow.getMaterialTwoId());
            case JAVELINS:
                return withdrawAll(javelin.getHeadId(), javelin.getShaftId());
            case BOWS:
                return withdrawX(bow.getMaterialOneId(), bow.getMaterialTwoId());
            case CROSSBOWS:
                return withdrawX(crossbow.getMaterialOneId(), crossbow.getMaterialTwoId());
            case SHIELDS:
                Rs2Bank.withdrawX(shield.getLogId(), 28);
                Rs2Bank.withdrawOne(shield.getKnifeId());
                return true;
            default:
                return false;
        }
    }

    private boolean withdrawAll(int a, int b) {
        Rs2Bank.withdrawAll(a);
        Rs2Bank.withdrawAll(b);
        return sleepUntil(
                () -> Rs2Inventory.hasItem(a) && Rs2Inventory.hasItem(b),
                3000
        );
    }

    private boolean withdrawX(int a, int b) {
        Rs2Bank.withdrawX(a, 14);
        Rs2Bank.withdrawX(b, 14);
        return sleepUntil(
                () -> Rs2Inventory.hasItem(a) && Rs2Inventory.hasItem(b),
                3000
        );
    }

    /* ===================== PROCESS ===================== */

    @Override
    public boolean process() {

        if (stackableSession && currentlyMaking) {

            // Out of materials → allow banking
            if (!Rs2Inventory.hasItem(itemA) || !Rs2Inventory.hasItem(itemB)) {
                log.info("Stackable session complete — waiting for banking");
                stackableSession = false;
                currentlyMaking = false;

                // IMPORTANT: return true so the script does NOT treat this as an error
                return true;
            }

            // Actively crafting
            if (Rs2Player.isAnimating()) {
                waitingForMakeX = false;
                return true;
            }

            // Retry SPACE once if Make-X didn't start
            if (waitingForMakeX && System.currentTimeMillis() - lastMakeXAttempt > 600) {
                log.warn("Retrying SPACE for Make-X");
                Rs2Keyboard.keyPress(32);
                waitingForMakeX = false;
                return true;
            }

            // Safety net
            if (!Rs2Player.isAnimating() && !waitingForMakeX) {
                log.warn("Idle during stackable session — restarting Make-X");

                if (Rs2Inventory.combine(itemA, itemB)) {
                    waitingForMakeX = true;
                    lastMakeXAttempt = System.currentTimeMillis();
                    sleep(400);
                    Rs2Keyboard.keyPress(32);
                    sleep(250);
                }
                return true;
            }

            return true;
        }

        switch (mode) {
            case DARTS:
                return startStackable(dart.getTipId(), dart.getFeatherId());
            case BOLTS:
                return startStackable(bolt.getMaterialOneId(), bolt.getMaterialTwoId());
            case ARROWS:
                return startStackable(arrow.getMaterialOneId(), arrow.getMaterialTwoId());
            case JAVELINS:
                return startStackable(javelin.getHeadId(), javelin.getShaftId());
            case BOWS:
                return startOnce(bow.getMaterialOneId(), bow.getMaterialTwoId(), bow.getName());
            case CROSSBOWS:
                return startOnce(crossbow.getMaterialOneId(), crossbow.getMaterialTwoId(), crossbow.getName());
            case SHIELDS:
                return startOnce(shield.getLogId(), shield.getKnifeId(), shield.getName());
            default:
                return false;
        }
    }

    private boolean startOnce(int a, int b, String name) {
        if (!Rs2Inventory.hasItem(a) || !Rs2Inventory.hasItem(b)) {
            return false;
        }

        if (Rs2Inventory.combine(a, b)) {
            handleDialogue(name);
            currentlyMaking = true;
            return true;
        }
        return false;
    }

    private boolean startStackable(int a, int b) {

        if (Rs2Inventory.combine(a, b)) {
            if (handleMakeXWidget()) {
                sleep(150);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

                itemA = a;          // ✅ REQUIRED
                itemB = b;          // ✅ REQUIRED
                currentlyMaking = true;
                stackableSession = true;
                waitingForMakeX = false;

                return true;
            }
        }

        return false;
    }

    /* ===================== DIALOGUE ===================== */

    private boolean handleMakeXWidget() {
        return sleepUntil(() ->
                        Rs2Widget.findWidget("How many sets of 10", null) != null
                                || Rs2Widget.findWidget("How many would you like to", null) != null,
                1500
        );
    }

    private void handleDialogue(String product) {

        boolean appeared = sleepUntil(
                () -> Rs2Dialogue.hasQuestion("How many")
                        || Rs2Dialogue.hasCombinationDialogue(),
                3000
        );

        if (!appeared) {
            return;
        }

        if (Rs2Dialogue.hasQuestion("How many")
                && Rs2Dialogue.hasDialogueOption("All")) {
            Rs2Dialogue.clickOption("All");
            sleep(300);
            return;
        }

        if (Rs2Dialogue.hasCombinationDialogue()) {
            Rs2Dialogue.clickCombinationOption(product);
            sleep(300);
        }
    }

    /* ===================== UTIL ===================== */

    private void resetSession() {
        currentlyMaking = false;
        stackableSession = false;
        waitingForMakeX = false;
        lastMakeXAttempt = 0;
        itemA = 0;
        itemB = 0;
    }

    @Override
    public boolean canContinueProcessing() {

        // If we were in a stackable session but no longer have materials,
        // we are DONE and should bank immediately.
        if (stackableSession
                && (!Rs2Inventory.hasItem(itemA) || !Rs2Inventory.hasItem(itemB))) {
            log.info("Stackable session finished — ready to bank");
            return false;
        }

        // Otherwise continue if we still have required items
        return hasRequiredItems();
    }

    @Override
    public List<String> getBankingRequirements() {
        return new ArrayList<>();
    }

    @Override
    public boolean hasRequiredItems() {

        if (stackableSession) {
            return true;
        }

        switch (mode) {
            case DARTS:
                return Rs2Inventory.hasItem(dart.getTipId())
                        && Rs2Inventory.hasItem(dart.getFeatherId());
            case BOLTS:
                return Rs2Inventory.hasItem(bolt.getMaterialOneId())
                        && Rs2Inventory.hasItem(bolt.getMaterialTwoId());
            case ARROWS:
                return Rs2Inventory.hasItem(arrow.getMaterialOneId())
                        && Rs2Inventory.hasItem(arrow.getMaterialTwoId());
            case JAVELINS:
                return Rs2Inventory.hasItem(javelin.getHeadId())
                        && Rs2Inventory.hasItem(javelin.getShaftId());
            case BOWS:
                return Rs2Inventory.hasItem(bow.getMaterialOneId())
                        && Rs2Inventory.hasItem(bow.getMaterialTwoId());
            case CROSSBOWS:
                return Rs2Inventory.hasItem(crossbow.getMaterialOneId())
                        && Rs2Inventory.hasItem(crossbow.getMaterialTwoId());
            case SHIELDS:
                return Rs2Inventory.hasItem(shield.getLogId())
                        && Rs2Inventory.hasItem(shield.getKnifeId());
            default:
                return false;
        }
    }

    @Override
    public String getStatusMessage() {
        return currentlyMaking ? "Fletching..." : "Preparing fletching";
    }
}
