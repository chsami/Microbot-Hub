package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtify.core.engine.GameReader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the tasks may look at, read from the game once per tick by a {@link GameReader}.
 * <p>
 * Tasks decide only from this snapshot, so the same task code runs against the live game, snapshots
 * built in tests, or ticks recorded from a live run. Keep it to plain values: it is written to the
 * run log as JSON and read back for replay tests.
 */
@Value
@Builder(toBuilder = true)
public class GameState {
    /** The client's game tick counter. */
    int tick;
    /** Null while the player is not loaded. */
    WorldPoint position;
    boolean moving;
    boolean animating;
    /**
     * Ticks since the player last moved or animated; 0 while busy. Some actions leave gaps of more than a tick
     * between their animations, so a short idle stretch is not yet the end of the action.
     */
    int idleTicks;
    boolean bankOpen;
    /** Null unless the activity requests Grand Exchange data. */
    ExchangeState exchange;
    /** On a members world, where members-only content works. */
    boolean membersWorld;
    int freeSlots;
    int combatLevel;
    /** Inventory: item ID to total quantity. */
    @Singular
    Map<Integer, Integer> items;
    /**
     * Bank contents as last seen, only for the item IDs the {@link Surroundings} ask for. Microbot keeps the bank's
     * contents between visits and sessions, so this is known before the first visit of a run.
     */
    @Singular("banked")
    Map<Integer, Integer> bankItems;
    /** The bank's contents are known: the bank is open, or Microbot remembers them from an earlier visit. */
    boolean bankKnown;
    /** Item IDs worn. */
    @Singular("worn")
    Set<Integer> equipment;
    /** Item IDs worn, by equipment slot (the client's slot numbers: weapon 3, shield 5). */
    @Singular("wornIn")
    Map<Integer, Integer> wornSlots;
    @Singular("xp")
    Map<Skill, Integer> xp;
    /** Current levels, with boosts and drains: hitpoints and prayer points left, a potion-boosted Strength. */
    @Singular("boosted")
    Map<Skill, Integer> boostedLevels;
    /** Run energy, 0 to 100. */
    int runEnergy;
    boolean runEnabled;
    /** Weight carried and worn, in kg; weight-reducing clothing can make it negative. */
    int weight;
    /** Attacking or being attacked. */
    boolean inCombat;
    /** A cutscene is playing (the client's {@code CUTSCENE_STATUS} varbit, 542, is 1). */
    boolean inCutscene;
    /** The text of the open warning screen, such as before a dangerous area; "Wilderness" for the Wilderness ditch's. */
    String warning;
    /** The open chat dialogue, or null. */
    Dialogue dialogue;
    /** NPCs the plugin's {@link Surroundings} asked for, near the player. */
    @Singular
    List<Npc> npcs;
    /** Values of the varbits and varps the plugin's {@link Surroundings} asked for. */
    @Singular
    Map<Integer, Integer> varbits;
    @Singular
    Map<Integer, Integer> varps;
    /** How far each quest the plugin's {@link Surroundings} asked for has come, as the client's quest list shows it. */
    @Singular("quest")
    Map<Quest, QuestState> quests;
    /** Objects the plugin's {@link Surroundings} asked for, near the player. */
    @Singular
    List<Scenery> objects;
    /** Ground items the plugin's {@link Surroundings} asked for, near the player. */
    @Singular
    List<GroundItem> groundItems;
    /** Item IDs of the products the open make menu offers, in its order; empty while it is closed. */
    @Singular
    List<Integer> makeOptions;
    /** Game messages in the chat box since the last snapshot, oldest first, without colour tags. */
    @Singular
    List<String> messages;

    public int count(int itemId) {
        return items.getOrDefault(itemId, 0);
    }

    /** How many of this item the bank held when last seen. */
    public int bankCount(int itemId) {
        // Journals written before bank memory was recorded have no bank map.
        return bankItems == null ? 0 : bankItems.getOrDefault(itemId, 0);
    }

    public boolean hasAny(Collection<Integer> itemIds) {
        for (int id : itemIds) {
            if (count(id) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAny(int... itemIds) {
        for (int id : itemIds) {
            if (count(id) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean wearsAny(int... itemIds) {
        for (int id : itemIds) {
            if (equipment.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFull() {
        return freeSlots == 0;
    }

    /** Neither walking nor doing an action. */
    public boolean isIdle() {
        return !moving && !animating;
    }

    public int xp(Skill skill) {
        return xp.getOrDefault(skill, 0);
    }

    /** The current level with boosts and drains; the real level when not read (run logs from before it was). */
    public int boosted(Skill skill) {
        Integer level = boostedLevels == null ? null : boostedLevels.get(skill);
        return level != null ? level : level(skill);
    }

    public int hitpoints() {
        return boosted(Skill.HITPOINTS);
    }

    public int prayerPoints() {
        return boosted(Skill.PRAYER);
    }

    /** A varbit or varp the surroundings asked for; 0 when not read. */
    public int varbit(int id) {
        return varbits == null ? 0 : varbits.getOrDefault(id, 0);
    }

    public int varp(int id) {
        return varps == null ? 0 : varps.getOrDefault(id, 0);
    }

    /** A quest the surroundings asked for; not started when it was not read (so a missing request never opens a gate). */
    public QuestState questState(Quest quest) {
        QuestState state = quests == null ? null : quests.get(quest);
        return state == null ? QuestState.NOT_STARTED : state;
    }

    public boolean inDialogue() {
        return dialogue != null;
    }

    /** NPCs found in the surroundings; empty in run logs from before they were read. */
    public List<Npc> npcs() {
        return npcs == null ? java.util.Collections.emptyList() : npcs;
    }

    /** The item worn in an equipment slot, or -1; -1 in run logs from before slots were read. */
    public int wornIn(int slot) {
        return wornSlots == null ? -1 : wornSlots.getOrDefault(slot, -1);
    }

    /** The make menu's products; empty while it is closed and in run logs from before it was read. */
    public List<Integer> makeOptions() {
        return makeOptions == null ? java.util.Collections.emptyList() : makeOptions;
    }

    public boolean isMakeMenuOpen() {
        return !makeOptions().isEmpty();
    }

    /** Game messages since the last snapshot; empty in run logs from before they were read. */
    public List<String> messages() {
        return messages == null ? java.util.Collections.emptyList() : messages;
    }

    /** The real (unboosted) level, which the game derives from XP. */
    public int level(Skill skill) {
        return Experience.getLevelForXp(xp(skill));
    }

    /** Tiles to {@code point}; {@link Integer#MAX_VALUE} on another floor or while the player is not loaded. */
    public int distanceTo(WorldPoint point) {
        return position == null ? Integer.MAX_VALUE : position.distanceTo(point);
    }

    /** Where the player is and what they are doing, for failure logs. */
    public String summary() {
        return String.format("tick %d, at %s, %s, %d free slots%s", tick,
                position == null ? "unknown" : position.getX() + "," + position.getY() + "," + position.getPlane(),
                moving ? "moving" : animating ? "animating" : "idle", freeSlots, bankOpen ? ", bank open" : "");
    }
}
