package net.runelite.client.plugins.microbot.anvilsmither;

import net.runelite.api.Quest;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilItem;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilLocation;
import net.runelite.client.plugins.microbot.anvilsmither.enums.Bars;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facts and rules that have each gone wrong once. Game facts are from the OSRS Wiki. */
class AnvilSmitherCheckTest {

    /** Hardcoding the plain hammer banked an Imcando hammer and never saw a worn one. */
    @Test
    void everyUsableHammerCounts() {
        assertTrue(AnvilSmitherScript.isHammer(ItemID.HAMMER));
        assertTrue(AnvilSmitherScript.isHammer(ItemID.IMCANDO_HAMMER));
        assertTrue(AnvilSmitherScript.isHammer(ItemID.IMCANDO_HAMMER_OFFHAND));
        assertFalse(AnvilSmitherScript.isHammer(ItemID.IMCANDO_HAMMER_BROKEN), "a broken hammer cannot smith");
        assertFalse(AnvilSmitherScript.isHammer(ItemID.MITHRIL_BAR));
        assertEquals(3, AnvilSmitherScript.HAMMER_IDS.length);
    }

    @Test
    void depositKeepsTheHammerAndTheMaterial() {
        for (int hammer : AnvilSmitherScript.HAMMER_IDS) {
            assertTrue(AnvilSmitherScript.keepsItem(false, ItemID.MITHRIL_BAR, null, hammer, "Hammer"));
            // one is worn already: a carried hammer is a wasted slot
            assertFalse(AnvilSmitherScript.keepsItem(true, ItemID.MITHRIL_BAR, null, hammer, "Hammer"));
        }
        assertTrue(AnvilSmitherScript.keepsItem(true, ItemID.MITHRIL_BAR, null, ItemID.MITHRIL_BAR, "Mithril bar"));
        assertFalse(AnvilSmitherScript.keepsItem(false, ItemID.MITHRIL_BAR, null, ItemID.IRON_BAR, "Iron bar"));
    }

    /** Large keels eat regular keel parts; the product's name contains the material's name. */
    @Test
    void largeKeelsKeepTheirMaterialNotTheirProduct() {
        assertTrue(AnvilSmitherScript.keepsItem(true, 0, "Bronze keel parts", 1, "Bronze keel parts"));
        assertFalse(AnvilSmitherScript.keepsItem(true, 0, "Bronze keel parts", 2, "Large bronze keel parts"));
    }

    @Test
    void theInterfaceEntryIsTheExactItem() {
        assertEquals(1, AnvilSmitherScript.pickMatch(
                Arrays.asList("Large bronze keel parts", "Bronze keel parts"), "Bronze keel parts"));
        assertEquals(0, AnvilSmitherScript.pickMatch(
                Arrays.asList("Large bronze keel parts", "Bronze keel parts"), "Large bronze keel parts"));
        // the interface may label it differently from the item: one match is the match
        assertEquals(0, AnvilSmitherScript.pickMatch(List.of("Lantern frame"), "Oil lantern frame"));
        assertEquals(-1, AnvilSmitherScript.pickMatch(List.of(), "Iron spit"));
    }

    /** The keel metal word is NOT the bar name minus " bar". */
    @Test
    void keelNamesUseTheGameMetalWord() {
        assertEquals("Adamant keel parts", AnvilSmitherScript.effectiveItemName(AnvilItem.KEEL_PARTS, Bars.ADAMANTITE));
        assertEquals("Rune keel parts", AnvilSmitherScript.effectiveItemName(AnvilItem.KEEL_PARTS, Bars.RUNITE));
        assertEquals("Large adamant keel parts",
                AnvilSmitherScript.effectiveItemName(AnvilItem.LARGE_KEEL_PARTS, Bars.ADAMANTITE));
        assertEquals("Scimitar", AnvilSmitherScript.effectiveItemName(AnvilItem.SCIMITAR, Bars.RUNITE));
        assertEquals("Rune scimitar", AnvilSmitherScript.displayName(AnvilItem.SCIMITAR, Bars.RUNITE));
        assertEquals("Adamant keel parts", AnvilSmitherScript.displayName(AnvilItem.KEEL_PARTS, Bars.ADAMANTITE));
        assertEquals("Iron spit", AnvilSmitherScript.displayName(AnvilItem.IRON_SPIT, Bars.IRON));
    }

    /** The level depends on the item, not only the bar: a level 85 player cannot make rune platebodies. */
    @Test
    void levelsArePerItem() {
        assertEquals(1, AnvilItem.DAGGER.levelFor(Bars.BRONZE));
        assertEquals(5, AnvilItem.SCIMITAR.levelFor(Bars.BRONZE));
        assertEquals(18, AnvilItem.PLATE_BODY.levelFor(Bars.BRONZE));
        assertEquals(20, AnvilItem.SCIMITAR.levelFor(Bars.IRON));
        assertEquals(23, AnvilItem.LIMBS.levelFor(Bars.IRON));
        assertEquals(48, AnvilItem.PLATE_BODY.levelFor(Bars.STEEL));
        assertEquals(56, AnvilItem.LIMBS.levelFor(Bars.MITHRIL));
        assertEquals(68, AnvilItem.PLATE_BODY.levelFor(Bars.MITHRIL));
        assertEquals(88, AnvilItem.PLATE_BODY.levelFor(Bars.ADAMANTITE));
        assertEquals(90, AnvilItem.SCIMITAR.levelFor(Bars.RUNITE));
        assertEquals(99, AnvilItem.TWO_HAND_SWORD.levelFor(Bars.RUNITE));
        assertEquals(99, AnvilItem.PLATE_BODY.levelFor(Bars.RUNITE), "held at 99");
        assertEquals(4, AnvilItem.BRONZE_WIRE.levelFor(Bars.BRONZE));
        assertEquals(17, AnvilItem.IRON_SPIT.levelFor(Bars.IRON));
        assertEquals(26, AnvilItem.OIL_LAMP.levelFor(Bars.IRON));
        assertEquals(36, AnvilItem.STEEL_STUDS.levelFor(Bars.STEEL));
        assertEquals(49, AnvilItem.BULLSEYE_LAMP.levelFor(Bars.STEEL));
    }

    @Test
    void keelTiers() {
        int[] levels = {10, 22, 38, 56, 74, 86};
        Bars[] bars = Bars.values();
        assertEquals(levels.length, bars.length, "dragon keels are Dragon Forge only, silver and gold make nothing here");
        for (int i = 0; i < bars.length; i++) {
            assertEquals(levels[i], AnvilItem.KEEL_PARTS.levelFor(bars[i]));
            assertEquals(levels[i], AnvilItem.LARGE_KEEL_PARTS.levelFor(bars[i]));
        }
        assertEquals(5, AnvilItem.KEEL_PARTS.getRequiredBars());
        assertEquals(5, AnvilItem.LARGE_KEEL_PARTS.getRequiredBars());
    }

    /** Wire is bronze, spit and lantern frame iron, studs and bullseye steel: refuse anything else up front. */
    @Test
    void oneMetalItemsAreRefusedForOtherBars() {
        assertNull(AnvilSmitherScript.rejectionReason(Bars.BRONZE, AnvilItem.BRONZE_WIRE));
        assertNotNull(AnvilSmitherScript.rejectionReason(Bars.IRON, AnvilItem.BRONZE_WIRE));
        assertNull(AnvilSmitherScript.rejectionReason(Bars.IRON, AnvilItem.IRON_SPIT));
        assertNull(AnvilSmitherScript.rejectionReason(Bars.IRON, AnvilItem.OIL_LAMP));
        assertNotNull(AnvilSmitherScript.rejectionReason(Bars.STEEL, AnvilItem.OIL_LAMP));
        assertNull(AnvilSmitherScript.rejectionReason(Bars.STEEL, AnvilItem.STEEL_STUDS));
        assertNull(AnvilSmitherScript.rejectionReason(Bars.STEEL, AnvilItem.BULLSEYE_LAMP));
        assertNotNull(AnvilSmitherScript.rejectionReason(Bars.RUNITE, AnvilItem.BULLSEYE_LAMP));
        for (Bars bar : Bars.values()) {
            assertNull(AnvilSmitherScript.rejectionReason(bar, AnvilItem.SCIMITAR));
            assertNull(AnvilSmitherScript.rejectionReason(bar, AnvilItem.LARGE_KEEL_PARTS));
        }
    }

    /** The script walks to one tile and clicks the other, so each pair must be within reach. */
    @Test
    void locationsAreBankside() {
        for (AnvilLocation loc : AnvilLocation.values()) {
            assertTrue(loc.getAnvil().distanceTo(loc.getBank()) <= 12, loc.toString());
            assertEquals(0, loc.getAnvil().getPlane());
            assertEquals(0, loc.getBank().getPlane());
        }
        assertNull(AnvilLocation.VARROCK_WEST.getRequiredQuest());
        assertEquals(Quest.SONG_OF_THE_ELVES, AnvilLocation.PRIFDDINAS_SOUTH.getRequiredQuest());
    }

    /** The camera must face ACROSS the bank-to-anvil corridor, not along it. */
    @Test
    void cameraFramesAcrossTheCorridor() {
        for (AnvilLocation loc : AnvilLocation.values()) {
            int[] yaws = AnvilSmitherScript.framingYaws(loc.getBank(), loc.getAnvil());
            assertEquals(2, yaws.length);
            assertEquals(1024, Math.abs(yaws[0] - yaws[1]));
        }
        assertEquals(1536, AnvilSmitherScript.framingYaws(
                AnvilLocation.VARROCK_WEST.getBank(), AnvilLocation.VARROCK_WEST.getAnvil())[0]);
        assertEquals(0, AnvilSmitherScript.framingYaws(
                AnvilLocation.PRIFDDINAS_SOUTH.getBank(), AnvilLocation.PRIFDDINAS_SOUTH.getAnvil())[0]);
    }

    /** Never instant, never a fixed beat, and a slow session is slower across the board. */
    @Test
    void reactionsAreNeverInstantAndVary() {
        Random r = new Random(7);
        AnvilHabits habits = AnvilHabits.draw(r);
        long[] samples = new long[5000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = habits.reactionMs(r, 450);
            assertTrue(samples[i] >= AnvilHabits.REACT_FLOOR_MS);
            assertTrue(samples[i] <= 450 * 5);
            assertTrue(habits.reactionMs(r, 1) >= AnvilHabits.REACT_FLOOR_MS);
        }
        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        assertTrue(median > 300 && median < 700, "median " + median);
        assertTrue(Arrays.stream(samples).distinct().count() > 200);
        // right-skewed like a person: the slow tail reaches further from the middle than the fast one
        assertTrue(samples[samples.length * 99 / 100] - median > median - samples[samples.length / 100]);
    }

    /** After a load the player is usually back in a second or two, sometimes much later, never at once. */
    @Test
    void theGapAfterALoadIsSpreadOut() {
        Random r = new Random(11);
        AnvilHabits habits = AnvilHabits.draw(r);
        int quick = 0, slow = 0, shortSlow = 0, n = 5000;
        for (int i = 0; i < n; i++) {
            long ms = habits.loadEndDelayMs(r, false, 60_000);
            assertTrue(ms >= 300 && ms <= 45_000);   // watching band 300-1,100
            if (ms < 1_500) quick++;
            if (ms >= 8_000) slow++;
            assertTrue(habits.loadEndDelayMs(r, true, 60_000) >= 1_500, "a cursor off the window is not an instant return");
            if (habits.loadEndDelayMs(r, false, 15_000) >= 1_500) shortSlow++;
            assertTrue(habits.loadEndDelayMs(r, r.nextBoolean(), 15_000) < 8_000, "nobody walks away from a 15 second load");
        }
        assertTrue(shortSlow < n * 15 / 100, "a 15 second load rarely ends in a stall: " + shortSlow);
        assertTrue(quick > n * 35 / 100 && quick < n * 80 / 100, "quick " + quick);
        assertTrue(slow > n / 100 && slow < n * 25 / 100, "slow " + slow);
    }

    @Test
    void sessionsDiffer() {
        Random r = new Random(3);
        String first = AnvilHabits.draw(r).describe();
        assertFalse(first.equals(AnvilHabits.draw(r).describe()));
    }

    /** Read live 2026-11-18: adamantite plate body selected gave bar 5, item 14. */
    @Test
    void spaceIsOnlyTrustedWhenTheGameRemembersThisItem() {
        int adamant = Bars.ADAMANTITE.ordinal() + 1, plateBody = AnvilItem.PLATE_BODY.getChildId();
        assertTrue(AnvilSmitherScript.rememberedMatches(5, 14, adamant, plateBody));
        assertFalse(AnvilSmitherScript.rememberedMatches(6, 14, adamant, plateBody), "another metal");
        assertFalse(AnvilSmitherScript.rememberedMatches(5, 13, adamant, plateBody), "another item");
        assertFalse(AnvilSmitherScript.rememberedMatches(5, 14, adamant, -1), "name-matched items are clicked");
    }

    @Test
    void aimStaysInsideTheTargetAndIsSpreadOut() {
        java.awt.Rectangle box = new java.awt.Rectangle(400, 350, 30, 24);
        int minX = Integer.MAX_VALUE, maxX = 0;
        for (int i = 0; i < 200; i++) {
            java.awt.Point p = AnvilSmitherScript.pointInside(box);
            assertTrue(box.contains(p));
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
        }
        assertTrue(maxX - minX >= 15, "clicks bunch in " + (maxX - minX) + " px");
    }

    @Test
    void productsAreKnownByTheGameName() {
        assertTrue(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.PLATE_BODY, "Adamant platebody"));
        assertTrue(AnvilSmitherScript.isProductName(Bars.RUNITE, AnvilItem.TWO_HAND_SWORD, "Rune 2h sword"));
        assertTrue(AnvilSmitherScript.isProductName(Bars.RUNITE, AnvilItem.LIMBS, "Runite limbs"));
        assertTrue(AnvilSmitherScript.isProductName(Bars.IRON, AnvilItem.IRON_SPIT, "Iron spit"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.SWORD, "Adamant longsword"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.PLATE_BODY, "Adamant platebody (g)"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.PLATE_BODY, "Rune platebody"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.KEEL_PARTS, "Adamant keel parts"));
        // stackables: one nature rune per nail or tip would cost far more than the alch pays
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.NAILS, "Adamantite nails"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.ADAMANTITE, AnvilItem.BOLTS, "Adamant bolts(unf)"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.RUNITE, AnvilItem.KNIVES, "Rune knife"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.RUNITE, AnvilItem.ARROWTIPS, "Rune arrowtips"));
        assertFalse(AnvilSmitherScript.isProductName(Bars.RUNITE, AnvilItem.DART_TIPS, "Rune dart tip"));
    }

    /** A break never cuts a load short, and is taken at once otherwise. */
    @Test
    void breaksWaitForTheLoad() {
        assertFalse(AnvilSmitherScript.holdsForBreak(true, true));
        assertTrue(AnvilSmitherScript.holdsForBreak(true, false));
        assertFalse(AnvilSmitherScript.holdsForBreak(false, false));
    }

    @Test
    void loggingBackInIsUnhurriedAndBacksOff() {
        assertEquals(5_000, AnvilSmitherScript.reloginWaitMs(0));
        assertEquals(40_000, AnvilSmitherScript.reloginWaitMs(1));
        assertTrue(AnvilSmitherScript.reloginGapMs(2, 0.5) > AnvilSmitherScript.reloginGapMs(1, 0.5));
        assertEquals(AnvilSmitherScript.reloginGapMs(8, 0.5), AnvilSmitherScript.reloginGapMs(20, 0.5), "capped");
        long total = 0;
        for (int a = 1; a <= 10; a++) total += AnvilSmitherScript.reloginGapMs(a, 1);
        assertTrue(total < 15 * 60_000, "gives up within a quarter of an hour");
    }

    @Test
    void alchSpellFollowsTheMagicLevel() {
        assertNull(AnvilSmitherScript.alchSpell(20));
        assertEquals(net.runelite.client.plugins.microbot.util.magic.Rs2Spells.LOW_LEVEL_ALCHEMY, AnvilSmitherScript.alchSpell(21));
        assertEquals(net.runelite.client.plugins.microbot.util.magic.Rs2Spells.LOW_LEVEL_ALCHEMY, AnvilSmitherScript.alchSpell(54));
        assertEquals(net.runelite.client.plugins.microbot.util.magic.Rs2Spells.HIGH_LEVEL_ALCHEMY, AnvilSmitherScript.alchSpell(55));
    }
}
