package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.TravelMethod;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.TunnelMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankTravelTest
{
    private static final int RING_WIDGET = (387 << 16) | 24;

    @Test
    public void onlyChargedDuelingRingsQualifyAndAreRetained() throws Exception
    {
        Fixture fixture = new Fixture();
        for (int id : new int[] {2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566})
        {
            fixture.inventory = new Item[] {new Item(id, 1)};
            assertTrue(fixture.actions.hasBankTravelRing());
            assertEquals(0, fixture.actions.lootCount());
        }
        for (int id : new int[] {2572, 11980, 20786, 3853, 1712, 19707})
        {
            fixture.inventory = new Item[] {new Item(id, 1)};
            assertFalse(fixture.actions.hasBankTravelRing());
            assertTrue(fixture.actions.bankTravelProblem().contains("charged Ring of dueling"));
            assertEquals(1, fixture.actions.lootCount());
        }
    }

    @Test
    public void legacyBankKeysHaveNoConfigurationApiAndOutboundSelectionRemains() throws Exception
    {
        assertFalse(Arrays.stream(BarrowsConfig.class.getMethods()).anyMatch(method ->
            Arrays.asList("bankTravelMethod", "bankX", "bankY", "bankPlane").contains(method.getName())));
        assertEquals(TravelMethod.BARROWS_TABLET, new BarrowsConfig() {}.travelMethod());
        Fixture fixture = new Fixture();
        assertEquals(new WorldPoint(3130, 3632, 0), fixture.actions.bankDestination());
    }

    @Test
    public void alreadyOpenBankCanReplenishMissingRingBeforeStarting() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.actions.bankTravelStartProblem().contains("charged"));
        fixture.bankOpen = true;
        assertNull(fixture.actions.bankTravelStartProblem());
    }

    @Test
    public void bankIntentMayUseDuelingFromCryptsOrTunnelsBeforeReward() throws Exception
    {
        for (WorldPoint point : new WorldPoint[] {Brother.DHAROK.getSarcophagus(), TunnelMap.roomCenter(4)})
        {
            Fixture fixture = wornRingFixture();
            fixture.position = point;
            fixture.actions.requestBank();
            assertTrue(fixture.actions.requestBankTravel(null));
            fixture.actions.dispatch();
            assertEquals(1, fixture.attempts);
            assertEquals("Ferox Enclave", fixture.destination);
        }
    }

    @Test
    public void rewardWindowMustBeObservedClosedBeforeRingTravel() throws Exception
    {
        Fixture fixture = wornRingFixture();
        fixture.position = TunnelMap.roomCenter(4);
        fixture.actions.requestBank();
        fixture.widgets.put(155 << 16, fixture.widget(-1));
        assertFalse(fixture.actions.requestBankTravel(null));
        fixture.widgets.remove(155 << 16);
        assertTrue(fixture.actions.requestBankTravel(null));
        fixture.widgets.put(155 << 16, fixture.widget(-1));
        fixture.actions.dispatch();
        assertEquals(0, fixture.attempts);
        fixture.widgets.remove(155 << 16);
        assertTrue(fixture.actions.requestBankTravel(null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.attempts);
    }

    @Test
    public void resetSessionCompletionAndUnavailablePlayerRejectQueuedRingRequests() throws Exception
    {
        for (int change = 0; change < 4; change++)
        {
            Fixture fixture = wornRingFixture();
            fixture.actions.requestBank();
            assertTrue(fixture.actions.requestBankTravel(null));
            if (change == 0)
            {
                fixture.actions.reset();
            }
            if (change == 1)
            {
                fixture.actions.newSession();
            }
            if (change == 2)
            {
                fixture.actions.completeTrip();
            }
            if (change == 3)
            {
                fixture.ready = false;
            }
            fixture.actions.dispatch();
            assertEquals(0, fixture.attempts);
        }
    }

    @Test
    public void wornRingEmergencyUsesTheSlotsFeroxEnclaveAction() throws Exception
    {
        Fixture fixture = wornRingFixture();
        assertTrue(fixture.actions.requestEmergencyTravel(null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.attempts);
        assertEquals("Ferox Enclave", fixture.destination);
    }

    @Test
    public void wornRingWidgetIdentityAndChargeAreRevalidated() throws Exception
    {
        Fixture fixture = wornRingFixture();
        fixture.actions.requestBank();
        Widget original = fixture.widgets.get(RING_WIDGET);
        assertTrue(fixture.actions.requestBankTravel(null));
        fixture.widgets.put(RING_WIDGET, fixture.widget(2552));
        fixture.actions.dispatch();
        assertEquals(0, fixture.attempts);
        fixture.widgets.put(RING_WIDGET, original);
        assertTrue(fixture.actions.requestBankTravel(null));
        fixture.equipment = new Item[] {new Item(2554, 1)};
        fixture.actions.dispatch();
        assertEquals(0, fixture.attempts);
    }

    @Test
    public void changedDuelingChargeStillSatisfiesConfiguredRingGear() throws Exception
    {
        Fixture fixture = wornRingFixture();
        fixture.equipment = new Item[] {new Item(2554, 1)};
        assertTrue(fixture.actions.gear("2552", null));
        assertEquals(0, fixture.attempts);
    }

    @Test
    public void feroxLandingAndBankAreDistinctAndRequireTheCorrectPlane() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = new WorldPoint(3151, 3636, 0);
        assertTrue(fixture.actions.atBankTravelArrival());
        assertFalse(fixture.actions.atSelectedBank());
        assertEquals(new WorldPoint(3130, 3632, 0), fixture.actions.bankDestination());
        fixture.position = new WorldPoint(3151, 3636, 1);
        assertFalse(fixture.actions.atBankTravelArrival());
    }

    @Test
    public void feroxRestorationRequiresFullRunAndEveryDepletedSkill() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.actions.feroxRestored());
        fixture.energy = 9999;
        assertFalse(fixture.actions.feroxRestored());
        fixture.energy = 100;
        assertFalse(fixture.actions.feroxRestored());
        fixture.energy = 10000;
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            fixture.boosted.put(skill, 98);
            assertFalse(fixture.actions.feroxRestored(), skill.name());
            fixture.boosted.put(skill, 99);
        }
        fixture.boosted.put(Skill.MAGIC, 110);
        assertTrue(fixture.actions.feroxRestored());
    }

    private static Fixture wornRingFixture() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.equipment = new Item[] {new Item(2552, 1)};
        fixture.widgets.put(RING_WIDGET, fixture.widget(2552));
        return fixture;
    }

    private static final class Fixture
    {
        private Item[] inventory = new Item[0];
        private Item[] equipment = new Item[0];
        private WorldPoint position = new WorldPoint(3565, 3315, 0);
        private boolean bankOpen;
        private boolean ready = true;
        private int visited = 1;
        private int energy = 10_000;
        private final Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
        private int attempts;
        private String destination;
        private final Map<Integer, Widget> widgets = new HashMap<>();
        private final BarrowsActions actions = new BarrowsActions()
        {
            @Override
            public boolean ready()
            {
                return ready;
            }

            @Override
            public boolean inside()
            {
                return Brother.inTomb(position) != null || TunnelMap.contains(position);
            }

            @Override
            public WorldPoint position()
            {
                return position;
            }

            @Override
            public boolean bankingOpen()
            {
                return bankOpen;
            }
        };

        private Fixture() throws Exception
        {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, call, arguments) ->
                {
                    switch (call.getName())
                    {
                        case "getItemContainer":
                            int id = (Integer) arguments[0];
                            return Proxy.newProxyInstance(ItemContainer.class.getClassLoader(), new Class<?>[] {ItemContainer.class},
                                (container, member, values) -> member.getName().equals("getItems") ? (id == 94 ? equipment : inventory) : null);
                        case "getWidget": return widgets.get(arguments.length == 1 ? (Integer) arguments[0]
                            : ((Integer) arguments[0] << 16) | (Integer) arguments[1]);
                        case "getVarbitValue": return visited;
                        case "getEnergy": return energy;
                        case "getRealSkillLevel": return 99;
                        case "getBoostedSkillLevel":
                            if (arguments[0] == Skill.OVERALL)
                            {
                                throw new AssertionError("OVERALL is not a restorable skill");
                            }
                            return boosted.getOrDefault((Skill) arguments[0], 99);
                        default: return null;
                    }
                });
            inject("client", client);
            inject("config", new BarrowsConfig()
            {
            });
            inject("api", new MicrobotApi()
            {
                @Override
                public boolean equippedBankRing(int id, String option)
                {
                    attempts++;
                    destination = option;
                    return true;
                }
            });
            inject("worker", new InteractionWorker((ClientThread) null)
            {
                @Override
                public boolean submit(BooleanSupplier valid, BooleanSupplier operation, Runnable onStarted)
                {
                    if (!valid.getAsBoolean())
                    {
                        return false;
                    }
                    onStarted.run();
                    return operation.getAsBoolean();
                }
            });
        }

        /** The live equipment slot: actions on the slot, the item on its second dynamic child. */
        private Widget widget(int item)
        {
            Widget[] children = {slotChild(-1), slotChild(item), slotChild(-1)};
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                (proxy, call, arguments) ->
                {
                    switch (call.getName())
                    {
                        case "getItemId": return -1;
                        case "getDynamicChildren": return children;
                        case "getActions": return new String[] {"Remove", "Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum", "Examine"};
                        case "isHidden": return false;
                        default: return null;
                    }
                });
        }

        private Widget slotChild(int item)
        {
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                (proxy, call, arguments) -> "getItemId".equals(call.getName()) ? item : "isHidden".equals(call.getName()) ? false : null);
        }

        private void inject(String name, Object value) throws Exception
        {
            Field field = BarrowsActions.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(actions, value);
        }
    }
}
