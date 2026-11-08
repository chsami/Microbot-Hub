package net.runelite.client.plugins.microbot.fourtifysgemmining;

import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApproachTest
{
    @Test
    public void nativeApproachLongerThanMiningDeadlineKeepsThePendingRequestAndCountsTheGem() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2819, 9380, 0);
        fixture.world.rock(2834, 9380);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        for (int x = 2820; x <= 2833; x++)
        {
            fixture.world.position = new WorldPoint(x, 9380, 0);
            fixture.tick();
            assertEquals("Approaching gem rock", fixture.plugin.getStatus());
            assertEquals(1, fixture.world.requests.size());
        }
        fixture.world.animation = 625;
        fixture.tick();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.tick();
        assertEquals(1, fixture.plugin.getGemsMined());
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void nativeBankApproachLongerThanOpeningDeadlineDoesNotRetryBeforeArrival() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2826, 9385, 0);
        fixture.world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        for (int x = 2827; x <= 2840; x++)
        {
            fixture.world.position = new WorldPoint(x, 9385, 0);
            fixture.tick();
            assertEquals("Approaching bank", fixture.plugin.getStatus());
            assertEquals(1, fixture.world.requests.size());
        }
        fixture.tick();
        assertEquals("Waiting for bank action", fixture.plugin.getStatus());
        assertEquals(1, fixture.world.requests.size());
        fixture.world.bankWithGems();
        fixture.tick();
        assertEquals(2, fixture.world.requests.size());
        assertEquals("Deposit-All", fixture.world.requests.get(1)[5]);
        assertTrue(fixture.plugin.getStatus().contains("Depositing"));
    }
}
