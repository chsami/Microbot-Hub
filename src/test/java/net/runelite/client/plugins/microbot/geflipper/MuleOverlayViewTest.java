package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MuleOverlayViewTest {
    @Test
    void receiverRowsListLinkHeldSessionRateAndAllTimeInThatOrder() {
        MuleLedger.Snapshot ledger = new MuleLedger.Snapshot(3_000_000L, 12_500_000_000L, 2_147_000_000L, 3_600_000L);
        List<MuleOverlayView.Row> rows = MuleOverlayView.receiverRows(
            new MuleConnectionMonitor.Snapshot(MuleConnectionMonitor.State.SENDER_SEEN, true, false, 1L),
            ledger, true, "");
        assertEquals(List.of("Flipper link:", "Total held:", "Muled (session):", "Muled GP/hr:", "Muled (all time):"),
            rows.stream().map(row -> row.label).collect(Collectors.toList()));
        assertEquals("Sender connected", rows.get(0).value);
        assertEquals("2.15B gp", rows.get(1).value);
        assertEquals("+3.00M gp", rows.get(2).value);
        assertEquals("+3.00M gp/hr", rows.get(3).value);
        assertEquals("+12.50B gp", rows.get(4).value);
        for (MuleOverlayView.Row row : rows) assertEquals(MuleOverlayView.Tone.POSITIVE, row.tone, row.label);
    }

    @Test
    void aCoinCountSeenEarlierIsLabelledAndAnUnseenOneIsUnknown() {
        MuleLedger.Snapshot seen = new MuleLedger.Snapshot(0L, 0L, 5_000_000L, 0L);
        assertEquals("5.00M gp", MuleOverlayView.receiverRows(null, seen, true, "").get(1).value);
        assertEquals("5.00M gp (last seen)", MuleOverlayView.receiverRows(null, seen, false, "").get(1).value);
        MuleOverlayView.Row unknown = MuleOverlayView.receiverRows(null, MuleLedger.Snapshot.EMPTY, false, "").get(1);
        assertEquals("Unknown", unknown.value);
        assertEquals(MuleOverlayView.Tone.MUTED, unknown.tone);
        MuleOverlayView.Row empty = MuleOverlayView.receiverRows(null,
            new MuleLedger.Snapshot(0L, 0L, 0L, 0L), true, "").get(1);
        assertEquals("0 gp", empty.value);
        assertEquals(MuleOverlayView.Tone.MUTED, empty.tone);
    }

    @Test
    void noTradesYetShowsZerosInTheMutedColour() {
        List<MuleOverlayView.Row> rows = MuleOverlayView.receiverRows(null, null, false, null);
        assertEquals(5, rows.size(), "A missing ledger or status adds nothing");
        assertEquals("Checking", rows.get(0).value);
        assertEquals(MuleOverlayView.Tone.WARNING, rows.get(0).tone);
        assertEquals("0 gp", rows.get(2).value);
        assertEquals("0 gp/hr", rows.get(3).value);
        assertEquals(MuleOverlayView.Tone.MUTED, rows.get(2).tone);
        assertEquals(MuleOverlayView.Tone.MUTED, rows.get(3).tone);
        assertEquals(MuleOverlayView.Tone.MUTED, rows.get(4).tone);
    }

    @Test
    void aStatusMessageIsAddedLastAsAWarningRowWithoutAValue() {
        List<MuleOverlayView.Row> rows = MuleOverlayView.receiverRows(null, null, false,
            "Mule: standby (logged out, W301, port 43599)");
        MuleOverlayView.Row last = rows.get(rows.size() - 1);
        assertEquals("Mule: standby (logged out, W301, port 43599)", last.label);
        assertEquals("", last.value);
        assertEquals(MuleOverlayView.Tone.WARNING, last.tone);
        assertThrows(UnsupportedOperationException.class, () -> rows.add(last));
    }

    @ParameterizedTest
    @EnumSource(MuleConnectionMonitor.State.class)
    void linkColoursKeepTheirMeaning(MuleConnectionMonitor.State state) {
        MuleConnectionMonitor.Snapshot link = new MuleConnectionMonitor.Snapshot(state, false, false, 1L);
        MuleOverlayView.Tone expected;
        switch (state) {
            case READY: case STANDBY: case SENDER_SEEN: expected = MuleOverlayView.Tone.POSITIVE; break;
            case OFFLINE: case WRONG_MULE: case STALE: expected = MuleOverlayView.Tone.NEGATIVE; break;
            case OFF: expected = MuleOverlayView.Tone.MUTED; break;
            default: expected = MuleOverlayView.Tone.WARNING;
        }
        assertEquals(expected, MuleOverlayView.linkTone(link));
    }

    @Test
    void colorsMatchTheExistingOverlayPalette() {
        assertEquals(new Color(0x52D273), MuleOverlayView.color(MuleOverlayView.Tone.POSITIVE));
        assertEquals(new Color(0xFF6666), MuleOverlayView.color(MuleOverlayView.Tone.NEGATIVE));
        assertEquals(Color.ORANGE, MuleOverlayView.color(MuleOverlayView.Tone.WARNING));
        assertEquals(Color.LIGHT_GRAY, MuleOverlayView.color(MuleOverlayView.Tone.MUTED));
        assertEquals(Color.ORANGE, MuleOverlayView.color(MuleOverlayView.linkTone(null)));
    }

    @Test
    void amountsHaveNoSignAndNeverGoNegative() {
        assertEquals("0 gp", MuleOverlayView.amount(0));
        assertEquals("999 gp", MuleOverlayView.amount(999));
        assertEquals("12.3K gp", MuleOverlayView.amount(12_345));
        assertEquals("2.00M gp", MuleOverlayView.amount(2_000_000));
        assertEquals("2.15B gp", MuleOverlayView.amount(2_147_483_647L));
        assertEquals("0 gp", MuleOverlayView.amount(-50));
    }

    @Test
    void theTitleCarriesThePluginVersion() {
        assertEquals("Microbot Flipper v" + FlipperPlugin.version, MuleOverlayView.title());
    }
}
