package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import java.util.function.BooleanSupplier;
import java.util.Set;
import java.util.EnumSet;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PrayerSettingsActionsTest {
    @Test public void noneTurnsOffOffensiveBoostsWithoutTouchingProtectionOrDetectingWeapon() throws Exception {
        assertEquals(DroSlayerOffensivePrayer.NONE, DroSlayerOffensivePrayer.fromString("NONE"));
        assertEquals(DroSlayerOffensivePrayer.AUTOMATIC, DroSlayerOffensivePrayer.fromString(null));
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.useOffensivePrayers()).thenReturn(true);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
        Field configField = DroSlayerScript.class.getDeclaredField("config");
        configField.setAccessible(true); configField.set(script, config);
        var profile = new DroSlayerTaskProfileJson();
        profile.setOffensivePrayer("NONE");
        Field profileField = DroSlayerScript.class.getDeclaredField("activeJsonProfile");
        profileField.setAccessible(true); profileField.set(script, profile);
        Set<Rs2PrayerEnum> active = EnumSet.of(
            Rs2PrayerEnum.PIETY,
            Rs2PrayerEnum.MYSTIC_VIGOUR,
            Rs2PrayerEnum.PROTECT_MAGIC);
        try (var prayers = mockStatic(Rs2Prayer.class);
             var slayer = mockStatic(Rs2Slayer.class);
             var runtime = mockStatic(DroSlayerRuntime.class);
             var magic = mockStatic(Rs2Magic.class)) {
            slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Lava dragons");
            prayers.when(() -> Rs2Prayer.isPrayerActive(any()))
                .thenAnswer(invocation -> active.contains(invocation.getArgument(0)));
            prayers.when(() -> Rs2Prayer.toggle(any(), anyBoolean(), eq(true)))
                .thenAnswer(invocation -> {
                    assertFalse((boolean) invocation.getArgument(1));
                    active.remove(invocation.getArgument(0)); return true;
                });
            runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(BooleanSupplier.class)))
                .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(2)).getAsBoolean());
            var activate = DroSlayerScript.class.getDeclaredMethod("activateOffensivePrayer");
            activate.setAccessible(true);
            activate.invoke(script); activate.invoke(script); activate.invoke(script);
            assertEquals(Set.of(Rs2PrayerEnum.PROTECT_MAGIC), active);
            magic.verifyNoInteractions();
        }
    }

    @Test public void prayerButtonWorksWithoutStartingSlayerAndIgnoresOtherButtons() {
        EventBus bus = new EventBus(); AtomicInteger calls = new AtomicInteger();
        bus.register(new DroSlayerPlugin.PrayerSettingsActions(calls::incrementAndGet));
        ConfigChanged event = new ConfigChanged(); event.setGroup(DroSlayerConfig.GROUP); event.setKey("editTaskPrayers");
        bus.post(event); assertEquals(1, calls.get());
        event.setKey("captureCurrentInventorySetup"); bus.post(event); assertEquals(1, calls.get());
        event.setGroup("anotherPlugin"); event.setKey("editTaskPrayers"); bus.post(event); assertEquals(1, calls.get());
    }
}
