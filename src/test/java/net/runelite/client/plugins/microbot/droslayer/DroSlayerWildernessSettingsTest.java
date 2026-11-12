package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerWildernessSettingsTest {
    private static final class Fake implements DroSlayerWildernessSettings.Environment {
        boolean loggedIn = true, paused, skull = true, aid, controls = true;
        int navigationClicks, skullClicks, aidClicks, restoreCalls;
        public boolean loggedIn() { return loggedIn; }
        public boolean paused() { return paused; }
        public boolean skullPreventionEnabled() { return skull; }
        public boolean acceptAidEnabled() { return aid; }
        public boolean controlsVisible() { return controls; }
        public void revealControls() { navigationClicks++; }
        public boolean enableSkullPrevention() { skullClicks++; return true; }
        public boolean disableAcceptAid() { aidClicks++; return true; }
        public void restoreTab() { restoreCalls++; }
    }

    @Test public void alreadySafeFlagsRequireNoSettingsClicks() {
        Fake environment = new Fake();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, () -> 0L);
        for (int i = 0; i < 20; i++) assertTrue(guard.ensureSafeSettings());
        assertEquals(0, environment.navigationClicks + environment.skullClicks + environment.aidClicks + environment.restoreCalls);
    }

    @Test public void successfulClickDoesNotPretendFlagChangedAndRetriesHaveSpacing() {
        Fake environment = new Fake(); environment.skull = false;
        AtomicLong now = new AtomicLong();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, now::get);
        assertFalse(guard.ensureSafeSettings());
        assertEquals(1, environment.skullClicks);
        now.set(1799); assertFalse(guard.ensureSafeSettings());
        assertEquals(1, environment.skullClicks);
        now.set(1800); assertFalse(guard.ensureSafeSettings());
        assertEquals(2, environment.skullClicks);
        environment.skull = true;
        assertTrue(guard.ensureSafeSettings());
        assertEquals(1, environment.restoreCalls);
        assertFalse(guard.isBlocked());
    }

    @Test public void skullPreventionNeverTogglesOffWhileAcceptAidIsDisabledSeparately() {
        Fake environment = new Fake(); environment.aid = true;
        AtomicLong now = new AtomicLong();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, now::get);
        assertFalse(guard.ensureSafeSettings());
        assertEquals(0, environment.skullClicks);
        assertEquals(1, environment.aidClicks);
        environment.aid = false;
        assertTrue(guard.ensureSafeSettings());
        assertEquals(0, environment.skullClicks);
    }

    @Test public void threeFailedToggleAttemptsHoldTaskUntilObservedManualCorrection() {
        Fake environment = new Fake(); environment.skull = false;
        AtomicLong now = new AtomicLong();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, now::get);
        for (int i = 0; i < 4; i++) { now.set(i * 1800L); assertFalse(guard.ensureSafeSettings()); }
        assertEquals(3, environment.skullClicks);
        assertTrue(guard.isBlocked());
        now.set(100000); assertFalse(guard.ensureSafeSettings());
        assertEquals(3, environment.skullClicks);
        environment.skull = true;
        assertTrue(guard.ensureSafeSettings());
        assertFalse(guard.isBlocked());
    }

    @Test public void safeFlagIsRecheckedAfterWorldHopOrExternalSettingChange() {
        Fake environment = new Fake();
        AtomicLong now = new AtomicLong();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, now::get);
        assertTrue(guard.ensureSafeSettings());
        environment.skull = false;
        assertFalse(guard.ensureSafeSettings());
        assertEquals(1, environment.skullClicks);
        environment.skull = true;
        assertTrue(guard.ensureSafeSettings());
        now.set(1800); environment.aid = true;
        assertFalse(guard.ensureSafeSettings());
        assertEquals(1, environment.aidClicks);
    }

    @Test public void noClicksWhileLoggedOutPausedOrMissingControls() {
        Fake environment = new Fake(); environment.skull = false; environment.loggedIn = false;
        AtomicLong now = new AtomicLong();
        DroSlayerWildernessSettings guard = new DroSlayerWildernessSettings(environment, now::get);
        assertFalse(guard.ensureSafeSettings());
        environment.loggedIn = true; environment.paused = true;
        assertFalse(guard.ensureSafeSettings());
        assertEquals(0, environment.skullClicks);
        environment.paused = false; environment.controls = false;
        for (int i = 0; i < 7; i++) { now.set(i * 1800L); assertFalse(guard.ensureSafeSettings()); }
        assertEquals(6, environment.navigationClicks);
        assertEquals(0, environment.skullClicks);
        assertTrue(guard.isBlocked());
    }

    @Test public void nativeSkullCheckboxChildWinsOverLabelContainer() {
        Widget container = mock(Widget.class), checkbox = mock(Widget.class);
        when(container.getChild(1)).thenReturn(checkbox);
        when(checkbox.getBounds()).thenReturn(new Rectangle(100, 100, 20, 20));
        assertSame(checkbox, DroSlayerWildernessSettings.skullCheckbox(container));
        when(checkbox.isHidden()).thenReturn(true);
        assertNull(DroSlayerWildernessSettings.skullCheckbox(container));
    }
}
