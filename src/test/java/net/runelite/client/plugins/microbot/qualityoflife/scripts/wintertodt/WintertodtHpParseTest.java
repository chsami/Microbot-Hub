package net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WintertodtHpParseTest {
    @Test
    void parsesEnergyPercentage() {
        assertEquals(45, WintertodtScript.parseWintertodtHp("Wintertodt's Energy: 45%"));
        assertEquals(100, WintertodtScript.parseWintertodtHp("Wintertodt's Energy: 100%"));
        assertEquals(0, WintertodtScript.parseWintertodtHp("Wintertodt's Energy: 0%"));
    }

    @Test
    void parsesTextStartingWithDigits() {
        assertEquals(72, WintertodtScript.parseWintertodtHp("72%"));
    }

    @Test
    void returnsMinusOneWhenTextHasNoDigits() {
        assertEquals(-1, WintertodtScript.parseWintertodtHp(""));
        assertEquals(-1, WintertodtScript.parseWintertodtHp("Wintertodt's Energy"));
        assertEquals(-1, WintertodtScript.parseWintertodtHp(null));
    }
}
