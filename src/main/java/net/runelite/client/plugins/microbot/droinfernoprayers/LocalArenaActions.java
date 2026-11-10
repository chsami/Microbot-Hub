package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.droinfernoprayers.TeleportBankGuard;
import net.runelite.client.util.Text;

/** Local preparation capabilities. Walker owns navigation; this adapter owns only its requests. */
final class LocalArenaActions {
    private static final int FIRE_CAPE = 6570;
    enum CapeSupply { READY, WAITING, MISSING }
    enum CapeUse { WAITING, SUBMITTED, UNAVAILABLE }

    private final BooleanSupplier allowed;
    private int capeBankEpochBefore = -1;

    LocalArenaActions(BooleanSupplier allowed) { this.allowed = allowed; }

    WorldPoint position() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            return loaded(client) ? client.getLocalPlayer().getWorldLocation() : null;
        }).orElse(null);
    }

    boolean near(WorldPoint target, int distance) {
        var position = position();
        return target != null && position != null && position.distanceTo(target) <= distance;
    }

    /** Automatic travel belongs to the AIO startup, which this plugin does not include. */
    private static final String NO_TRAVEL = "Automatic travel is not available; walk to the entrance yourself";
    private String routeError;
    void walk(WorldPoint target) { routeError = NO_TRAVEL; }
    void walkWithSupplies(WorldPoint target) { routeError = NO_TRAVEL; }
    String routeFailure() { return routeError; }
    void cancel() { routeError = null; }

    boolean objectAvailable(int id, String action) { return findObject(id, action) != null; }

    boolean object(int id, String action) {
        if (!backgroundAllowed()) return false;
        var object = findObject(id, action);
        return object != null && allowed.getAsBoolean() && object.click(action);
    }

    private Rs2TileObjectModel findObject(int id, String action) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (!allowed.getAsBoolean() || !outside(client) || client.isWidgetSelected()) return null;
            return Microbot.getRs2TileObjectCache().query().where(object -> {
                var composition = object.getObjectComposition();
                var point = object.getWorldLocation();
                return object.getWorldView() == client.getTopLevelWorldView() && point != null
                    && point.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 24
                    && composition != null && composition.getId() == id
                    && composition.getActions() != null && Arrays.asList(composition.getActions()).contains(action);
            }).nearestOnClientThread();
        }).orElse(null);
    }

    /** Select once, then require the exact Fire cape selection before a later NPC-use action. */
    CapeUse useCape(int npcId) {
        if (!backgroundAllowed()) return CapeUse.WAITING;
        var npc = findNpc(npcId);
        if (npc == null || !capeCarried()) return CapeUse.UNAVAILABLE;
        boolean selected = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var widget = client.getSelectedWidget();
            return outside(client) && client.isWidgetSelected() && widget != null && widget.getItemId() == FIRE_CAPE;
        }).orElse(false);
        if (!allowed.getAsBoolean()) return CapeUse.WAITING;
        if (!selected) {
            Rs2Inventory.use(FIRE_CAPE);
            return CapeUse.WAITING;
        }
        return allowed.getAsBoolean() && npc.click("") ? CapeUse.SUBMITTED : CapeUse.UNAVAILABLE;
    }

    private Rs2NpcModel findNpc(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (!allowed.getAsBoolean() || !outside(client)) return null;
            return Microbot.getRs2NpcCache().query().withId(id).where(npc -> {
                var point = npc.getWorldLocation();
                return !npc.isDead() && npc.getWorldView() == client.getTopLevelWorldView()
                    && point != null && point.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 20;
            }).nearestOnClientThread();
        }).orElse(null);
    }

    boolean capeCarried() { return Rs2Inventory.hasItem(FIRE_CAPE); }

    CapeSupply supplyCape() {
        if (!backgroundAllowed()) return CapeSupply.WAITING;
        if (capeCarried()) return CapeSupply.READY;
        if (Rs2Inventory.isFull()) {
            if (Rs2Bank.isOpen()) {
                if (allowed.getAsBoolean()) Rs2Bank.depositAllExcept(FIRE_CAPE);
            } else openCapeBank();
            return CapeSupply.WAITING;
        }
        if (Rs2Equipment.isWearing(FIRE_CAPE)) {
            if (allowed.getAsBoolean()) Rs2Equipment.unEquip(FIRE_CAPE);
            return CapeSupply.WAITING;
        }
        if (!Rs2Bank.isOpen()) {
            openCapeBank();
            return CapeSupply.WAITING;
        }
        // The bank widget may appear before the live BANK container receipt.
        int epoch = Rs2Bank.getBankLiveEpoch();
        if (epoch <= 0 || epoch <= capeBankEpochBefore) return CapeSupply.WAITING;
        if (!Rs2Bank.hasItem(FIRE_CAPE)) return CapeSupply.MISSING;
        if (allowed.getAsBoolean()) Rs2Bank.withdrawOne(FIRE_CAPE);
        return CapeSupply.WAITING;
    }

    private void openCapeBank() {
        var object = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (!allowed.getAsBoolean() || !outside(client) || client.isWidgetSelected()) return null;
            return Microbot.getRs2TileObjectCache().query().where(candidate -> {
                var composition = candidate.getObjectComposition();
                return candidate.getWorldView() == client.getTopLevelWorldView() && composition != null
                    && composition.getActions() != null && Arrays.asList(composition.getActions()).contains("Bank");
            }).nearestOnClientThread(12);
        }).orElse(null);
        if (object != null) {
            if (allowed.getAsBoolean()) {
                capeBankEpochBefore = Rs2Bank.getBankLiveEpoch();
                object.click("Bank");
            }
            return;
        }
        var banker = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (!allowed.getAsBoolean() || !outside(client) || client.isWidgetSelected()) return null;
            return Microbot.getRs2NpcCache().query().where(candidate -> {
                var composition = candidate.getNpc().getTransformedComposition();
                if (composition == null) composition = candidate.getNpc().getComposition();
                return !candidate.isDead() && candidate.getWorldView() == client.getTopLevelWorldView()
                    && composition != null && composition.getActions() != null
                    && Arrays.asList(composition.getActions()).contains("Bank");
            }).nearestOnClientThread(12);
        }).orElse(null);
        if (banker != null) {
            if (allowed.getAsBoolean()) {
                capeBankEpochBefore = Rs2Bank.getBankLiveEpoch();
                banker.click("Bank");
            }
            return;
        }
        if (near(InfernoApproach.BANK, 8)) {
            capeBankEpochBefore = Rs2Bank.getBankLiveEpoch();
            object(30267, "Use");
            return;
        }
        var bank = Rs2Bank.getNearestBank();
        if (bank != null && allowed.getAsBoolean()) walkWithSupplies(bank.getWorldPoint());
    }

    boolean talk(int npcId) {
        if (!backgroundAllowed()) return false;
        var npc = findNpc(npcId);
        return npc != null && allowed.getAsBoolean() && npc.click("Talk-to");
    }

    boolean infernoUnlocked() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            return loaded(client) && client.getVarbitValue(VarbitID.INFERNO_SACRIFICED_FIRECAPE) != 0;
        }).orElse(false);
    }

    boolean ketKehDialogue() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var name = Microbot.getClient().getWidget(InterfaceID.ChatLeft.NAME);
            return name != null && !name.isHidden() && name.getText() != null
                && "TzHaar-Ket-Keh".equals(Text.removeTags(name.getText()));
        }).orElse(false);
    }

    boolean confirmCape() {
        if (!backgroundAllowed()) return false;
        String option = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Dialogue.getDialogueOptions().stream()
            .filter(widget -> widget != null && widget.getText() != null)
            .map(widget -> Text.removeTags(widget.getText())).filter(LocalArenaActions::affirmativeCapeOption)
            .findFirst().orElse(null)).orElse(null);
        return option != null && choose(option);
    }

    boolean inDialogue() {
        return Microbot.getClientThread().runOnClientThreadOptional(Rs2Dialogue::isInDialogue).orElse(false);
    }

    static boolean affirmativeCapeOption(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.matches("yes([ ,.!].*)?") && !lower.contains("don't") && !lower.contains("not ");
    }

    boolean continueDialogue() {
        if (!backgroundAllowed() || !Microbot.getClientThread().runOnClientThreadOptional(Rs2Dialogue::hasContinue).orElse(false)
            || !allowed.getAsBoolean()) return false;
        Rs2Dialogue.clickContinue();
        return true;
    }

    boolean choose(String text) { return backgroundAllowed() && Rs2Dialogue.clickOption(text, true); }

    private boolean backgroundAllowed() {
        return allowed.getAsBoolean() && !Thread.currentThread().isInterrupted()
            && (Microbot.getClientThread() == null || !Microbot.getClientThread().isClientThread());
    }

    /** Client-thread only. */
    private static boolean loaded(Client client) {
        return client != null && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
            && client.getTopLevelWorldView() != null && client.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
    }

    private static boolean outside(Client client) { return loaded(client) && !client.getTopLevelWorldView().isInstance(); }
}
