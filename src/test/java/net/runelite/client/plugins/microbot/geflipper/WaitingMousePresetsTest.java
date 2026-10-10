package net.runelite.client.plugins.microbot.geflipper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static net.runelite.client.plugins.microbot.geflipper.FlipperConfig.RandomizationPreset.*;
import static net.runelite.client.plugins.microbot.geflipper.FlipperConfig.TimeOfDaySource.*;
import static org.junit.jupiter.api.Assertions.*;

class WaitingMousePresetsTest {
    private static final Instant START = Instant.parse("2026-10-09T02:00:00Z");

    @Test
    void neutralSessionPresetsUseTheirCentersAndCustomPreservesTheClampedExistingChoice() {
        MutableConfig config = new MutableConfig();
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(Clock.fixed(START, ZoneOffset.UTC), random);
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 30, 99, 100, 101, Integer.MAX_VALUE}) {
            config.base = value;
            assertEquals(Math.max(0, Math.min(100, value)), presets.frequency(config));
            assertEquals(value, config.base, "Reading an effective value cannot rewrite the custom preference");
        }
        config.base = 37;
        config.preset = AFK;
        assertEquals(25, presets.frequency(config));
        config.preset = SEMI_AFK;
        assertEquals(50, presets.frequency(config));
        config.preset = ATTENTIVE_HUMAN;
        assertEquals(75, presets.frequency(config));
        config.preset = CUSTOM;
        assertEquals(37, presets.frequency(config), "Returning to Custom recovers the existing choice");
        assertEquals(1, random.calls, "Static modes share one sampled session offset while speed is off");
    }

    @Test
    void startingPhasesAndCustomTimesAnchorTheVirtualClockRegardlessOfActualTime() {
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = MORNING;
        WaitingMousePresets presets = new WaitingMousePresets(Clock.fixed(START, ZoneOffset.ofHours(10)), new RandomSource());
        assertEquals(74, presets.frequency(config));
        assertTrue(presets.description(config).contains("Morning start 09:00; virtual time 09:00"));
        config.source = MID_DAY;
        assertEquals(68, presets.frequency(config));
        assertTrue(presets.description(config).contains("Mid-day start 13:00; virtual time 13:00"));
        config.source = NIGHT;
        assertEquals(26, presets.frequency(config));
        assertTrue(presets.description(config).contains("Night start 21:00; virtual time 21:00"));
        config.source = CUSTOM_TIME;
        config.time = "10:00";
        assertEquals(75, presets.frequency(config), "Maximum alertness is anchored at 10am");
        assertTrue(presets.description(config).contains("Custom time start 10:00; virtual time 10:00"));
        config.time = "22:00";
        assertEquals(25, presets.frequency(config), "The quietest phase is anchored at 10pm");
    }

    @Test
    void elapsedTimeAdvancesThroughMidnightAndWrapsAfterFullDays() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = CUSTOM_TIME;
        config.time = "23:59";
        WaitingMousePresets presets = new WaitingMousePresets(clock, new RandomSource());
        assertTrue(presets.description(config).contains("virtual time 23:59"));
        clock.set(START.plusSeconds(61));
        assertTrue(presets.description(config).contains("start 23:59; virtual time 00:00"));
        clock.set(START.plusSeconds(24 * 60 * 60));
        assertTrue(presets.description(config).contains("start 23:59; virtual time 23:59"));
        clock.set(START.plusSeconds(48 * 60 * 60 + 120));
        assertTrue(presets.description(config).contains("start 23:59; virtual time 00:01"));
        assertEquals("23:59", config.time, "Advancing virtual time must not save an updated starting time");
    }

    @Test
    void backwardClockClampsElapsedTimeWithoutNegativeVirtualTimeOrExtraDrift() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(74, presets.frequency(config));
        clock.set(START.minusSeconds(3600));
        assertEquals(74, presets.frequency(config));
        assertTrue(presets.description(config).contains("virtual time 09:00"));
        assertEquals(2, random.calls);
        clock.set(START.plusSeconds(3600));
        assertEquals(75, presets.frequency(config));
        assertTrue(presets.description(config).contains("virtual time 10:00"));
        clock.set(START.minusSeconds(30));
        assertEquals(75, presets.frequency(config), "A backward clock must not accelerate integer updates");
        assertTrue(presets.description(config).contains("virtual time 09:00"));
    }

    @Test
    void driftIsStableForItsIntervalAndIntegerChangesAreSeparatedByThirtySeconds() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = CUSTOM_TIME;
        config.time = "10:00";
        RandomSource random = new RandomSource(0, 5, -5);
        random.interval = 120;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(75, presets.frequency(config));
        clock.set(START.plusSeconds(119));
        assertEquals(75, presets.frequency(config));
        assertEquals(2, random.calls, "The initial drift is stable until its interval expires");
        clock.set(START.plusSeconds(120));
        assertEquals(76, presets.frequency(config));
        assertEquals(4, random.calls);
        clock.set(START.plusSeconds(149));
        assertEquals(76, presets.frequency(config));
        clock.set(START.plusSeconds(150));
        assertEquals(77, presets.frequency(config));
        for (int read = 0; read < 100; read++) assertEquals(77, presets.frequency(config));
        clock.set(START.plusSeconds(240));
        assertEquals(76, presets.frequency(config), "Even a missed interval permits only one point on the next read");
        assertEquals(6, random.calls);
        assertEquals(76, presets.frequency(config));
        clock.set(START.plusSeconds(269));
        assertEquals(76, presets.frequency(config));
        clock.set(START.plusSeconds(270));
        assertEquals(75, presets.frequency(config));
    }

    @ParameterizedTest
    @EnumSource(value = FlipperConfig.TimeOfDaySource.class, names = {"CUSTOM_TIME", "LOCAL_TIME"})
    void longestDriftIntervalIsHonoredAndDayFatigueStaysVariableWithinTwentyToEighty(FlipperConfig.TimeOfDaySource choice) {
        MutableClock clock = new MutableClock();
        Instant start = choice == LOCAL_TIME ? START.plusSeconds(8 * 3600) : START;
        clock.set(start);
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = choice;
        config.time = "10:00";
        RandomSource random = new RandomSource(5, -5);
        random.interval = 300;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        int previous = presets.frequency(config);
        assertEquals(80, previous);
        clock.set(start.plusSeconds(299));
        clock.elapse(299);
        presets.frequency(config);
        assertEquals(2, random.calls);
        clock.set(start.plusSeconds(300));
        clock.elapse(300);
        previous = presets.frequency(config);
        assertEquals(4, random.calls);
        int lowest = previous;
        int highest = previous;
        for (int elapsed = 330; elapsed <= 48 * 60 * 60; elapsed += 30) {
            clock.set(start.plusSeconds(elapsed));
            clock.elapse(elapsed);
            int current = presets.frequency(config);
            assertTrue(current >= 20 && current <= 80, "Day Fatigue must stay within its bounded range");
            assertTrue(Math.abs(current - previous) <= 1, "Runtime adjustments move at most one point per 30 seconds");
            lowest = Math.min(lowest, current);
            highest = Math.max(highest, current);
            previous = current;
        }
        assertTrue(lowest <= 30, "The overnight phase should become quieter");
        assertTrue(highest >= 70, "The morning phase should become more attentive");
        assertEquals(30, config.base, "Day Fatigue must not overwrite the custom slider value");
        if (choice == CUSTOM_TIME) assertEquals(0, clock.tickReads, "A virtual clock retains its original elapsed-time source");
    }

    @Test
    void localClockIgnoresSavedCustomTimeAndDoesNotRerollOnHiddenEditsOrRepeatedReads() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        config.time = null;
        config.rejectCustomTime = true;
        RandomSource random = new RandomSource(0, 5);
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        assertEquals(75, presets.frequency(config));
        assertTrue(presets.description(config).contains("computer local time 10:00"));
        assertFalse(presets.description(config).contains("start"));
        for (String hidden : Arrays.asList("24:99", "DO_NOT_ECHO_SYNTHETIC_TEXT", "09:00", null)) {
            config.time = hidden;
            for (int read = 0; read < 25; read++) {
                assertEquals(75, presets.frequency(config));
                assertFalse(presets.description(config).contains("DO_NOT_ECHO_SYNTHETIC_TEXT"));
            }
        }
        assertEquals(0, config.customTimeReads, "LOCAL_TIME must not consult even a malformed saved custom value");
        assertEquals(2, random.calls, "Hidden manual edits and repeated reads cannot restart drift");
        assertNull(config.time);
        assertEquals(LOCAL_TIME, config.source);
        assertEquals(DAY_FATIGUE, config.preset);
        assertEquals(30, config.base);
    }

    @Test
    void localClockFollowsLiveZoneChangesWithoutResettingOrJumpingTheEffectiveSlider() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        assertEquals(75, presets.frequency(config));
        clock.zone = ZoneOffset.UTC;
        assertTrue(presets.description(config).contains("computer local time 02:00"));
        assertEquals(75, presets.frequency(config), "Changing the zone alone cannot bypass the 30-second limit");
        assertEquals(2, random.calls, "A live zone change must not establish another anchor or drift");
        clock.set(START.plusSeconds(29));
        clock.elapse(29);
        assertEquals(75, presets.frequency(config));
        clock.set(START.plusSeconds(30));
        clock.elapse(30);
        assertEquals(74, presets.frequency(config));
        clock.zone = ZoneId.of("Australia/Sydney");
        assertTrue(presets.description(config).contains("computer local time 13:00"));
        assertEquals(74, presets.frequency(config));
        clock.set(START.plusSeconds(60));
        clock.elapse(60);
        assertEquals(73, presets.frequency(config));
        assertEquals(2, random.calls);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-03T15:59:45Z, 01:59, 03:00, 1",
        "2027-04-03T15:59:45Z, 02:59, 02:00, -1"
    })
    void daylightSavingTransitionsFollowTheLocalClockButOnlySlewOnePoint(
        String instant, String before, String after, int direction) {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Australia/Sydney");
        Instant start = Instant.parse(instant);
        clock.set(start);
        MutableConfig config = localConfig();
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        int initial = presets.frequency(config);
        assertTrue(presets.description(config).contains("computer local time " + before));
        clock.set(start.plusSeconds(30));
        clock.elapse(30);
        assertTrue(presets.description(config).contains("computer local time " + after));
        assertEquals(initial + direction, presets.frequency(config));
        for (int read = 0; read < 100; read++) assertEquals(initial + direction, presets.frequency(config));
        assertEquals(2, random.calls, "DST changes local time without adding elapsed drift intervals");
        clock.set(start.plusSeconds(59));
        clock.elapse(59);
        assertEquals(initial + direction, presets.frequency(config));
        clock.set(start.plusSeconds(60));
        clock.elapse(60);
        assertEquals(initial + 2 * direction, presets.frequency(config));
    }

    @Test
    void localClockCrossesMidnightAndKeepsTheSavedStartingTimeUntouched() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Australia/Sydney");
        Instant start = Instant.parse("2026-10-09T12:59:45Z");
        clock.set(start);
        MutableConfig config = localConfig();
        config.time = "17:23";
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        int initial = presets.frequency(config);
        assertTrue(presets.description(config).contains("computer local time 23:59"));
        clock.set(start.plusSeconds(30));
        clock.elapse(30);
        assertTrue(presets.description(config).contains("computer local time 00:00"));
        assertTrue(Math.abs(initial - presets.frequency(config)) <= 1);
        assertEquals(2, random.calls);
        assertEquals("17:23", config.time);
        assertEquals(0, config.customTimeReads);
    }

    @Test
    void localClockRollbackChangesTheDisplayedTimeWithoutReplayingDriftOrSliderSteps() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        RandomSource random = new RandomSource(0, -5, 5);
        random.interval = 120;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        assertEquals(75, presets.frequency(config));
        clock.set(START.plusSeconds(120));
        clock.elapse(120);
        assertEquals(74, presets.frequency(config));
        assertEquals(4, random.calls);
        clock.set(START.minusSeconds(3600));
        clock.elapse(90);
        assertEquals(74, presets.frequency(config), "An injected monotonic rollback also cannot replay an elapsed interval");
        assertEquals(4, random.calls);
        clock.elapse(149);
        for (int read = 0; read < 100; read++) assertEquals(74, presets.frequency(config));
        assertTrue(presets.description(config).contains("computer local time 09:00"));
        assertEquals(4, random.calls);
        clock.set(START.minusSeconds(3570));
        clock.elapse(150);
        assertEquals(73, presets.frequency(config));
        clock.set(START.minusSeconds(7200));
        clock.elapse(180);
        assertEquals(72, presets.frequency(config), "A backward wall edit cannot freeze further monotonic slider updates");
        assertEquals(4, random.calls);
        clock.set(START.minusSeconds(7140));
        clock.elapse(240);
        assertEquals(73, presets.frequency(config), "The next drift uses elapsed time despite the wall clock still being behind");
        assertEquals(6, random.calls);
        assertEquals(73, presets.frequency(config));
        clock.elapse(269);
        assertEquals(73, presets.frequency(config));
        clock.elapse(270);
        assertEquals(74, presets.frequency(config));
    }

    @Test
    void hugeClockJumpsSampleAtMostOneDriftAndCannotCatchUpTheSliderInRepeatedReads() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        RandomSource random = new RandomSource(0, -5);
        random.interval = 120;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        assertEquals(75, presets.frequency(config));
        Instant farFuture = START.plusSeconds(100L * 366 * 24 * 60 * 60 + 12 * 3600);
        clock.set(farFuture);
        for (int read = 0; read < 100; read++) assertEquals(75, presets.frequency(config));
        assertEquals(2, random.calls, "A hundred-year wall edit alone cannot advance drift or slew");
        clock.elapse(29);
        assertEquals(75, presets.frequency(config));
        clock.elapse(30);
        assertEquals(74, presets.frequency(config));
        clock.set(START);
        assertTrue(presets.description(config).contains("computer local time 10:00"));
        clock.elapse(59);
        assertEquals(74, presets.frequency(config));
        clock.elapse(60);
        assertEquals(75, presets.frequency(config), "Correcting the wall clock back cannot freeze a monotonic update");
        clock.set(farFuture);
        clock.elapse(3600);
        assertEquals(74, presets.frequency(config), "A missed monotonic hour still changes only one point");
        for (int read = 0; read < 100; read++) assertEquals(74, presets.frequency(config));
        assertEquals(4, random.calls, "Missed intervals do not replay a backlog of random draws");
        clock.elapse(3629);
        assertEquals(74, presets.frequency(config));
        clock.elapse(3630);
        assertEquals(73, presets.frequency(config));
        assertEquals(4, random.calls);
    }

    @Test
    void disabledLocalClockNeverReadsTimeZoneCustomTimeOrRandomnessAndReenableUsesCurrentTime() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        config.speed = false;
        config.rejectTimeControls = true;
        config.time = "invalid hidden time";
        config.base = 47;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        for (int read = 0; read < 100; read++) {
            assertEquals(47, presets.frequency(config));
            assertTrue(presets.description(config).contains("clock disabled"));
        }
        assertEquals(0, clock.reads);
        assertEquals(0, clock.zoneReads);
        assertEquals(0, clock.tickReads);
        assertEquals(0, random.calls);
        assertEquals(0, config.timeReads);
        config.rejectTimeControls = false;
        config.rejectCustomTime = true;
        config.speed = true;
        assertEquals(75, presets.frequency(config));
        config.speed = false;
        assertEquals(47, presets.frequency(config));
        int reads = clock.reads;
        int zones = clock.zoneReads;
        int ticks = clock.tickReads;
        int calls = random.calls;
        clock.set(START.plusSeconds(12 * 3600));
        for (int read = 0; read < 100; read++) assertEquals(47, presets.frequency(config));
        assertEquals(reads, clock.reads);
        assertEquals(zones, clock.zoneReads);
        assertEquals(ticks, clock.tickReads);
        assertEquals(calls, random.calls);
        config.speed = true;
        assertEquals(25, presets.frequency(config), "Re-enabling LOCAL_TIME must use 22:00 now, not its old 10:00 anchor");
        assertTrue(presets.description(config).contains("computer local time 22:00"));
        assertEquals(calls + 2, random.calls);
        assertEquals(47, config.base);
        assertEquals("invalid hidden time", config.time);
        assertEquals(0, config.customTimeReads);
    }

    @Test
    void switchingLocalAndVirtualSourcesInitializesTheChosenClockAndPreservesCustomTimeAcrossReset() {
        MutableClock clock = new MutableClock();
        clock.zone = ZoneId.of("Asia/Singapore");
        MutableConfig config = localConfig();
        config.time = "14:30";
        config.base = 43;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random, clock::nanoTime);
        assertEquals(75, presets.frequency(config));
        clock.set(START.plusSeconds(3600));
        assertTrue(presets.description(config).contains("computer local time 11:00"));
        config.source = CUSTOM_TIME;
        assertTrue(presets.description(config).contains("Custom time start 14:30; virtual time 14:30"));
        clock.set(START.plusSeconds(3660));
        assertTrue(presets.description(config).contains("virtual time 14:31"));
        config.source = LOCAL_TIME;
        assertTrue(presets.description(config).contains("computer local time 11:01"));
        config.source = MORNING;
        assertTrue(presets.description(config).contains("Morning start 09:00; virtual time 09:00"));
        config.source = MID_DAY;
        assertTrue(presets.description(config).contains("Mid-day start 13:00; virtual time 13:00"));
        config.source = NIGHT;
        assertTrue(presets.description(config).contains("Night start 21:00; virtual time 21:00"));
        config.source = LOCAL_TIME;
        presets.frequency(config);
        int calls = random.calls;
        presets.reset();
        assertTrue(presets.description(config).contains("computer local time 11:01"));
        assertEquals(calls + 2, random.calls);
        assertEquals("14:30", config.time);
        assertEquals(43, config.base);
        assertEquals(LOCAL_TIME, config.source);
        assertEquals(DAY_FATIGUE, config.preset);
        config.preset = CUSTOM;
        calls = random.calls;
        int reads = clock.reads;
        int zones = clock.zoneReads;
        int ticks = clock.tickReads;
        assertEquals(43, presets.frequency(config));
        assertEquals("Custom randomization: 43.", presets.description(config));
        assertEquals(calls, random.calls);
        assertEquals(reads, clock.reads);
        assertEquals(zones, clock.zoneReads);
        assertEquals(ticks, clock.tickReads);
    }

    @Test
    void invalidCustomTimeDisablesOnlyThisFrequencyAndNeverEchoesTheInput() {
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = CUSTOM_TIME;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(Clock.fixed(START, ZoneOffset.UTC), random);
        for (String invalid : Arrays.asList(null, "", "9:00", "24:00", "29:00", "23:60", "00:0", "09:00:00",
            " 09:00", "09:00 ", "DO_NOT_ECHO_SYNTHETIC_TEXT")) {
            config.time = invalid;
            assertEquals(0, presets.frequency(config));
            assertEquals("Enter time as HH:mm", presets.description(config));
        }
        assertEquals(0, random.calls, "Invalid custom text must not start a drift timer");
        config.time = "09:00";
        assertEquals(74, presets.frequency(config));
        assertEquals(2, random.calls);
        config.source = null;
        assertEquals(0, presets.frequency(config));
        assertEquals("Choose a starting phase.", presets.description(config));
        config.preset = null;
        assertEquals(0, presets.frequency(config));
        assertEquals("Choose a randomization preset.", presets.description(config));
        config.preset = CUSTOM;
        config.base = 63;
        assertEquals(63, presets.frequency(config), "Invalid time cannot affect ordinary Custom randomization");
    }

    @Test
    void sourceTimeEditsAndLeavingDayFatigueResetThePrivateAnchorOnTheNextEntry() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        WaitingMousePresets presets = new WaitingMousePresets(clock, new RandomSource());
        presets.frequency(config);
        clock.set(START.plusSeconds(3600));
        assertTrue(presets.description(config).contains("virtual time 10:00"));
        config.source = NIGHT;
        assertEquals(26, presets.frequency(config));
        assertTrue(presets.description(config).contains("virtual time 21:00"));
        clock.set(START.plusSeconds(3660));
        assertTrue(presets.description(config).contains("virtual time 21:01"));
        config.source = CUSTOM_TIME;
        config.time = "12:30";
        assertTrue(presets.description(config).contains("virtual time 12:30"));
        config.time = "14:00";
        assertEquals(63, presets.frequency(config));
        assertTrue(presets.description(config).contains("virtual time 14:00"));
        config.preset = AFK;
        assertEquals(25, presets.frequency(config));
        clock.set(START.plusSeconds(10860));
        config.preset = DAY_FATIGUE;
        config.speed = true;
        assertEquals(63, presets.frequency(config));
        assertTrue(presets.description(config).contains("start 14:00; virtual time 14:00"));
    }

    @Test
    void unrelatedSliderChangesDoNotResetTheDayAnchorAndNoMasterOrTradingSettingsAreRead() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        WaitingMousePresets presets = new WaitingMousePresets(clock, new RandomSource());
        presets.frequency(config);
        clock.set(START.plusSeconds(3600));
        config.base = 100;
        assertTrue(presets.description(config).contains("start 09:00; virtual time 10:00"));
        assertEquals(75, presets.frequency(config));
        assertEquals(100, config.base);
        // MutableConfig's unrelated getters throw; all previous calls prove the policy never consults them.
    }

    @Test
    void explicitProfileResetReinitializesOnlyThePrivateDayState() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.base = 41;
        RandomSource random = new RandomSource(0, 0, -5);
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(74, presets.frequency(config));
        clock.set(START.plusSeconds(3600));
        assertTrue(presets.description(config).contains("virtual time 10:00"));
        presets.reset();
        assertEquals(69, presets.frequency(config), "Reset chooses fresh drift at the configured starting phase");
        assertTrue(presets.description(config).contains("Morning start 09:00; virtual time 09:00"));
        assertEquals(DAY_FATIGUE, config.preset);
        assertEquals(MORNING, config.source);
        assertEquals("09:00", config.time);
        assertEquals(41, config.base, "A profile reset cannot save or replace the custom slider preference");
    }

    @Test
    void disabledClockUsesClampedManualValueWithoutReadingTimeClockOrRandomness() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.source = null;
        config.time = "DO_NOT_ECHO_SYNTHETIC_TEXT";
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 37, 99, 100, 101, Integer.MAX_VALUE}) {
            config.base = value;
            int clamped = Math.max(0, Math.min(100, value));
            assertEquals(clamped, presets.frequency(config));
            assertEquals("Time-of-day clock disabled; using saved manual randomization: " + clamped + ".",
                presets.description(config));
            assertEquals(value, config.base, "Disabling the clock must preserve the saved manual choice");
        }
        assertEquals(0, random.calls);
        assertEquals(0, clock.reads, "A disabled clock cannot advance or establish an anchor");
        assertEquals(0, config.timeReads, "Disabled time settings must not be read or validated");
        assertEquals(DAY_FATIGUE, config.preset);
        assertNull(config.source);
        assertEquals("DO_NOT_ECHO_SYNTHETIC_TEXT", config.time);
    }

    @Test
    void speedToggleStopsFatigueAndReenableStartsAgainFromRememberedTime() {
        MutableClock clock = new MutableClock();
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.speed = true;
        config.source = CUSTOM_TIME;
        config.time = "14:30";
        config.base = 43;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertTrue(presets.description(config).contains("start 14:30; virtual time 14:30"));
        clock.set(START.plusSeconds(3600));
        assertTrue(presets.description(config).contains("virtual time 15:30"));

        config.speed = false;
        int calls = random.calls;
        int timeReads = config.timeReads;
        int clockReads = clock.reads;
        assertEquals(43, presets.frequency(config));
        clock.set(START.plusSeconds(20 * 60 * 60));
        assertEquals("Time-of-day clock disabled; using saved manual randomization: 43.",
            presets.description(config));
        assertEquals(calls, random.calls);
        assertEquals(timeReads, config.timeReads);
        assertEquals(clockReads, clock.reads);

        config.speed = true;
        assertTrue(presets.description(config).contains("start 14:30; virtual time 14:30"),
            "Re-enabling starts a fresh clock rather than continuing the old anchor");
        assertEquals(calls + 2, random.calls, "A new clock chooses a new bounded drift and interval");
        clock.set(START.plusSeconds(20 * 60 * 60 + 60));
        assertTrue(presets.description(config).contains("virtual time 14:31"));
        assertEquals(DAY_FATIGUE, config.preset);
        assertEquals(CUSTOM_TIME, config.source);
        assertEquals("14:30", config.time);
        assertEquals(43, config.base);
    }

    @Test
    void invalidCustomTimeIsIgnoredUntilSpeedIsEnabledAndDisablingClearsItsError() {
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.source = CUSTOM_TIME;
        config.time = "24:30";
        config.base = 61;
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(Clock.fixed(START, ZoneOffset.UTC), random);
        assertEquals(61, presets.frequency(config));
        assertEquals(0, config.timeReads);
        config.speed = true;
        assertEquals(0, presets.frequency(config));
        assertEquals("Enter time as HH:mm", presets.description(config));
        config.speed = false;
        assertEquals(61, presets.frequency(config));
        assertEquals("Time-of-day clock disabled; using saved manual randomization: 61.",
            presets.description(config));
        assertEquals("24:30", config.time, "Invalid saved input must be retained for explicit correction");
        assertEquals(0, random.calls);
    }

    @Test
    void sessionAndCustomPresetsNeverReadTheTimeOfDayControls() {
        MutableConfig config = new MutableConfig();
        config.base = 42;
        config.source = null;
        config.time = "24:30";
        RandomSource random = new RandomSource();
        WaitingMousePresets presets = new WaitingMousePresets(Clock.fixed(START, ZoneOffset.UTC), random);
        FlipperConfig.RandomizationPreset[] choices = {CUSTOM, AFK, SEMI_AFK, ATTENTIVE_HUMAN};
        int[] frequencies = {42, 25, 50, 75};
        for (boolean speed : new boolean[]{false, true}) {
            config.speed = speed;
            for (int index = 0; index < choices.length; index++) {
                config.preset = choices[index];
                assertEquals(frequencies[index], presets.frequency(config));
                assertFalse(presets.description(config).contains("clock disabled"));
            }
        }
        assertTrue(config.speedReads > 0, "Session presets use the speed switch to gate gradual fatigue");
        assertEquals(0, config.timeReads);
        assertTrue(random.calls > 0);
        assertEquals(42, config.base);
    }

    @ParameterizedTest
    @EnumSource(value = FlipperConfig.RandomizationPreset.class, names = {"AFK", "SEMI_AFK", "ATTENTIVE_HUMAN"})
    void independentSessionsHaveStableBoundedOffsetsWithoutReadingAClockWhenSpeedIsOff(FlipperConfig.RandomizationPreset choice) {
        MutableConfig config = new MutableConfig();
        config.preset = choice;
        int baseline = center(choice);
        MutableClock clock = new MutableClock();
        RandomSource low = new RandomSource();
        low.offset = -3;
        RandomSource high = new RandomSource();
        high.offset = 3;
        WaitingMousePresets first = new WaitingMousePresets(clock, low);
        WaitingMousePresets second = new WaitingMousePresets(clock, high);
        for (int read = 0; read < 100; read++) {
            assertEquals(baseline - 3, first.frequency(config));
            assertEquals(baseline + 3, second.frequency(config));
            assertTrue(first.description(config).contains("fatigue disabled"));
        }
        assertEquals(1, low.calls);
        assertEquals(1, high.calls);
        assertEquals(0, clock.reads);
        assertEquals(0, config.timeReads);
        assertEquals(30, config.base);
        assertEquals(choice, config.preset);
    }

    @ParameterizedTest
    @EnumSource(value = FlipperConfig.RandomizationPreset.class, names = {"AFK", "SEMI_AFK", "ATTENTIVE_HUMAN"})
    void everySessionPresetGraduallyFatiguesWithinItsOwnBand(FlipperConfig.RandomizationPreset choice) {
        MutableConfig config = new MutableConfig();
        config.preset = choice;
        config.speed = true;
        MutableClock clock = new MutableClock();
        RandomSource random = new RandomSource();
        random.fatigueInterval = 900;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        int baseline = center(choice);
        int previous = presets.frequency(config);
        assertEquals(baseline, previous);
        for (int elapsed = 30; elapsed <= 6 * 60 * 60; elapsed += 30) {
            clock.set(START.plusSeconds(elapsed));
            int value = presets.frequency(config);
            assertTrue(value >= baseline - 10 && value <= baseline + 10);
            assertTrue(Math.abs(value - previous) <= 1);
            for (int read = 0; read < 3; read++) assertEquals(value, presets.frequency(config));
            previous = value;
        }
        assertEquals(baseline - 6, previous, "Fatigue is gradual and capped, rather than an unbounded decline");
        assertTrue(presets.description(config).contains("gradual fatigue active"));
        assertEquals(0, config.timeReads, "Session fatigue must not use or validate the hidden custom time");
        assertEquals(30, config.base);
    }

    @Test
    void sessionDriftIsHeldAndChangesTheSliderAtMostOncePerThirtySeconds() {
        MutableConfig config = new MutableConfig();
        config.preset = SEMI_AFK;
        config.speed = true;
        MutableClock clock = new MutableClock();
        RandomSource random = new RandomSource();
        random.interval = 120;
        random.sessionDrifts.addAll(Arrays.asList(0, 2, -2));
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(50, presets.frequency(config));
        int sampled = random.calls;
        clock.set(START.plusSeconds(119));
        for (int read = 0; read < 100; read++) assertEquals(50, presets.frequency(config));
        assertEquals(sampled, random.calls);
        clock.set(START.plusSeconds(120));
        assertEquals(51, presets.frequency(config));
        clock.set(START.plusSeconds(149));
        assertEquals(51, presets.frequency(config));
        clock.set(START.plusSeconds(150));
        assertEquals(52, presets.frequency(config));
        clock.set(START.plusSeconds(240));
        assertEquals(51, presets.frequency(config));
        clock.set(START.minusSeconds(1));
        sampled = random.calls;
        assertEquals(51, presets.frequency(config), "Clock rollback cannot undo fatigue or accelerate the display");
        assertEquals(sampled, random.calls);
        clock.set(START.plusSeconds(270));
        assertEquals(50, presets.frequency(config));
    }

    @Test
    void disablingSessionFatigueDoesNotCountTheOffIntervalOrResampleItsSessionOffset() {
        MutableConfig config = new MutableConfig();
        config.preset = AFK;
        config.speed = true;
        MutableClock clock = new MutableClock();
        RandomSource random = new RandomSource();
        random.fatigueInterval = 900;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(25, presets.frequency(config));
        clock.set(START.plusSeconds(1800));
        assertEquals(24, presets.frequency(config), "A missed interval still adjusts by only one point");
        clock.set(START.plusSeconds(1830));
        assertEquals(23, presets.frequency(config));
        config.speed = false;
        assertEquals(25, presets.frequency(config));
        int samples = random.calls;
        int reads = clock.reads;
        clock.set(START.plusSeconds(20 * 60 * 60));
        for (int read = 0; read < 100; read++) assertEquals(25, presets.frequency(config));
        assertEquals(samples, random.calls);
        assertEquals(reads, clock.reads);
        config.speed = true;
        assertEquals(25, presets.frequency(config));
        assertEquals(samples + 3, random.calls, "Restart only the drift and timing; retain the session offset");
        clock.set(START.plusSeconds(20 * 60 * 60 + 899));
        assertEquals(25, presets.frequency(config));
        clock.set(START.plusSeconds(20 * 60 * 60 + 900));
        assertEquals(24, presets.frequency(config));
        assertEquals(AFK, config.preset);
        assertEquals(30, config.base);
        random.offset = 3;
        config.speed = false;
        presets.reset();
        assertEquals(28, presets.frequency(config), "An explicit profile/model reset samples a new session variation");
    }

    @Test
    void selectingAnotherSessionPresetKeepsElapsedFatigueAndCustomStaysExact() {
        MutableConfig config = new MutableConfig();
        config.preset = AFK;
        config.speed = true;
        MutableClock clock = new MutableClock();
        RandomSource random = new RandomSource();
        random.offset = 2;
        random.fatigueInterval = 900;
        WaitingMousePresets presets = new WaitingMousePresets(clock, random);
        assertEquals(27, presets.frequency(config));
        clock.set(START.plusSeconds(1800));
        assertEquals(26, presets.frequency(config));
        int samples = random.calls;
        config.preset = SEMI_AFK;
        assertEquals(50, presets.frequency(config));
        config.preset = ATTENTIVE_HUMAN;
        assertEquals(75, presets.frequency(config));
        assertEquals(samples, random.calls, "Changing only preset identity must not reset or reroll its session");
        config.preset = CUSTOM;
        config.base = 37;
        int reads = clock.reads;
        assertEquals(37, presets.frequency(config));
        assertEquals("Custom randomization: 37.", presets.description(config));
        assertEquals(samples, random.calls);
        assertEquals(reads, clock.reads);
    }

    private static int center(FlipperConfig.RandomizationPreset choice) {
        return choice == AFK ? 25 : choice == SEMI_AFK ? 50 : 75;
    }

    private static MutableConfig localConfig() {
        MutableConfig config = new MutableConfig();
        config.preset = DAY_FATIGUE;
        config.source = LOCAL_TIME;
        config.speed = true;
        return config;
    }

    private static final class MutableConfig implements FlipperConfig {
        private RandomizationPreset preset = CUSTOM;
        private TimeOfDaySource source = MORNING;
        private String time = "09:00";
        private int base = 30;
        private boolean speed;
        private int speedReads;
        private int timeReads;
        private int customTimeReads;
        private boolean rejectCustomTime;
        private boolean rejectTimeControls;

        @Override public RandomizationPreset waitingMousePreset() { return preset; }
        @Override public TimeOfDaySource waitingMouseTimeSource() {
            if (rejectTimeControls) throw new AssertionError("Inactive time controls must not be read");
            timeReads++;
            return source;
        }
        @Override public String waitingMouseTime() {
            customTimeReads++;
            if (rejectCustomTime || rejectTimeControls) throw new AssertionError("Hidden custom time must not be read");
            timeReads++;
            return time;
        }
        @Override public int waitingMouseChance() { return base; }
        @Override public boolean randomizeMouseSpeed() { speedReads++; return speed; }
        @Override public boolean waitingMouseOffScreen() { throw new AssertionError("The master switch belongs to the caller"); }
        @Override public SelectionMethod selectionMethod() { throw new AssertionError("Randomization cannot inspect trading settings"); }
        @Override public SlotAction slotAction() { throw new AssertionError("Randomization cannot inspect trading settings"); }
    }

    private static final class MutableClock extends Clock {
        private Instant now = START;
        private ZoneId zone = ZoneOffset.UTC;
        private int reads;
        private int zoneReads;
        private long nanos;
        private int tickReads;
        private void set(Instant time) { now = time; }
        private void elapse(long seconds) { nanos = TimeUnit.SECONDS.toNanos(seconds); }
        private long nanoTime() { tickReads++; return nanos; }
        @Override public ZoneId getZone() { zoneReads++; return zone; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { reads++; return now; }
    }

    private static final class RandomSource implements WaitingMouse.RandomInt {
        private final Deque<Integer> drifts = new ArrayDeque<>();
        private final Deque<Integer> sessionDrifts = new ArrayDeque<>();
        private int calls;
        private int interval = 300;
        private int offset;
        private int fatigueInterval = 1800;

        private RandomSource(Integer... drift) { drifts.addAll(Arrays.asList(drift)); }

        @Override public int inclusive(int minimum, int maximum) {
            calls++;
            if (minimum == -3) { assertEquals(3, maximum); return offset; }
            if (minimum == -2) { assertEquals(2, maximum); return sessionDrifts.isEmpty() ? 0 : sessionDrifts.removeFirst(); }
            if (minimum == 900) { assertEquals(1800, maximum); return fatigueInterval; }
            if (minimum == -5) {
                assertEquals(5, maximum);
                return drifts.isEmpty() ? 0 : drifts.removeFirst();
            }
            assertEquals(120, minimum);
            assertEquals(300, maximum);
            return interval;
        }
    }
}
