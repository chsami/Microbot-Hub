package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class AccountSnapshot {
    boolean loggedIn;
    @Builder.Default Map<Skill, Integer> levels = Collections.emptyMap();
    @Builder.Default Map<Skill, Integer> xp = Collections.emptyMap();
    @Builder.Default Map<Integer, Integer> inventory = Collections.emptyMap();
    @Builder.Default Map<Integer, Integer> equipment = Collections.emptyMap();
    @Builder.Default Map<Integer, Integer> bank = Collections.emptyMap();
    boolean bankKnown;
    int freeSlots;
    int coins;
    Progress progress;
    /** 0 until the Account Summary has loaded it. */
    WorldPoint location;
    boolean starterKitClaimed;
    boolean mudfootToDo;
    boolean harlanReady;
    boolean shopsAllowed;
    /** By unnoted id; already counted in inventory, but noted items cannot be eaten, worn or used. */
    @Builder.Default Map<Integer, Integer> notedInventory = Collections.emptyMap();
    @Builder.Default int foodStock = 10;
    boolean foodBlocked;
    boolean wantsFood;
    @Builder.Default Set<Skill> protectedSkills = Collections.emptySet();

    public int owned(int itemId) {
        return inventory.getOrDefault(itemId, 0) + equipment.getOrDefault(itemId, 0) + bank.getOrDefault(itemId, 0);
    }

    public int carried(int itemId) {
        return inventory.getOrDefault(itemId, 0) - notedInventory.getOrDefault(itemId, 0) + equipment.getOrDefault(itemId, 0);
    }

    public int totalLevel() {
        return levels.values().stream().mapToInt(Integer::intValue).sum();
    }
}
