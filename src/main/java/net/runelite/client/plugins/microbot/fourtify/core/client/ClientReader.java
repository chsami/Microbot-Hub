package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.fourtify.core.engine.GameReader;
import net.runelite.client.plugins.microbot.fourtify.core.game.Dialogue;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GroundItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;
import net.runelite.client.plugins.microbot.fourtify.core.game.Scenery;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the live client through Microbot's helpers and entity lists. Runs on the script thread. The player
 * and inventory helpers each make their own client-thread hop; the world type, XP and the scanned objects and
 * ground items are read in one hop, inside which Microbot's nested client-thread calls run inline.
 */
public class ClientReader implements GameReader {
    private final Surroundings surroundings;
    /**
     * The level-up message ({@code levelup_display}): its two lines of text and its "Click here to
     * continue", which Microbot's dialogue helpers do not read.
     */
    static final int LEVEL_UP = 233;
    static final int LEVEL_UP_TEXT_1 = 1;
    static final int LEVEL_UP_TEXT_2 = 2;
    static final int LEVEL_UP_CONTINUE = 3;

    /** The last tick the player moved or animated; none yet at the start. */
    private int lastBusyTick = Integer.MIN_VALUE;

    /** The newest chat message already read. The first read takes no history, only where the run starts. */
    private int lastMessageId = Integer.MIN_VALUE;

    public ClientReader(Surroundings surroundings) {
        this.surroundings = surroundings;
    }

    @Override
    public GameState read() {
        boolean bankOpen = Rs2Bank.isOpen();
        int tick = Microbot.getClient().getTickCount();
        boolean moving = Rs2Player.isMoving();
        boolean animating = Rs2Player.isAnimating();
        GameState.GameStateBuilder state = GameState.builder()
                .tick(tick)
                .position(Rs2Player.getWorldLocation())
                .moving(moving)
                .animating(animating)
                .bankOpen(bankOpen)
                .freeSlots(Rs2Inventory.emptySlotCount())
                .combatLevel(Rs2Player.getCombatLevel());
        Map<Integer, Integer> items = new HashMap<>();
        Rs2Inventory.items().forEach(item -> items.merge(item.getId(), item.getQuantity(), Integer::sum));
        state.items(items);
        for (Rs2ItemModel item : Rs2Equipment.items()) {
            state.worn(item.getId());
            state.wornIn(item.getSlot(), item.getId());
        }
        bank(state, bankOpen);
        // One trip to the client thread for XP and all the surroundings: each model getter (name, actions,
        // reachability) would otherwise wait for its own client frame, per object, every tick.
        Boolean menuOpen = Microbot.getClientThread().runOnClientThreadOptional(() -> scene(state)).orElse(null);
        boolean read = menuOpen != null;
        if (!read) {
            // A snapshot without XP or the world type would read as level 1 on a free-to-play world and send a
            // best-tree run to the wrong tree; skip this tick instead.
            throw new IllegalStateException("the client thread did not answer; tick skipped");
        }
        state.idleTicks(idleTicks(tick, busy(moving, animating, menuOpen)));
        return state.build();
    }

    /**
     * The bank counts of the item IDs the surroundings ask for. Microbot keeps the bank's contents from the last time
     * it was open, saved with the profile between sessions, so they are known before the first visit.
     */
    private void bank(GameState.GameStateBuilder state, boolean bankOpen) {
        List<Rs2ItemModel> bank = Rs2Bank.bankItems();
        state.bankKnown(bankOpen || (bank != null && !bank.isEmpty()));
        if (bank == null || surroundings.getBankItemIds().isEmpty()) {
            return;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (Rs2ItemModel item : bank) {
            if (surroundings.getBankItemIds().contains(item.getId())) {
                counts.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        state.bankItems(counts);
    }

    /**
     * XP and current levels, run energy, weight, combat, the open dialogue, the varbits, varps and quests, and the
     * objects, NPCs and ground items the surroundings ask for. Runs on the client thread, where Microbot's own
     * client-thread calls run inline. Returns whether the make menu is open.
     */
    private boolean scene(GameState.GameStateBuilder state) {
        Client client = Microbot.getClient();
        state.membersWorld(client.getWorldType().contains(WorldType.MEMBERS));
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            xp.put(skill, client.getSkillExperience(skill));
            boosted.put(skill, client.getBoostedSkillLevel(skill));
        }
        state.xp(xp);
        state.boostedLevels(boosted);
        state.runEnergy(Rs2Player.getRunEnergy())
                .runEnabled(Rs2Player.isRunEnabled())
                .weight(client.getWeight())
                .inCombat(Rs2Player.isInCombat())
                .dialogue(dialogue())
                .warning(warning())
                .inCutscene(client.getVarbitValue(VarbitID.CUTSCENE_STATUS) == 1);
        state.messages(newMessages(client.getMessages()));
        boolean menuOpen = makeOptions(client, state);
        for (int id : surroundings.getVarbits()) {
            state.varbit(id, client.getVarbitValue(id));
        }
        for (int id : surroundings.getVarps()) {
            state.varp(id, client.getVarpValue(id));
        }
        state.quests(questStates(client, surroundings.getQuests()));
        // The NPCs asked for by name, and always those after the player, such as a random event.
        Player me = client.getLocalPlayer();
        Map<Integer, NPC> npcs = new LinkedHashMap<>();
        if (!surroundings.getNpcNames().isEmpty()) {
            for (Rs2NpcModel model : Microbot.getRs2NpcCache().query()
                    .withNames(surroundings.getNpcNames().toArray(new String[0]))
                    .within(surroundings.getRadius())
                    .toList()) {
                npcs.put(((NPC) model.getActor()).getIndex(), (NPC) model.getActor());
            }
        }
        for (Rs2NpcModel model : Microbot.getRs2NpcCache().query()
                .where(candidate -> ((NPC) candidate.getActor()).getInteracting() == me)
                .toList()) {
            npcs.putIfAbsent(((NPC) model.getActor()).getIndex(), (NPC) model.getActor());
        }
        if (me != null && me.getInteracting() instanceof NPC) {
            NPC target = (NPC) me.getInteracting();
            npcs.putIfAbsent(target.getIndex(), target);
        }
        for (NPC npc : npcs.values()) {
            NPCComposition definition = npc.getTransformedComposition();
            if (definition == null || definition.getName() == null) {
                continue;
            }
            int scale = npc.getHealthScale();
            state.npc(new Npc(npc.getId(), npc.getIndex(), Rs2UiHelper.stripColTags(definition.getName()), npc.getWorldLocation(),
                    npc.getCombatLevel(), clean(definition.getActions()), npc.getInteracting() == me,
                    scale > 0 ? 100.0 * npc.getHealthRatio() / scale : -1,
                    npc.getInteracting() != null && npc.getInteracting() != me, me != null && me.getInteracting() == npc));
        }
        if (!surroundings.getObjectNames().isEmpty()) {
            List<Rs2TileObjectModel> found = Microbot.getRs2TileObjectCache().query()
                    .withNames(surroundings.getObjectNames().toArray(new String[0]))
                    .within(surroundings.getRadius())
                    .toList();
            for (Rs2TileObjectModel object : found) {
                String name = object.getName();
                if (name != null) {
                    state.object(new Scenery(object.getId(), name, object.getWorldLocation(), actions(object.getId()), object.isReachable()));
                }
            }
        }
        if (!surroundings.getGroundItemNameParts().isEmpty() || !surroundings.getGroundItemIds().isEmpty()) {
            List<Rs2TileItemModel> found = Microbot.getRs2TileItemCache().query()
                    .where(item -> surroundings.wantsGroundItem(item.getId(), item.getName()))
                    .within(surroundings.getRadius())
                    .toList();
            for (Rs2TileItemModel item : found) {
                state.groundItem(new GroundItem(item.getId(), item.getName(), item.getQuantity(), item.getWorldLocation(), !item.isLootAble()));
            }
        }
        return menuOpen;
    }

    /**
     * Moving, animating, or choosing in the make menu: the menu closes a tick before making shows, so a settle
     * wait counted from the menu does not take that tick for the end of making.
     */
    static boolean busy(boolean moving, boolean animating, boolean makeMenuOpen) {
        return moving || animating || makeMenuOpen;
    }

    /** Ticks since the player was last {@code busy} (moving or animating), counted across reads. */
    int idleTicks(int tick, boolean busy) {
        if (busy) {
            lastBusyTick = tick;
        }
        return lastBusyTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : tick - lastBusyTick;
    }

    /**
     * The state of each quest asked for, from the client's quest list: {@code Quest.getState} runs the game's quest
     * status script, which must run on the client thread (this is called from {@link #scene}, which does). One quest
     * that fails to read is left out, and a quest that was not read counts as not started.
     */
    static Map<Quest, QuestState> questStates(Client client, Set<Quest> quests) {
        Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
        for (Quest quest : quests) {
            try {
                states.put(quest, quest.getState(client));
            } catch (RuntimeException e) {
                // Left out: not started, so the gates it opens stay shut.
            }
        }
        return states;
    }

    /** The products of the open make menu, in slot order: each slot's item, from the model it draws. */
    private static boolean makeOptions(Client client, GameState.GameStateBuilder state) {
        Widget root = client.getWidget(MakeMenu.GROUP, MakeMenu.ROOT);
        if (root == null || root.isHidden()) {
            return false;
        }
        for (int child = MakeMenu.FIRST_SLOT; child <= MakeMenu.LAST_SLOT; child++) {
            Widget slot = client.getWidget(MakeMenu.GROUP, child);
            int item = slot == null || slot.isHidden() ? -1 : itemOf(slot);
            if (item <= 0) {
                return child > MakeMenu.FIRST_SLOT; // slots fill in order
            }
            state.makeOption(item);
        }
        return true;
    }

    private static int itemOf(Widget slot) {
        if (slot.getItemId() > 0) {
            return slot.getItemId();
        }
        Widget[] parts = slot.getDynamicChildren();
        if (parts != null) {
            for (Widget part : parts) {
                if (part != null && part.getItemId() > 0) {
                    return part.getItemId();
                }
            }
        }
        return -1;
    }

    /**
     * Game messages added since the last read, oldest first. The client numbers chat messages in the order they
     * arrive, so a higher ID is a newer message. The first read only marks where the run starts.
     */
    List<String> newMessages(Iterable<MessageNode> messages) {
        boolean first = lastMessageId == Integer.MIN_VALUE;
        int newest = lastMessageId;
        List<MessageNode> fresh = new ArrayList<>();
        for (MessageNode message : messages) {
            if (message.getId() > lastMessageId) {
                newest = Math.max(newest, message.getId());
                if (message.getType() == ChatMessageType.GAMEMESSAGE || message.getType() == ChatMessageType.SPAM) {
                    fresh.add(message);
                }
            }
        }
        lastMessageId = Math.max(newest, -1);
        if (first) {
            return Collections.emptyList();
        }
        fresh.sort(Comparator.comparingInt(MessageNode::getId));
        List<String> texts = new ArrayList<>();
        for (MessageNode message : fresh) {
            texts.add(Rs2UiHelper.stripColTags(message.getValue()));
        }
        return texts;
    }

    /** The text of the open warning screen, or null. Runs on the client thread. */
    private static String warning() {
        if (Rs2Widget.isWidgetVisible(Warnings.WILDERNESS, Warnings.WILDERNESS_TEXT)) {
            return "Wilderness";
        }
        for (int screen : Warnings.SCREENS) {
            Widget text = Rs2Widget.getWidget(screen, Warnings.TEXT);
            if (text != null && !text.isHidden() && text.getText() != null) {
                return Rs2UiHelper.stripColTags(text.getText().replace("<br>", " "));
            }
        }
        return null;
    }

    /** The open chat dialogue: its text, its options, and whether it can be continued; null when none is open. */
    static Dialogue dialogue() {
        if (Rs2Widget.isWidgetVisible(LEVEL_UP, LEVEL_UP_CONTINUE)) {
            return new Dialogue((text(LEVEL_UP, LEVEL_UP_TEXT_1) + " " + text(LEVEL_UP, LEVEL_UP_TEXT_2)).trim(),
                    Collections.emptyList(), true);
        }
        if (!Rs2Dialogue.isInDialogue()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        List<Widget> widgets = Rs2Dialogue.getDialogueOptions();
        if (widgets != null) {
            for (Widget widget : widgets) {
                if (widget.getText() != null && !widget.getText().isEmpty()) {
                    options.add(Rs2UiHelper.stripColTags(widget.getText()));
                }
            }
        }
        String text = Rs2Dialogue.getDialogueText();
        return new Dialogue(text == null ? "" : Rs2UiHelper.stripColTags(text), options, Rs2Dialogue.hasContinue());
    }

    private static String text(int group, int child) {
        Widget widget = Rs2Widget.getWidget(group, child);
        return widget == null || widget.getText() == null ? "" : Rs2UiHelper.stripColTags(widget.getText());
    }

    /** Menu actions without nulls and colour tags. */
    private static List<String> clean(String[] actions) {
        List<String> out = new ArrayList<>();
        if (actions != null) {
            for (String action : actions) {
                if (action != null) {
                    out.add(Rs2UiHelper.stripColTags(action));
                }
            }
        }
        return out;
    }

    /** The menu actions the player sees, from the object's visible (impostor-resolved) definition. */
    private static List<String> actions(int objectId) {
        ObjectComposition composition = Rs2TileObjectModel.compositionOf(objectId);
        if (composition == null || composition.getActions() == null) {
            return Collections.emptyList();
        }
        List<String> actions = new ArrayList<>();
        for (String action : composition.getActions()) {
            if (action != null) {
                actions.add(Rs2UiHelper.stripColTags(action));
            }
        }
        return actions;
    }
}
