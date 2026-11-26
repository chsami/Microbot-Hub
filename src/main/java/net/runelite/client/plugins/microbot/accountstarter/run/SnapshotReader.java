package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.HarlanClaim;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.logic.ProgressReader;
import net.runelite.client.plugins.microbot.accountstarter.logic.ResumeState;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/** Item ids are canonical (unnoted). */
public final class SnapshotReader {
    // runes and bronze arrows also come from Tutorial Island, so they do not count
    private static final Set<Integer> KIT_ONLY = Set.of(ItemID.IRON_MED_HELM, ItemID.IRON_CHAINBODY, ItemID.IRON_PLATELEGS,
            ItemID.IRON_LONGSWORD, ItemID.BLACK_ROBE, ItemID.BLACK_SKIRT);
    private static final WorldPoint NOWHERE = new WorldPoint(0, 0, 0);

    private final ItemManager itemManager;
    private final ResumeState state;
    /** Client thread only. Quest.getState runs a client script, and a snapshot is read several times a loop. */
    private int questTick = -1;
    private boolean mudfootDone;

    public SnapshotReader(ItemManager itemManager, ResumeState state) {
        this.itemManager = itemManager;
        this.state = state;
    }

    public AccountSnapshot read(boolean bankKnown) {
        return Microbot.getClientThread().invoke(() -> {
            return readNow(Microbot.getClient(), bankKnown);
        });
    }

    private AccountSnapshot readNow(Client client, boolean bankKnown) {
        Player me = client.getLocalPlayer();
        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && me != null;
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        for (Skill s : Skill.values()) {
            levels.put(s, client.getRealSkillLevel(s));
            xp.put(s, client.getSkillExperience(s));
        }
        ItemContainer invC = client.getItemContainer(InventoryID.INV);
        Map<Integer, Integer> inv = container(invC);
        Map<Integer, Integer> noted = new HashMap<>();
        int used = 0;
        if (invC != null) {
            for (Item i : invC.getItems()) {
                if (i.getId() > 0) used++;
                // canonicalize unnotes: an id it changes is a note (or a placeholder, never in the inventory)
                int canon = i.getId() > 0 ? itemManager.canonicalize(i.getId()) : i.getId();
                if (i.getId() > 0 && i.getQuantity() > 0 && canon != i.getId()) noted.merge(canon, i.getQuantity(), Integer::sum);
            }
        }
        Map<Integer, Integer> worn = container(client.getItemContainer(InventoryID.WORN));
        Map<Integer, Integer> bank = new HashMap<>();
        if (bankKnown) {
            for (Rs2ItemModel m : Rs2Bank.bankItems()) {
                // placeholders have quantity 0
                if (m.getQuantity() > 0) bank.merge(itemManager.canonicalize(m.getId()), m.getQuantity(), Integer::sum);
            }
        }
        Progress progress = ProgressReader.read(client::getVarbitValue);
        if (client.getTickCount() != questTick) {
            questTick = client.getTickCount();
            mudfootDone = Quest.MUDFOOTS_BIG_ADVENTURE.getState(client) == QuestState.FINISHED;
        }
        int total = levels.values().stream().mapToInt(Integer::intValue).sum();
        boolean kit = state.isStarterKitClaimed()
                || owned(ApTask.STARTER_KIT_ID, inv, worn, bank)
                || KIT_ONLY.stream().anyMatch(id -> owned(id, inv, worn, bank))
                || ApTask.of(ApPath.COMBAT).stream().anyMatch(t -> progress.of(t) != TaskState.NOT_DONE);
        return AccountSnapshot.builder()
                .loggedIn(loggedIn)
                .levels(Collections.unmodifiableMap(levels))
                .xp(Collections.unmodifiableMap(xp))
                .inventory(Collections.unmodifiableMap(inv))
                .notedInventory(Collections.unmodifiableMap(noted))
                .equipment(Collections.unmodifiableMap(worn))
                .bank(Collections.unmodifiableMap(bank))
                .bankKnown(bankKnown)
                .freeSlots(28 - used)
                .coins(inv.getOrDefault(ItemID.COINS, 0) + bank.getOrDefault(ItemID.COINS, 0))
                .progress(progress)
                .location(me == null ? NOWHERE : me.getWorldLocation())
                .starterKitClaimed(kit)
                .mudfootToDo(!mudfootDone)
                .harlanReady(mudfootDone && HarlanClaim.ready(System.currentTimeMillis()))
                .build();
    }

    private Map<Integer, Integer> container(ItemContainer c) {
        Map<Integer, Integer> m = new HashMap<>();
        if (c == null) return m;
        for (Item i : c.getItems()) {
            if (i.getId() > 0 && i.getQuantity() > 0) m.merge(itemManager.canonicalize(i.getId()), i.getQuantity(), Integer::sum);
        }
        return m;
    }

    private static boolean owned(int id, Map<Integer, Integer> inv, Map<Integer, Integer> worn, Map<Integer, Integer> bank) {
        return inv.containsKey(id) || worn.containsKey(id) || bank.containsKey(id);
    }
}
