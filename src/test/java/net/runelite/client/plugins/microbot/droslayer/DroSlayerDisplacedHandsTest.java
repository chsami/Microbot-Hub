package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DroSlayerDisplacedHandsTest {
    private static final int GLOVES = 7460;

    private static class Bank implements DroSlayerBracelets.Supplies {
        boolean open = true, equipped, confirmEquip = true, depositSucceeds = true;
        int hands = GLOVES, oldGloves, bracelets = 1, deposits, depositedId;
        long session = 1;
        final DroSlayerBracelets.BankAttemptBudget budget = new DroSlayerBracelets.BankAttemptBudget();
        public boolean bankOpen() { return open; }
        public long bankSession() { return session; }
        public DroSlayerBracelets.BankAttemptBudget attempts() { return budget; }
        public boolean worn(String name) { return equipped; }
        public int carried(String name) { return bracelets; }
        public int stock(String name) { return 0; }
        public int emptySlots() { return 3; }
        public boolean withdrawOne(String name) { return false; }
        public boolean depositOneFood() { fail("Existing food must not be deposited"); return false; }
        public int handsItemId() { return hands; }
        public int carriedQuantity(int id) { return id == GLOVES ? oldGloves : 0; }
        public boolean equip(String name) {
            if (confirmEquip) {
                if (hands > 0) oldGloves++;
                hands = -1; bracelets--; equipped = true;
            }
            return true;
        }
        public boolean depositOne(int id) {
            deposits++; depositedId = id;
            if (depositSucceeds) oldGloves--;
            return depositSucceeds;
        }
    }

    private DroSlayerTaskProfileJson profile(boolean expeditious) {
        DroSlayerTaskProfileJson p = new DroSlayerTaskProfileJson();
        if (expeditious) p.setExpeditiousBracelet(true);
        else p.setSlaughterBracelet(true);
        return p;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void banksExactlyTheDisplacedGlovesAfterEitherBraceletIsConfirmed(boolean expeditious) {
        Bank b = new Bank(); b.oldGloves = 1; // An existing carried duplicate is not also banked.
        assertTrue(DroSlayerBracelets.prepareAtBank(profile(expeditious), false, b, false));
        assertEquals(1, b.deposits);
        assertEquals(GLOVES, b.depositedId);
        assertEquals(1, b.oldGloves);
        assertTrue(b.equipped);
        assertEquals(0, b.bracelets);
    }

    @Test void unconfirmedEquipNeverBanksTheOriginalGear() {
        Bank b = new Bank(); b.confirmEquip = false;
        assertFalse(DroSlayerBracelets.prepareAtBank(profile(false), false, b, false));
        assertEquals(0, b.deposits);
        assertEquals(GLOVES, b.hands);
    }

    @Test void fieldReplacementWaitsForANaturalBankVisit() {
        Bank b = new Bank(); b.open = false;
        assertTrue(DroSlayerBracelets.equipCarried(profile(false), false, b));
        assertEquals(0, b.deposits);
        assertEquals(1, b.oldGloves);
        b.open = true;
        assertTrue(DroSlayerBracelets.prepareAtBank(profile(false), false, b, false));
        assertEquals(1, b.deposits);
        assertEquals(0, b.oldGloves);
    }

    @Test void failedDepositIsBoundedAndRetriedOnlyOnAnotherBankOpening() {
        Bank b = new Bank(); b.depositSucceeds = false;
        for (int i = 0; i < 10; i++)
            assertTrue(DroSlayerBracelets.prepareAtBank(profile(false), false, b, false));
        assertEquals(1, b.deposits);
        assertEquals(1, b.oldGloves);
        b.session++; b.depositSucceeds = true;
        assertTrue(DroSlayerBracelets.prepareAtBank(profile(false), false, b, false));
        assertEquals(2, b.deposits);
        assertEquals(0, b.oldGloves);
    }

    @Test void replacingABrokenBraceletWithEmptyHandsDoesNotBankAnything() {
        Bank b = new Bank(); b.hands = -1;
        assertTrue(DroSlayerBracelets.prepareAtBank(profile(false), false, b, false));
        assertEquals(0, b.deposits);
    }
}
