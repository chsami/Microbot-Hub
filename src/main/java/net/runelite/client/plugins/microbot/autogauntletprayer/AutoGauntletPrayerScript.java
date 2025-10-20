package net.runelite.client.plugins.microbot.autogauntletprayer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.autogauntletprayer.AutoGauntletPrayerConfig;
import net.runelite.client.plugins.microbot.autogauntletprayer.AutoGauntletPrayerPlugin;
import net.runelite.client.plugins.microbot.autogauntletprayer.AutoGauntletPrayerState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;


import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoGauntletPrayerScript extends Script {

    public static long agpWeaponTime;
    public static long agpPrayTime;
    private long currentTime;

    @Getter
    private static int timer1Count = 0;
    @Getter
    private static long timer1Time = 0;

    private final AutoGauntletPrayerPlugin plugin;
    private final AutoGauntletPrayerConfig config;

    public static AutoGauntletPrayerState agpstate = AutoGauntletPrayerState.Idle;


    @Inject
    public AutoGauntletPrayerScript(AutoGauntletPrayerPlugin plugin, AutoGauntletPrayerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    
    public void Timer1() {
        agpPrayTime = currentTime;

    }

    public static void timer1() {
        timer1Time = System.currentTimeMillis();
        timer1Count++;
    }


    public static String getFormattedTimer1Time() {
        if (timer1Time == 0) return "Never";
        long seconds = (System.currentTimeMillis() - timer1Time) / 1000;
        return seconds + "s ago";
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}
