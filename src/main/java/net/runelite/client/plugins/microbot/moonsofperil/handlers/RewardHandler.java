package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsofperil.enums.Locations;
import net.runelite.client.plugins.microbot.moonsofperil.enums.State;
import net.runelite.client.plugins.microbot.moonsofperil.enums.Widgets;
import net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class RewardHandler implements BaseHandler {

    @Getter
    private static final AtomicInteger rewardChestCount = new AtomicInteger();
    private static final WorldPoint rewardChestLocation = Locations.REWARDS_CHEST_LOBBY.getWorldPoint();
    private static final int lunarChestGameObjectID = 51346;
    private static final int lunarChestBankAllWidgetID = 56885268;
    private static final String CHEST_SEALED_MESSAGE = "sealed tightly shut";
    private static final String CHEST_SEALED_STATUS = "Lunar Chest sealed, no rewards. Idle until a moon can be fought - see log";
    private static final String MOON_AVAILABLE_STATUS = "Moon available - continuing";
    private static final long SEALED_LOG_INTERVAL_MS = 300_000;
    private static final Set<ChatMessageType> GAME_REPLY_TYPES = EnumSet.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM,
            ChatMessageType.ENGINE, ChatMessageType.MESBOX, ChatMessageType.DIALOG);
    private static volatile boolean chestSealed;
    static volatile long lastSealedLogAt;
    private boolean eclipseEnabled;
    private boolean blueEnabled;
    private boolean bloodEnabled;
    private final boolean debugLogging;
    private final net.runelite.client.plugins.microbot.moonsofperil.handlers.BossHandler boss;

    @Inject
    public RewardHandler(MoonsOfPerilConfig cfg) {
        this.eclipseEnabled = cfg.enableEclipse();
        this.blueEnabled = cfg.enableBlue();
        this.bloodEnabled = cfg.enableBlood();
        this.boss = new BossHandler(cfg);
        this.debugLogging = cfg.debugLogging();
        chestSealed = false;
        lastSealedLogAt = 0;
    }

    public static void onChatMessage(ChatMessageType type, String message) {
        if (type != null && GAME_REPLY_TYPES.contains(type) && message != null && message.contains(CHEST_SEALED_MESSAGE)) {
            chestSealed = true;
        }
    }

    @Override
    public boolean validate() {
        boolean eclipseDone = !eclipseEnabled || !boss.bossIsAlive("Eclipse Moon", Widgets.ECLIPSE_MOON_ID.getID());
        boolean blueDone = !blueEnabled || !boss.bossIsAlive("Blue Moon", Widgets.BLUE_MOON_ID.getID());
        boolean bloodDone = !bloodEnabled || !boss.bossIsAlive("Blood Moon", Widgets.BLOOD_MOON_ID.getID());
        if (!(eclipseDone && blueDone && bloodDone)) {
            if (chestSealed) {
                chestSealed = false;
                lastSealedLogAt = 0;
                Microbot.status = MOON_AVAILABLE_STATUS;
            }
            return false;
        }
        if (chestSealed) {
            reportSealedChest();
            return false;
        }
        return true;
    }

    private void reportSealedChest() {
        Microbot.status = CHEST_SEALED_STATUS;
        long now = System.currentTimeMillis();
        if (lastSealedLogAt != 0 && now - lastSealedLogAt < SEALED_LOG_INTERVAL_MS) {
            return;
        }
        lastSealedLogAt = now;
        Microbot.log("Lunar Chest is sealed: there are no rewards to claim. The script will not click the chest again and waits "
                + "until one of your enabled moons can be fought. If it stays idle, stop the plugin, defeat a moon yourself, "
                + "then start it again. If this keeps happening, post this line in Bug Reports. Moon status seen by the script - Eclipse: "
                + moonStatus(eclipseEnabled, Widgets.ECLIPSE_MOON_ID.getID())
                + ", Blue: " + moonStatus(blueEnabled, Widgets.BLUE_MOON_ID.getID())
                + ", Blood: " + moonStatus(bloodEnabled, Widgets.BLOOD_MOON_ID.getID()));
    }

    private String moonStatus(boolean enabled, int widgetId) {
        if (!enabled) {
            return "not enabled";
        }
        return Rs2Widget.isHidden(widgetId) ? "not defeated" : "defeated (check mark)";
    }

    @Override
    public State execute() {
        BreakHandlerScript.setLockState(true);
        boss.walkToBoss(null, "Rewards Chest", rewardChestLocation);
        if (Microbot.getRs2TileObjectCache().query().interact(lunarChestGameObjectID, "Claim")) {
            sleepUntil(() -> chestSealed || Rs2Widget.isWidgetVisible(lunarChestBankAllWidgetID), 5_000);
            if (chestSealed) {
                reportSealedChest();
                BreakHandlerScript.setLockState(false);
                return State.IDLE;
            }
            if (Rs2Widget.isWidgetVisible(lunarChestBankAllWidgetID)) {
                if (debugLogging) {Microbot.log("Successfully claimed rewards from Lunar Chest");}
                rewardChestCount.incrementAndGet();
            }
        }
        if (Rs2Widget.clickWidget(lunarChestBankAllWidgetID)) {
            if (debugLogging) {Microbot.log("Successfully banked all rewards");}
            sleep(1_200);
        }
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }
}
