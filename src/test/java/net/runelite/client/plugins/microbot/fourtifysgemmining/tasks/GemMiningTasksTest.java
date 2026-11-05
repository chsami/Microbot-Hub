package net.runelite.client.plugins.microbot.fourtifysgemmining.tasks;

import java.util.Arrays;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GemMiningTasksTest
{
    @Test
    public void miningWaitsForObservationAndPausesAfterThreeDispatchedRequests()
    {
        Fixture fixture = new Fixture();
        fixture.world.rock(2825, 3000);
        assertEquals("Starting mining", fixture.mine(0));
        fixture.mine(9);
        assertEquals(1, fixture.world.requests.size());
        fixture.mine(10);
        fixture.mine(20);
        assertTrue(fixture.mine(30).contains("three attempts"));
        fixture.mine(100);
        assertEquals(3, fixture.world.requests.size());
        fixture.mining.reset();
        assertEquals("Starting mining", fixture.mine(101));
        assertEquals(4, fixture.world.requests.size());
    }

    @Test
    public void animationPreventsRepeatedClickingAndGemObservationReleasesTarget()
    {
        Fixture fixture = new Fixture();
        fixture.world.rock(2825, 3000);
        fixture.mine(0);
        fixture.world.animation = 625;
        assertEquals("Mining gem rocks", fixture.mine(20));
        assertEquals(1, fixture.world.requests.size());
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.mine(21);
        assertEquals(1, fixture.world.requests.size());
        fixture.world.animation = -1;
        assertEquals("Starting mining", fixture.mine(22));
        assertEquals(2, fixture.world.requests.size());
    }

    @Test
    public void nonAdjacentRockWithinRangeRequestsTheNativeMiningAction()
    {
        Fixture fixture = new Fixture();
        fixture.world.rock(2829, 3000);
        assertEquals("Starting mining", fixture.mine(0));
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void noRockWithinTwentyTilesPromptsCloserWithoutAnOutOfRangeRequest()
    {
        Fixture fixture = new Fixture(GemMine.UNDERGROUND);
        fixture.world.position = new WorldPoint(2819, 9380, 0);
        fixture.world.rock(2840, 9380);
        assertEquals("Walk closer to gem rocks or wait for a respawn", fixture.mine(0));
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.objects.clear();
        assertEquals("Walk closer to gem rocks or wait for a respawn", fixture.mine(1));
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void bankLatchPersistsUntilEveryGemIsGoneAndBankClosed()
    {
        Fixture fixture = new Fixture();
        fixture.world.fullInventory();
        fixture.world.bankWithGems();
        assertTrue(fixture.banking.isValid());
        assertEquals("Depositing uncut gems", fixture.bank(0));
        fixture.bank(7);
        assertEquals(1, fixture.world.requests.size());
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1621, 1)};
        fixture.world.bankWithGems();
        assertTrue(fixture.banking.isValid());
        assertEquals("Depositing uncut gems", fixture.bank(8));
        assertEquals(2, fixture.world.requests.size());
        fixture.world.inventory = new Item[] {new Item(1265, 1)};
        fixture.world.bankWithGems();
        assertEquals("Closing bank", fixture.bank(9));
        assertEquals("Close", fixture.world.requests.get(2)[5]);
        fixture.world.widgets.clear();
        fixture.world.roots = new Widget[0];
        assertTrue(fixture.bank(10).contains("Walk closer to"));
        assertFalse(fixture.banking.isValid());
        assertEquals(3, fixture.world.requests.size());
    }

    @Test
    public void failedDispatchedDepositsAreNotSuccessAndRetriesAreBounded()
    {
        Fixture fixture = new Fixture();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.world.bankWithGems();
        fixture.bank(0);
        fixture.bank(8);
        fixture.bank(16);
        assertTrue(fixture.bank(24).contains("after three tries"));
        fixture.bank(100);
        assertEquals(3, fixture.world.requests.size());
    }

    @Test
    public void fullInventoryOfToolsNeverDepositsOrDropsThem()
    {
        Fixture fixture = new Fixture();
        fixture.world.inventory = new Item[28];
        Arrays.fill(fixture.world.inventory, new Item(1265, 1));
        assertTrue(fixture.banking.isValid());
        assertTrue(fixture.bank(0).contains("other items"));
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void rejectedBankOpeningHasItsOwnBoundedObservationWindow()
    {
        Fixture fixture = new Fixture(GemMine.UNDERGROUND);
        fixture.world.position = new WorldPoint(2840, 9385, 0);
        fixture.world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        fixture.world.fullInventory();
        assertTrue(fixture.banking.isValid());
        assertEquals("Opening bank", fixture.bank(0));
        fixture.bank(7);
        assertEquals(1, fixture.world.requests.size());
        fixture.bank(8);
        fixture.bank(16);
        assertTrue(fixture.bank(24).contains("after three tries"));
        fixture.bank(100);
        assertEquals(3, fixture.world.requests.size());
    }

    @Test
    public void bankPinPromptIsEnteredWithoutCountingAsAFailedOpen()
    {
        Fixture fixture = new Fixture(GemMine.UNDERGROUND);
        fixture.world.position = new WorldPoint(2840, 9385, 0);
        fixture.world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        fixture.world.fullInventory();
        assertTrue(fixture.banking.isValid());
        assertEquals("Opening bank", fixture.bank(0));
        fixture.world.widgets.put(213 << 16, fixture.world.widget(213 << 16, -1, -1, null, null));
        long before = fixture.world.actions.getRequestsDispatched();
        for (int tick = 1; tick < 60; tick++)
        {
            assertTrue(fixture.bank(tick).contains("bank PIN"), "A PIN prompt must not stop banking");
        }
        assertTrue(fixture.world.actions.getRequestsDispatched() > before, "The PIN is entered from the action worker");
        assertFalse(fixture.banking.isPaused());
        fixture.world.widgets.remove(213 << 16);
        fixture.world.depositBoxWithGems();
        assertEquals("Depositing uncut gems", fixture.bank(60));
    }

    @Test
    public void undispatchedMiningRequestsDoNotConsumeFailureAttempts()
    {
        Fixture fixture = new Fixture();
        fixture.world.rock(2825, 3000);
        for (int tick = 0; tick < 40; tick++)
        {
            fixture.plugin.tick = tick;
            fixture.world.actions.nextTick();
            fixture.mining.execute();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertFalse(fixture.plugin.getStatus().contains("three attempts"));
        fixture.world.actions.dispatch();
        assertEquals(1, fixture.world.requests.size());
        fixture.mine(48);
        assertEquals(1, fixture.world.requests.size());
        fixture.mine(49);
        assertEquals(2, fixture.world.requests.size());
    }

    @Test
    public void undispatchedDepositsDoNotConsumeFailureAttempts()
    {
        Fixture fixture = new Fixture();
        fixture.world.inventory = new Item[] {new Item(1625, 1)};
        fixture.world.bankWithGems();
        for (int tick = 0; tick < 40; tick++)
        {
            fixture.plugin.tick = tick;
            fixture.world.actions.nextTick();
            fixture.banking.execute();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertFalse(fixture.plugin.getStatus().contains("after three tries"));
        fixture.world.actions.dispatch();
        assertEquals(1, fixture.world.requests.size());
        fixture.bank(46);
        assertEquals(1, fixture.world.requests.size());
        fixture.bank(47);
        assertEquals(2, fixture.world.requests.size());
    }

    @Test
    public void revalidationRejectionsDoNotConsumeMiningFailureBudget()
    {
        Fixture fixture = new Fixture();
        fixture.world.rock(2825, 3000);
        for (int tick = 0; tick < 40; tick++)
        {
            fixture.plugin.tick = tick;
            fixture.world.actions.nextTick();
            fixture.mining.execute();
            fixture.world.animation = 625;
            fixture.world.actions.dispatch();
            fixture.world.animation = -1;
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertFalse(fixture.mining.isPending());
        assertEquals("Starting mining", fixture.mine(40));
        assertEquals(1, fixture.world.requests.size());
        fixture.mine(50);
        fixture.mine(60);
        assertTrue(fixture.mine(70).contains("three attempts"));
        assertEquals(3, fixture.world.requests.size());
    }

    private static final class TickPlugin extends FourtifysGemMiningPlugin
    {
        private int tick;

        @Override
        public int getTick()
        {
            return tick;
        }
    }

    private static final class Fixture
    {
        private final TestWorld world = new TestWorld();
        private final TickPlugin plugin = new TickPlugin();
        private final MineGemsTask mining;
        private final BankGemsTask banking;

        private Fixture()
        {
            this(GemMine.SURFACE);
        }

        private Fixture(GemMine mine)
        {
            GemMiningConfig config = new GemMiningConfig()
            {
                @Override
                public GemMine mine()
                {
                    return mine;
                }
            };
            mining = new MineGemsTask(plugin, world.client, config, world.actions);
            banking = new BankGemsTask(plugin, world.client, config, world.actions, new GemBagTask(plugin, world.actions));
        }

        private String mine(int tick)
        {
            plugin.tick = tick;
            world.actions.nextTick();
            mining.execute();
            world.actions.dispatch();
            return plugin.getStatus();
        }

        private String bank(int tick)
        {
            plugin.tick = tick;
            world.actions.nextTick();
            banking.execute();
            world.actions.dispatch();
            return plugin.getStatus();
        }
    }
}
