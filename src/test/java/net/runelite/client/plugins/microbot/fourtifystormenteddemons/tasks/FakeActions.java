package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.GroundDrop;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Reads come from plain fields; each request is recorded as text and, when accepted, reports itself sent. */
final class FakeActions extends DemonActions
{
    final TestWorld world = new TestWorld();
    final List<String> requests = new ArrayList<>();
    final Map<Integer, Integer> inventory = new LinkedHashMap<>();
    final Map<Integer, Integer> bank = new LinkedHashMap<>();
    final Set<Integer> worn = new HashSet<>();
    final Set<Rs2PrayerEnum> prayersOn = new HashSet<>();
    final Set<String> refused = new HashSet<>();
    final Map<Integer, TileObject> objects = new LinkedHashMap<>();
    final Map<Integer, Long> prices = new LinkedHashMap<>();
    final Map<Integer, String> names = new LinkedHashMap<>();
    final Set<Integer> stackables = new HashSet<>();
    final List<NPC> demonList = new ArrayList<>();
    final List<GroundDrop> dropList = new ArrayList<>();
    WorldPoint point = new WorldPoint(4133, 4382, 0);
    int hp = 99;
    int maxHp = 99;
    int prayer = 99;
    int maxPrayer = 99;
    boolean bankIsOpen;
    boolean pinShown;
    boolean fullyRestored = true;

    FakeActions()
    {
        super(null, null, null, null);
    }

    @Override
    public Player player()
    {
        world.position = point;
        return world.player;
    }

    @Override
    public WorldPoint position()
    {
        return point;
    }

    @Override
    public int hitpoints()
    {
        return hp;
    }

    @Override
    public int maxHitpoints()
    {
        return maxHp;
    }

    @Override
    public int prayerPoints()
    {
        return prayer;
    }

    @Override
    public int maxPrayer()
    {
        return maxPrayer;
    }

    @Override
    public boolean prayerActive(Rs2PrayerEnum which)
    {
        return prayersOn.contains(which);
    }

    @Override
    public boolean restored()
    {
        return fullyRestored;
    }

    @Override
    public boolean stackable(int id)
    {
        return stackables.contains(id);
    }

    @Override
    public Item[] items()
    {
        List<Item> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : inventory.entrySet())
        {
            result.add(new Item(entry.getKey(), entry.getValue()));
        }
        return result.toArray(new Item[0]);
    }

    @Override
    public int bankCount(int id)
    {
        return bank.getOrDefault(id, 0);
    }

    @Override
    public boolean wearing(int id)
    {
        return worn.contains(id);
    }

    @Override
    public boolean inventoryLoaded()
    {
        return true;
    }

    @Override
    public boolean bankOpen()
    {
        return bankIsOpen;
    }

    @Override
    public boolean bankPinShown()
    {
        return pinShown;
    }

    @Override
    public long price(int id)
    {
        return prices.getOrDefault(id, 0L);
    }

    @Override
    public String itemName(int id)
    {
        return names.get(id);
    }

    @Override
    public List<NPC> demons()
    {
        return demonList;
    }

    @Override
    public List<GroundDrop> groundDrops()
    {
        return dropList;
    }

    @Override
    public TileObject object(Set<Integer> ids)
    {
        for (int id : ids)
        {
            if (objects.containsKey(id))
            {
                return objects.get(id);
            }
        }
        return null;
    }

    @Override
    public boolean attack(NPC npc, Runnable callback)
    {
        return record("attack", callback);
    }

    @Override
    public boolean prayer(Rs2PrayerEnum which, boolean active, Runnable callback)
    {
        return record("prayer:" + which.name() + ":" + active, callback);
    }

    @Override
    public boolean inventory(int id, String action, Runnable callback)
    {
        return record("inventory:" + id + ":" + action, callback);
    }

    @Override
    public boolean equipment(int id, String action, Runnable callback)
    {
        return record("equipment:" + id + ":" + action, callback);
    }

    @Override
    public boolean ringTeleport(Runnable callback)
    {
        return record("ring", callback);
    }

    @Override
    public boolean pickup(GroundDrop drop, Runnable callback)
    {
        return record("pickup:" + drop.getItem().getId(), callback);
    }

    @Override
    public boolean object(TileObject object, String action, Runnable callback)
    {
        return record("object:" + object.getId() + ":" + action, callback);
    }

    @Override
    public boolean enterBankPin(Runnable callback)
    {
        return record("enterBankPin", callback);
    }

    @Override
    public boolean openBank(Runnable callback)
    {
        return record("openBank", callback);
    }

    @Override
    public boolean deposit(int id, Runnable callback)
    {
        return record("deposit:" + id, callback);
    }

    @Override
    public boolean withdrawOne(int id, Runnable callback)
    {
        return record("withdraw:" + id, callback);
    }

    @Override
    public boolean withdraw(int id, int amount, Runnable callback)
    {
        return record(amount <= 1 ? "withdraw:" + id : "withdraw:" + id + ":" + amount, callback);
    }

    @Override
    public boolean closeBank(Runnable callback)
    {
        return record("closeBank", callback);
    }

    @Override
    public boolean step(WorldPoint tile, Runnable callback)
    {
        return record("step:" + tile.getX() + "," + tile.getY(), callback);
    }

    private boolean record(String request, Runnable callback)
    {
        String kind = request.contains(":") ? request.substring(0, request.indexOf(':')) : request;
        if (refused.contains(request) || refused.contains(kind))
        {
            return false;
        }
        requests.add(request);
        if (callback != null)
        {
            callback.run();
        }
        return true;
    }

    void have(int id, int quantity)
    {
        inventory.put(id, quantity);
    }

    void inBank(int id, int quantity)
    {
        bank.put(id, quantity);
    }
}
