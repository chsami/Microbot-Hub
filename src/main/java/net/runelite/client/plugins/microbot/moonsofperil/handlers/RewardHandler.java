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
    private static final String CHEST_SEALED_STATUS = "Lunar Chest sealed - skipping rewards";
    private static final Set<ChatMessageType> GAME_REPLY_TYPES = EnumSet.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM,
            ChatMessageType.ENGINE, ChatMessageType.MESBOX, ChatMessageType.DIALOG);
    private static volatile boolean chestSealed;
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
            chestSealed = false;
            return false;
        }
        if (chestSealed) {
            Microbot.status = CHEST_SEALED_STATUS;
            return false;
        }
        return true;
    }

    private String checkMark(boolean enabled, int widgetId) {
        if (!enabled) {
            return "disabled";
        }
        return Rs2Widget.isHidden(widgetId) ? "no check mark" : "check mark";
    }

    @Override
    public State execute() {
        BreakHandlerScript.setLockState(true);
        boss.walkToBoss(null, "Rewards Chest", rewardChestLocation);
        if (Microbot.getRs2TileObjectCache().query().interact(lunarChestGameObjectID, "Claim")) {
            sleepUntil(() -> chestSealed || Rs2Widget.isWidgetVisible(lunarChestBankAllWidgetID), 5_000);
            if (chestSealed) {
                Microbot.status = CHEST_SEALED_STATUS;
                Microbot.log("Lunar Chest is sealed: there are no rewards to claim. Skipping the chest until a moon is defeated. "
                        + "If the script stays idle, restart the plugin or fight a moon manually. Moons read as Eclipse: "
                        + checkMark(eclipseEnabled, Widgets.ECLIPSE_MOON_ID.getID())
                        + ", Blue: " + checkMark(blueEnabled, Widgets.BLUE_MOON_ID.getID())
                        + ", Blood: " + checkMark(bloodEnabled, Widgets.BLOOD_MOON_ID.getID()));
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
