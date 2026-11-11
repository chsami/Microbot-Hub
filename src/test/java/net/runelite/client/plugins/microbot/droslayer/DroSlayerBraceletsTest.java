package net.runelite.client.plugins.microbot.droslayer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import org.junit.jupiter.api.Test;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerBraceletsTest {
    private static final Gson GSON = new Gson();

    private DroSlayerTaskProfileJson slaughter(boolean wilderness) {
        var profile = new DroSlayerTaskProfileJson();
        if (wilderness) profile.setWildernessSlaughterBracelet(true);
        else profile.setSlaughterBracelet(true);
        return profile;
    }

    private static final class Bank implements DroSlayerBracelets.Supplies {
        boolean open = true, worn, failWithdraw, failEquip, failDeposit;
        int carried, stock = 100, slots = 3, withdrawals, equips, food, deposits;
        long session = 1L;
        final DroSlayerBracelets.BankAttemptBudget budget = new DroSlayerBracelets.BankAttemptBudget();
        public DroSlayerBracelets.BankAttemptBudget attempts() { return budget; }
        public long bankSession() { return session; }
        public boolean depositOneFood() {
            deposits++;
            if (food <= 0 || failDeposit) return false;
            food--; slots++;
            return true;
        }
        public boolean bankOpen() { return open; }
        public boolean worn(String name) { return worn; }
        public int carried(String name) { return carried; }
        public int stock(String name) { return stock; }
        public int emptySlots() { return slots; }
        public boolean withdrawOne(String name) {
            withdrawals++;
            if (failWithdraw) return false;
            stock--; carried++; slots--;
            return true;
        }
        public boolean equip(String name) {
            equips++;
            if (failEquip) return false;
            carried--; slots++; worn = true;
            return true;
        }
    }

    @Test public void oldAndNewProfilesLeaveEveryBraceletDisabledByDefault() {
        for (var profile : new DroSlayerTaskProfileJson[] {
                new DroSlayerTaskProfileJson(), GSON.fromJson("{\"setup\":\"melee\"}", DroSlayerTaskProfileJson.class) }) {
            assertFalse(profile.isSlaughterBracelet());
            assertFalse(profile.isExpeditiousBracelet());
            assertFalse(profile.isWildernessSlaughterBracelet());
            assertFalse(profile.isWildernessExpeditiousBracelet());
            assertNull(DroSlayerBracelets.requestedBracelet(profile, false));
            assertNull(DroSlayerBracelets.requestedBracelet(profile, true));
        }
    }

    @Test public void selectionsAreExclusiveWithinEachIndependentContext() {
        var profile = slaughter(false);
        profile.setWildernessExpeditiousBracelet(true);
        assertEquals(DroSlayerBracelets.SLAUGHTER, DroSlayerBracelets.requestedBracelet(profile, false));
        assertEquals(DroSlayerBracelets.EXPEDITIOUS, DroSlayerBracelets.requestedBracelet(profile, true));
        profile.setExpeditiousBracelet(true);
        assertFalse(profile.isSlaughterBracelet());
        profile.setWildernessSlaughterBracelet(true);
        assertFalse(profile.isWildernessExpeditiousBracelet());
        profile.setExpeditiousBracelet(false);
        assertNull(DroSlayerBracelets.requestedBracelet(profile, false));
        assertEquals(DroSlayerBracelets.SLAUGHTER, DroSlayerBracelets.requestedBracelet(profile, true));
    }

    @Test public void conflictingHandEditedFlagsDoNotGuessARequiredBracelet() {
        var profile = GSON.fromJson("{\"slaughterBracelet\":true,\"expeditiousBracelet\":true}", DroSlayerTaskProfileJson.class);
        assertNull(DroSlayerBracelets.requestedBracelet(profile, false));
    }

    @Test public void checkboxPairCanSelectOneOrNeither() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JCheckBox first = new JCheckBox(), second = new JCheckBox();
            DroSlayerPrayerEditor.mutuallyExclusive(first, second);
            first.doClick(); assertTrue(first.isSelected()); assertFalse(second.isSelected());
            second.doClick(); assertFalse(first.isSelected()); assertTrue(second.isSelected());
            second.doClick(); assertFalse(first.isSelected()); assertFalse(second.isSelected());
        });
    }

    @Test public void naturalBankTripEquipsOneAndWithdrawsAtMostOneSpare() {
        Bank bank = new Bank();
        assertTrue(DroSlayerBracelets.ensureAtBank(slaughter(true), true, bank));
        assertEquals(2, bank.withdrawals);
        assertEquals(1, bank.equips);
        assertEquals(1, bank.carried);
        assertEquals(98, bank.stock);
        assertTrue(DroSlayerBracelets.ensureAtBank(slaughter(true), true, bank));
        assertEquals(2, bank.withdrawals);
        assertEquals(1, bank.equips);
    }

    @Test public void primaryGearStageDefersSpareUntilRequiredSuppliesAreReady() {
        Bank bank = new Bank();
        assertTrue(DroSlayerBracelets.prepareAtBank(slaughter(false), false, bank, false));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.carried);
        bank.slots = 0; // Required food/protection/travel takes the remaining space.
        assertTrue(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.carried);
    }

    @Test public void lastBraceletIsUsableWithoutRequiringASpare() {
        Bank bank = new Bank(); bank.stock = 1;
        assertTrue(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.carried);
        assertEquals(0, bank.stock);
    }

    @Test public void missingStockFullInventoryOrWrongContextNeverForcesBankingWork() {
        Bank bank = new Bank(); bank.stock = 0;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        bank.stock = 100; bank.slots = 0;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        bank.slots = 3;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), true, bank));
        bank.open = false;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(0, bank.withdrawals);
        assertEquals(0, bank.equips);
    }

    @Test public void unobservedWithdrawalOrEquipmentStopsOptionalWorkWithoutRetrying() {
        Bank bank = new Bank(); bank.failWithdraw = true;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.equips);
        bank = new Bank(); bank.failEquip = true;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.withdrawals);
        assertEquals(1, bank.equips);
        assertEquals(1, bank.carried);
    }

    @Test public void editorSavePreservesOtherTasksAndUnknownLatestFields() throws Exception {
        Path directory = Files.createTempDirectory("slayer-bracelet-test-");
        Path file = directory.resolve("profiles.json");
        try {
            Files.writeString(file, "{\"greater demons\":{\"setup\":\"melee\",\"futureField\":17},\"suqahs\":{\"prayer\":\"pmage\"}}", StandardCharsets.UTF_8);
            var selection = slaughter(true);
            selection.setExpeditiousBracelet(true);
            DroSlayerPrayerEditor.saveTaskSettings(file, "greater demons", "PROTECT_MELEE", "PIETY", selection);
            JsonObject saved = GSON.fromJson(Files.readString(file), JsonObject.class);
            JsonObject greater = saved.getAsJsonObject("greater demons");
            assertEquals("melee", greater.get("setup").getAsString());
            assertEquals(17, greater.get("futureField").getAsInt());
            assertEquals("pmage", saved.getAsJsonObject("suqahs").get("prayer").getAsString());
            assertFalse(greater.get("slaughterBracelet").getAsBoolean());
            assertTrue(greater.get("expeditiousBracelet").getAsBoolean());
            assertTrue(greater.get("wildernessSlaughterBracelet").getAsBoolean());
            assertFalse(greater.get("wildernessExpeditiousBracelet").getAsBoolean());
            assertEquals("PIETY", greater.get("offensivePrayer").getAsString());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }

    @Test public void deletedTaskDoesNotOverwriteProfilesOnSave() throws Exception {
        Path directory = Files.createTempDirectory("slayer-bracelet-test-");
        Path file = directory.resolve("profiles.json");
        try {
            Files.writeString(file, "{}", StandardCharsets.UTF_8);
            try {
                DroSlayerPrayerEditor.saveTaskSettings(file, "greater demons", "NONE", "AUTOMATIC", slaughter(true));
                fail("Expected missing task error");
            } catch (java.io.IOException expected) { assertEquals("{}", Files.readString(file)); }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }

    @Test public void fullInventoryBanksExactlyOneFoodForThePrimaryBracelet() {
        Bank bank = new Bank(); bank.slots = 0; bank.food = 10;
        assertTrue(DroSlayerBracelets.prepareAtBank(slaughter(false), false, bank, false));
        assertEquals(1, bank.deposits);
        assertEquals(9, bank.food);
        assertEquals(1, bank.withdrawals);
        assertTrue(bank.worn);
    }

    @Test public void fullInventoryCanBankOneFoodForOneOptionalSpare() {
        Bank bank = new Bank(); bank.slots = 0; bank.food = 10; bank.worn = true;
        assertTrue(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.deposits);
        assertEquals(1, bank.withdrawals);
        assertEquals(1, bank.carried);
        assertEquals(9, bank.food);
    }

    @Test public void failedFullInventoryDepositNeverLoopsDuringSameBankOpening() {
        Bank bank = new Bank(); bank.slots = 0; bank.food = 10; bank.failDeposit = true;
        for (int i = 0; i < 20; i++)
            assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.deposits);
        assertEquals(0, bank.withdrawals);
        assertEquals(10, bank.food);
        bank.session++; bank.failDeposit = false;
        assertTrue(DroSlayerBracelets.prepareAtBank(slaughter(false), false, bank, false));
        assertEquals(2, bank.deposits);
        assertEquals(1, bank.withdrawals);
    }

    @Test public void failedWithdrawalAfterFoodDepositNeverDepositsMoreFoodOrRetries() {
        Bank bank = new Bank(); bank.slots = 0; bank.food = 10; bank.failWithdraw = true;
        for (int i = 0; i < 20; i++)
            assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(1, bank.deposits);
        assertEquals(1, bank.withdrawals);
        assertEquals(9, bank.food);
    }

    @Test public void missingBraceletStockNeverDepositsFood() {
        Bank bank = new Bank(); bank.slots = 0; bank.food = 10; bank.stock = 0;
        assertFalse(DroSlayerBracelets.ensureAtBank(slaughter(false), false, bank));
        assertEquals(0, bank.deposits);
        assertEquals(10, bank.food);
    }
}
