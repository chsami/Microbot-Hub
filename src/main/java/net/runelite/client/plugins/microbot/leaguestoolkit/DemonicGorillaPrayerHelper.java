package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Set;

/**
 * Event-driven demonic gorilla prayer helper.
 * Uses onAnimationChanged for instant prayer switching and
 * onHitsplatApplied for hit counting to predict style switches.
 */
@Slf4j
public class DemonicGorillaPrayerHelper {

    private static final Set<Integer> ALL_GORILLA_IDS = Set.of(
            NpcID.MM2_DEMON_GORILLA_1_MELEE, NpcID.MM2_DEMON_GORILLA_1_RANGED, NpcID.MM2_DEMON_GORILLA_1_MAGIC,
            NpcID.MM2_DEMON_GORILLA_2_MELEE, NpcID.MM2_DEMON_GORILLA_2_RANGED, NpcID.MM2_DEMON_GORILLA_2_MAGIC
    );

    private static final int ANIM_MAGIC = AnimationID.DEMONIC_GORILLA_MAGIC;   // 7225
    private static final int ANIM_MELEE = AnimationID.DEMONIC_GORILLA_PUNCH;   // 7226
    private static final int ANIM_RANGED = AnimationID.DEMONIC_GORILLA_RANGE;  // 7227

    @Getter
    private String status = "Idle";
    @Getter
    private String currentStyle = "Unknown";
    @Getter
    private int blockedHitCount = 0;

    private int lastPrayedStyle = -1;
    private boolean active = false;

    public void reset() {
        status = "Idle";
        currentStyle = "Unknown";
        blockedHitCount = 0;
        lastPrayedStyle = -1;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active && lastPrayedStyle != -1) {
            deactivateCurrentPrayer();
            lastPrayedStyle = -1;
            blockedHitCount = 0;
            status = "Stopped";
            currentStyle = "None";
        }
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Called from @Subscribe onAnimationChanged in the plugin.
     * Fires INSTANTLY on the client thread when any actor's animation changes.
     */
    public void onAnimationChanged(AnimationChanged event) {
        if (!active) return;

        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) return;

        NPC npc = (NPC) actor;
        if (!ALL_GORILLA_IDS.contains(npc.getId())) return;

        // Check if this gorilla is targeting us
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null || !player.equals(npc.getInteracting())) return;

        int anim = npc.getAnimation();
        int style = animToStyle(anim);
        if (style == -1) return; // Not an attack animation

        if (style != lastPrayedStyle) {
            switchPrayer(style);
            lastPrayedStyle = style;
            blockedHitCount = 0; // Reset count on style switch
        }
    }

    /**
     * Called from @Subscribe onHitsplatApplied in the plugin.
     * Tracks blocked hits (0 damage) to predict style switches.
     */
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!active) return;

        // Only track hitsplats on the player
        Actor actor = event.getActor();
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null || !actor.equals(player)) return;

        // 0 damage = blocked by prayer
        if (event.getHitsplat().getAmount() == 0) {
            blockedHitCount++;
            status = "Praying " + currentStyle + " (" + blockedHitCount + "/3 blocked)";
            if (blockedHitCount >= 3) {
                log.info("[DemonicGorilla] 3 blocked hits — expecting style switch!");
                status = "Switch incoming!";
            }
        } else {
            // Took damage — reset counter (prayer was wrong or boulder hit)
            blockedHitCount = 0;
        }
    }

    /**
     * Called from @Subscribe onOverheadTextChanged in the plugin.
     * Detects the "Rhaaaaaaa!" scream that confirms a style switch.
     */
    public void onOverheadTextChanged(OverheadTextChanged event) {
        if (!active) return;

        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) return;

        NPC npc = (NPC) actor;
        if (!ALL_GORILLA_IDS.contains(npc.getId())) return;

        String text = event.getOverheadText();
        if (text != null && text.contains("Rhaaaaaaa")) {
            log.info("[DemonicGorilla] Style switch confirmed via overhead scream!");
            blockedHitCount = 0;
            status = "Style switched!";
        }
    }

    private int animToStyle(int animation) {
        if (animation == ANIM_MELEE) return 0;
        if (animation == ANIM_RANGED) return 1;
        if (animation == ANIM_MAGIC) return 2;
        return -1;
    }

    private void deactivateCurrentPrayer() {
        switch (lastPrayedStyle) {
            case 0: Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false); break;
            case 1: Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false); break;
            case 2: Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false); break;
        }
        log.info("[DemonicGorilla] Deactivated prayer");
    }

    private void switchPrayer(int style) {
        switch (style) {
            case 0:
                currentStyle = "Melee";
                log.info("[DemonicGorilla] → Protect from Melee (instant)");
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                break;
            case 1:
                currentStyle = "Ranged";
                log.info("[DemonicGorilla] → Protect from Missiles (instant)");
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                break;
            case 2:
                currentStyle = "Magic";
                log.info("[DemonicGorilla] → Protect from Magic (instant)");
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                break;
        }
    }
}
