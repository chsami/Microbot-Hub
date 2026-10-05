package net.runelite.client.plugins.microbot.cooking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cooking.enums.CookingLocation;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingLocationTest {
    @Test
    void mythsGuildRangeIsOnBankFloor() {
        WorldPoint range = CookingLocation.MYTHS_GUILD.getCookingObjectWorldPoint();
        WorldPoint bank = BankLocation.MYTHS_GUILD.getWorldPoint();

        assertEquals(new WorldPoint(2466, 2848, 1), range);
        assertEquals(bank.getPlane(), range.getPlane());
        assertTrue(range.distanceTo(bank) <= 3);
    }
}
