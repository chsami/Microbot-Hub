package net.runelite.client.plugins.microbot.drodriftnet;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.*;
import net.runelite.api.Perspective;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class DroDriftNetScript extends Script
{
    private static final int DRIFT_UNDERWATER_REGION = 15008;
    private static final String DRIFT_PROD_MESSAGE = "You prod at the shoal of fish to scare it.";
    private static final int MAX_DRIFT_NET_FETCH_ATTEMPTS = 5;
    private static final WorldPoint DRIFT_FIRST_TUNNEL = new WorldPoint(3729, 10295, 1);

    private DroDriftNetConfig config;
    private BaseProfileDro profile;

    private volatile String status = "Starting";
    private volatile boolean runInputsActive;

    private volatile boolean fishingXpInitialized;
    private volatile int startFishingXp;
    private volatile int fishingXpGained;

    private volatile boolean hunterXpInitialized;
    private volatile int startHunterXp;
    private volatile int hunterXpGained;

    // Drift-net event state.
    private final Set<Integer> driftFish = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> driftTaggedFish = new ConcurrentHashMap<>();
    private final List<DriftNet> driftNets = Arrays.asList(
            new DriftNet(NullObjectID.NULL_31433, Varbits.NORTH_NET_STATUS, Varbits.NORTH_NET_CATCH_COUNT,
                    ImmutableSet.of(new WorldPoint(3737, 10299, 1), new WorldPoint(3737, 10300, 1),
                            new WorldPoint(3737, 10301, 1), new WorldPoint(3737, 10302, 1),
                            new WorldPoint(3737, 10303, 1), new WorldPoint(3737, 10304, 1))),
            new DriftNet(NullObjectID.NULL_31434, Varbits.SOUTH_NET_STATUS, Varbits.SOUTH_NET_CATCH_COUNT,
                    ImmutableSet.of(new WorldPoint(3737, 10287, 1), new WorldPoint(3737, 10288, 1),
                            new WorldPoint(3737, 10289, 1), new WorldPoint(3737, 10290, 1),
                            new WorldPoint(3737, 10291, 1), new WorldPoint(3737, 10292, 1)))
    );

    private boolean inDriftNetArea;
    private boolean driftArmInteraction;
    private GameObject annette;

    private int driftNetFetchAttempts;
    private long driftWithdrawRequestedAt;
    private boolean driftWithdrawAmountSent;
    private volatile int driftPendingFish = -1;
    private int driftPendingFishTick;
    private int driftLastRunAttemptTick = -100;
    private boolean driftCameraApplied;
    private long driftNextEmptyClickAt;
    private long driftNextDoubleClickAt;
    private DroDriftTravel driftTravel;

    public boolean run(DroDriftNetConfig config)
    {
        this.config = config;
        runInputsActive = true;
        resetRuntime();

        BaseProfileDro.Settings settings = new BaseProfileDro.Settings()
                .activity(Activity.GENERAL_FISHING)
                .activityIntensity(ActivityIntensity.LOW)
                .playStyle(PlayStyle.PASSIVE)
                .nativeRandomMouseChance(0.0)
                .mouseActivity(config.mouseActivity())
                .parkChances(100, 90, 70, 70)
                .customBreaksEnabled(config.smartBreaks())
                .breakIntervals(config.minBreakInterval(), Math.max(config.minBreakInterval(), config.maxBreakInterval()))
                .logoutBreakChance(config.logoutBreakChance())
                .afkBreakDuration(config.afkBreakMin(), Math.max(config.afkBreakMin(), config.afkBreakMax()))
                .logoutBreakDuration(config.logoutBreakMin(), Math.max(config.logoutBreakMin(), config.logoutBreakMax()))
                .postLoginSettleSeconds(config.postLoginSettle())
                .overlayEnabled(false)
                .writeBreakStatusToMicrobot(false);

        profile = new BaseProfileDro(settings)
                .setBreakSettingsUpdater(liveBreakSettings -> liveBreakSettings
                        .customBreaksEnabled(config.smartBreaks())
                        .breakIntervals(config.minBreakInterval(), Math.max(config.minBreakInterval(), config.maxBreakInterval()))
                        .logoutBreakChance(config.logoutBreakChance())
                        .afkBreakDuration(config.afkBreakMin(), Math.max(config.afkBreakMin(), config.afkBreakMax()))
                        .logoutBreakDuration(config.logoutBreakMin(), Math.max(config.logoutBreakMin(), config.logoutBreakMax()))
                        .postLoginSettleSeconds(config.postLoginSettle()))
                .setNativeTemplateApplier(() ->
                        net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban
                                .antibanSetupTemplates.applyFishingSetup())
                .setAttentionAction(() -> false);
        profile.start();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run())
                {
                    return;
                }

                boolean loggedIn = Microbot.isLoggedIn();
                profile.setFastInputDelays(true);

                boolean moving = loggedIn && Rs2Player.isMoving();
                boolean safeForBreak = loggedIn && !moving && !Rs2Bank.isOpen();
                boolean idleOpportunity = false;

                // Keep ticking while logged out so BaseProfileDro can perform its own return/login flow.
                if (profile.tick(safeForBreak, idleOpportunity, config.mouseActivity()))
                {
                    status = profile.getProfileStatus();
                    return;
                }

                if (!loggedIn)
                {
                    setStatus("Logged out");
                    return;
                }

                initializeXpIfNeeded();
                handleDriftNet();
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                setStatus("Recovering: " + ex.getClass().getSimpleName());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    public boolean ownsRunInputs()
    {
        return runInputsActive;
    }

    public boolean isAutoLoginBlockedByBreak()
    {
        BaseProfileDro activeProfile = profile;
        return activeProfile != null && activeProfile.isAutoLoginBlockedByBreak();
    }

    /** Re-evaluate entry, equipment and supplies from observed state on every tick. */
    private void handleDriftNet()
    {
        traceDroAction("handleDriftNet");
        WorldPoint here = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            // Starting inside the instance does not emit a fresh LOGGED_IN event.
            updateDriftAreaFlag();
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? null : WorldPoint.fromLocalInstance(
                    Microbot.getClient(), player.getLocalLocation());
        }).orElse(null);
        if (here == null) return;
        if (!inDriftNetArea && !DroDriftTravel.atFishingEntrance(here))
        {
            if (!config.driftAutoTravel()) {
                setStatus("Start Drift Net near the underwater entrance or enable automatic travel");
                return;
            }
            if (driftTravel == null)
                driftTravel = new DroDriftTravel(config, mainScheduledFuture,
                        profile, this::setStatus);
            driftTravel.tick(here);
            return;
        }

        // A withdrawal owns its amount dialogue until inventory confirms success or it times out.
        // General continue/entry handling must not consume or dismiss that prompt.
        if (driftWithdrawRequestedAt != 0 && !Rs2Inventory.hasItem(ItemID.FOSSIL_DRIFT_NET))
        {
            fetchDriftNetsFromAnnetteOriginal();
            return;
        }

        // Resolve pending dialogues before another movement/equipment click can dismiss them.
        if (Rs2Dialogue.hasSelectAnOption())
        {
            String option = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Rs2Dialogue.getDialogueOptions().stream().map(Widget::getText)
                            .filter(text -> isDriftEntryOption(text, here.getX() >= 3737))
                            .findFirst().orElse(null)).orElse(null);
            if (option != null)
            {
                profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
                Rs2Dialogue.clickOption(option, true);
                profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
            }
            else setStatus("Drift Net: unrecognised dialogue; select the entry option");
            return;
        }
        if (Rs2Dialogue.hasContinue())
        {
            profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
            Rs2Dialogue.clickContinue();
            profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
            return;
        }
        if (here.getX() < 3737)
        {
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            if (Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null)
            {
                if (Rs2Inventory.emptySlotCount() == 0)
                {
                    stopDrift("Free an inventory slot to unequip your weapon");
                    return;
                }
                profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
                Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
                profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
                return;
            }
            setStatus("Entering Drift Net area");
            if (here.getY() <= 10294)
            {
                profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
                Rs2Walker.walkCanvas(DRIFT_FIRST_TUNNEL);
                profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
            }
            else clickDriftObject(ObjectID.FOSSIL_UNDERWATER_DRIFTNET_CURTAIN, "Navigate");
            return;
        }

        if (setDriftCameraOnEntry()) return;

        int tick = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getTickCount()).orElse(0);
        int energy = Rs2Player.getRunEnergy();
        // Dose before energy is exhausted, leaving enough for the swim-speed reset.
        if (energy <= 50 && !Rs2Player.hasStaminaActive())
        {
            Rs2ItemModel stamina = Rs2Inventory.get(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).startsWith("stamina potion("));
            if (stamina != null)
            {
                setStatus("Drinking stamina potion");
                if (profile.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                        () -> Rs2Inventory.interact(stamina, "Drink"))) return;
            }
        }

        // The cave applies fast swimming only when run is enabled before wielding.
        // After run expires, repeat that order once energy has recovered above 30%.
        boolean resetRun = DriftNetTargeting.shouldEnableRun(Rs2Player.isRunEnabled(), energy);
        if (resetRun && Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null)
        {
            if (Rs2Inventory.emptySlotCount() == 0)
            {
                stopDrift("Free an inventory slot to reset swim speed");
                return;
            }
            setStatus("Removing weapon to reset swim speed");
            profile.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON));
            return;
        }
        if (resetRun && tick - driftLastRunAttemptTick >= 2)
        {
            driftLastRunAttemptTick = tick;
            setStatus("Enabling fast swim");
            profile.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Player.toggleRunEnergy(true));
            return;
        }
        if (!Rs2Player.isRunEnabled() && Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null)
            return; // Wait for the run orb to confirm before wielding.

        if (Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null)
        {
            Rs2ItemModel weapon = Rs2Inventory.get(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("trident"));
            if (weapon != null)
            {
                setStatus("Equipping trident");
                profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
                Rs2Inventory.interact(weapon, "Wield");
                profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
                // Wait for either success or the warning; never repeat Wield over its dialogue.
                sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null
                        || Rs2Dialogue.isInDialogue(), 2500);
                return;
            }
        }

        Rs2ItemModel gracefulBoots = Rs2Inventory.get(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("graceful boots"));
        if (gracefulBoots != null && Rs2Equipment.isWearing(ItemID.MUDSKIPPER_FLIPPERS, ItemID.DARK_FLIPPERS))
        {
            setStatus("Equipping graceful boots");
            if (profile.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Inventory.interact(gracefulBoots, "Wear"))) return;
        }

        // Store carried noted nets first, then the original withdrawal takes up to 19.
        if (Rs2Inventory.hasItem(ItemID.Cert.FOSSIL_DRIFT_NET))
        {
            depositNotedDriftNets();
            return;
        }
        if (!Rs2Inventory.hasItem(ItemID.FOSSIL_DRIFT_NET))
        {
            fetchDriftNetsFromAnnetteOriginal();
            return;
        }
        driftNetFetchAttempts = 0;
        driftWithdrawRequestedAt = 0;
        driftWithdrawAmountSent = false;
        if (Rs2Widget.isWidgetVisible(20250629))
        {
            profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            return;
        }
        // Refresh from the player-state cache even when the plugin missed a spawn/varbit event.
        for (DriftNet net : driftNets)
            net.setStatus(DriftNetStatus.of(Microbot.getVarbitValue(net.getStatusVarbit())));
        if (driftNets.stream().anyMatch(net -> net.getStatus() == DriftNetStatus.FULL
                || net.getStatus() == DriftNetStatus.UNSET))
        {
            if (Rs2Player.isAnimating()) return;
            processDriftNetsOriginal();
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        chaseNearbyDriftFish();
    }

    /** Apply the randomized reference view in one client-thread operation per cave entry. */
    private boolean setDriftCameraOnEntry()
    {
        if (driftCameraApplied) return false;
        boolean applied = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            int pitch = 3004 + ThreadLocalRandom.current().nextInt(-90, 91);
            int yaw = 10189 + ThreadLocalRandom.current().nextInt(-305, 306);
            int targetScale = 148 + ThreadLocalRandom.current().nextInt(-4, 5);
            int scale = Microbot.getClient().getScale();
            if (scale <= 0) return false;
            // Client FOV units are logarithmic: +256 doubles projection scale.
            // Using the live scale accounts for the current viewport dimensions.
            int zoom = Rs2Camera.getZoom() + (int) Math.round(256.0
                    * Math.log(targetScale / (double) scale) / Math.log(2.0));
            zoom = Math.max(-400, Math.min(1400, zoom));
            Microbot.getClient().setCameraPitchTarget(Math.min(3064, pitch));
            Microbot.getClient().setCameraYawTarget(yaw);
            Microbot.getClient().runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
            return true;
        }).orElse(false);
        driftCameraApplied = applied;
        if (applied) {
            long now = System.currentTimeMillis();
            driftNextEmptyClickAt = now + ThreadLocalRandom.current().nextInt(60000, 240001);
            driftNextDoubleClickAt = now + ThreadLocalRandom.current().nextInt(15000, 60001);
        }
        return true;
    }

    static boolean isDriftEntryOption(String text, boolean inside)
    {
        if (text == null) return false;
        String plain = text.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
        if (inside) return plain.startsWith("i'll steer clear");
        return plain.equals("enter instance.") || plain.equals("enter instance")
                || (plain.matches(".*\\b200\\s+numulites?\\b.*")
                    && !plain.contains("permanent"));
    }

    static boolean isDriftAmountPrompt(String title, String input)
    {
        return isDriftAmountPromptText(title) || isDriftAmountPromptText(input);
    }

    private static boolean isDriftAmountPromptText(String text)
    {
        if (text == null) return false;
        String plain = text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
                .replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return plain.matches("enter (?:an )?amount[:?]?")
                || plain.matches("how many(?:[ ?:].*)?");
    }

    static int driftWithdrawalAmount(int weight, int slots, int stored)
    {
        return Math.max(0, Math.min(19, Math.min(Math.min(23 - weight, slots), stored)));
    }

    /** Local object click avoids generic world-route recovery inside this instance. */
    private boolean clickDriftObject(int id, String action)
    {
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withId(id).nearestOnClientThread();
        if (object == null) return false;
        profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
        boolean clicked = object.click(action);
        if (clicked) profile.afterAction(BaseProfileDro.ActionPhase.SETUP, false);
        return clicked;
    }

    private void stopDrift(String reason)
    {
        Microbot.log("Drift Net stopped: " + reason);
        shutdown();
        setStatus(reason);
    }

    private void traceDroAction(String action)
    {
    }

    /** Use the noted nets withdrawn at the island bank on Annette; Store-All then stores them. */
    private void depositNotedDriftNets()
    {
        if (driftNetFetchAttempts >= MAX_DRIFT_NET_FETCH_ATTEMPTS)
        {
            stopDrift("Could not deposit noted drift nets with Annette");
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        setStatus("Depositing noted drift nets with Annette");
        // Using the notes opens Annette's storage; Store-All on the inventory side stores them.
        if (Rs2Widget.isWidgetVisible(20250629))
        {
            storeAllNotedDriftNets();
            return;
        }
        profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        boolean used = Rs2Inventory.useItemOnObject(ItemID.Cert.FOSSIL_DRIFT_NET, ObjectID.FOSSIL_MERMAID_DRIFTNETS);
        profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        if (!used || !sleepUntil(() -> Rs2Widget.isWidgetVisible(20250629)
                || !Rs2Inventory.hasItem(ItemID.Cert.FOSSIL_DRIFT_NET), 5000))
            driftNetFetchAttempts++;
    }

    /** Store-All on the noted net in Annette's inventory panel (307:0, op 4). */
    private void storeAllNotedDriftNets()
    {
        Rs2ItemModel noted = Rs2Inventory.get(ItemID.Cert.FOSSIL_DRIFT_NET);
        if (noted == null) return;
        int slot = noted.getSlot();
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget panel = Microbot.getClient().getWidget(20119552);
            Widget item = panel == null || panel.isHidden() ? null : panel.getChild(slot);
            return item == null || item.isHidden() ? null : item.getBounds();
        }).orElse(null);
        if (bounds == null)
        {
            driftNetFetchAttempts++;
            return;
        }
        profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        Microbot.doInvoke(new NewMenuEntry("Store-All", slot, 20119552, MenuAction.CC_OP.getId(), 4,
                ItemID.Cert.FOSSIL_DRIFT_NET, "<col=ff9040>Drift net</col>"), bounds);
        profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        if (sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.Cert.FOSSIL_DRIFT_NET), 4000))
            driftNetFetchAttempts = 0;
        else
            driftNetFetchAttempts++;
    }

    /** Annette supplies nets inside the instance; an empty store is not a bank route. */
    private void fetchDriftNetsFromAnnetteOriginal()
    {
        if (driftNetFetchAttempts >= MAX_DRIFT_NET_FETCH_ATTEMPTS)
        {
            stopDrift("Could not withdraw nets from Annette; check storage and access");
            return;
        }
        int stored = Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.FOSSIL_DRIFTNET_STORE);
        if (stored <= 0)
        {
            stopDrift("Annette has no drift nets stored; supply her with noted nets and restart");
            return;
        }
        int weight = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWeight()).orElse(25);
        int amount = driftWithdrawalAmount(weight, Rs2Inventory.emptySlotCount(), stored);
        if (amount == 0)
        {
            stopDrift("Make room and reduce weight before withdrawing drift nets");
            return;
        }
        if (driftWithdrawRequestedAt != 0)
        {
            boolean amountPrompt = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget title = Microbot.getClient().getWidget(162, 43);
                Widget input = Microbot.getClient().getWidget(162, 44);
                return isDriftAmountPrompt(
                        title == null || title.isHidden() ? null : title.getText(),
                        input == null || input.isHidden() ? null : input.getText());
            }).orElse(false);
            if (!driftWithdrawAmountSent && amountPrompt)
            {
                profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
                setStatus("Withdrawing " + amount + " drift nets");
                Rs2Keyboard.typeString(String.valueOf(amount));
                Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
                driftWithdrawAmountSent = true;
                driftWithdrawRequestedAt = System.currentTimeMillis();
            }
            else if (System.currentTimeMillis() - driftWithdrawRequestedAt >= 4000)
            {
                Microbot.log(driftWithdrawAmountSent
                        ? "Drift Net: withdrawal did not add nets; retrying"
                        : "Drift Net: amount prompt not detected; retrying");
                driftNetFetchAttempts++;
                driftWithdrawRequestedAt = 0;
                driftWithdrawAmountSent = false;
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
            return;
        }
        setStatus("Fetching drift nets from Annette");
        if (!Rs2Widget.isWidgetVisible(20250629))
        {
            clickDriftObject(ObjectID.FOSSIL_MERMAID_DRIFTNETS, "Nets");
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(20250629), 4000))
                driftNetFetchAttempts++;
            return;
        }
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(20250629);
            return widget == null || widget.isHidden() ? null : widget.getBounds();
        }).orElse(null);
        if (bounds == null)
        {
            driftNetFetchAttempts++;
            return;
        }
        profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        Microbot.doInvoke(new NewMenuEntry(0, 20250629, 57, 3,
                ItemID.FOSSIL_DRIFT_NET, "<col=ff9040>Drift net</col>"), bounds);
        profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
        driftWithdrawRequestedAt = System.currentTimeMillis();
        driftWithdrawAmountSent = false;
    }

    private void processDriftNetsOriginal()
    {
        traceDroAction("processDriftNetsOriginal");
        for (DriftNet net : driftNets)
        {
            if (net.getStatus() == DriftNetStatus.UNSET)
            {
                handleUnsetDriftNetOriginal(net);
                return;
            }
        }
        for (DriftNet net : driftNets)
        {
            if (net.getStatus() == DriftNetStatus.FULL)
            {
                handleFullDriftNetOriginal(net);
                return;
            }
        }
    }

    private void handleFullDriftNetOriginal(DriftNet net)
    {
        traceDroAction("handleFullDriftNetOriginal");
        setStatus("Looting drift net");

        if (!clickDriftObject(net.getObjectId(), null)) return;
        boolean opened = sleepUntil(() -> Rs2Widget.isWidgetVisible(39780359)
                || Microbot.getVarbitValue(net.getStatusVarbit()) != 3, 4000);
        if (!opened) return;

        if (config.driftBankFish())
        {
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(39780359), 2500)) return;

            if (profile != null)
            {
                profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            }

            Rs2Widget.clickWidget(39780359);

            if (profile != null)
            {
                profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            }

            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(39780365), 2500)) return;

            if (profile != null)
            {
                profile.beforeAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            }

            Rs2Widget.clickWidget(39780365);

            if (profile != null)
            {
                profile.afterAction(BaseProfileDro.ActionPhase.MAINTENANCE, false);
            }
        }
    }

    private void handleUnsetDriftNetOriginal(DriftNet net)
    {
        traceDroAction("handleUnsetDriftNetOriginal");
        if (!Rs2Inventory.hasItem(ItemID.FOSSIL_DRIFT_NET)) return;
        Rs2TileObjectModel anchor = Microbot.getRs2TileObjectCache().query()
                .withId(net.getObjectId()).nearestOnClientThread();
        if (anchor == null)
        {
            setStatus("Waiting for drift net anchor");
            return;
        }
        String action = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition composition = anchor.getObjectComposition();
            return driftSetupAction(composition == null ? null : composition.getActions());
        }).orElse(null);
        if (action == null)
        {
            setStatus("Waiting for drift net Set-up action");
            return;
        }
        setStatus("Setting drift net");
        profile.beforeAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false);
        boolean clicked = anchor.click(action);
        if (clicked) profile.afterAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false);
        if (clicked && !sleepUntil(() -> Microbot.getVarbitValue(net.getStatusVarbit()) != 0, 5000))
            setStatus("Drift net setup not confirmed; retrying");
    }

    static String driftSetupAction(String[] actions)
    {
        if (actions == null) return null;
        for (String action : actions)
        {
            if (action != null && action.replaceAll("[-\\s]", "").equalsIgnoreCase("setup"))
                return action;
        }
        return null;
    }

    private void chaseNearbyDriftFish()
    {
        List<WorldPoint> acceptingTiles = driftNets.stream()
                .filter(net -> DriftNetTargeting.acceptsFish(net.getStatus()))
                .flatMap(net -> net.getAdjacentTiles().stream()).collect(Collectors.toList());
        if (acceptingTiles.isEmpty()) return;
        int tick = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getTickCount()).orElse(0);
        boolean confirmedProd = false;
        if (driftPendingFish >= 0)
        {
            if (!driftTaggedFish.containsKey(driftPendingFish) && tick - driftPendingFishTick < 2)
                return;
            if (driftTaggedFish.containsKey(driftPendingFish)) {
                confirmedProd = true;
                profile.parkAfterDriftProd(config.mouseActivity());
            }
            driftPendingFish = -1;
        }
        List<Rs2NpcModel> fishNpcs = Microbot.getRs2NpcCache().query()
                .withId(NpcID.FOSSIL_FISH_SHOAL).toListOnClientThread();
        Integer targetIndex = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null) return null;
            WorldPoint here = WorldPoint.fromLocalInstance(Microbot.getClient(), player.getLocalLocation());
            Map<Integer, WorldPoint> positions = new HashMap<>();
            for (Rs2NpcModel npc : fishNpcs)
                if (npc.getLocalLocation() != null)
                    positions.put(npc.getIndex(), WorldPoint.fromLocalInstance(
                            Microbot.getClient(), npc.getLocalLocation()));
            Set<Integer> reachable = fishNpcs.stream().filter(Rs2NpcModel::isReachable)
                    .map(Rs2NpcModel::getIndex).collect(Collectors.toSet());
            boolean localGroup = positions.entrySet().stream().anyMatch(fish ->
                    !driftTaggedFish.containsKey(fish.getKey()) && reachable.contains(fish.getKey())
                    && DriftNetTargeting.nearbyOnSide(fish.getValue(), here, acceptingTiles));
            int bestScore = Integer.MAX_VALUE;
            Integer best = null;
            for (Map.Entry<Integer, WorldPoint> fish : positions.entrySet())
            {
                if (driftTaggedFish.containsKey(fish.getKey()) || !reachable.contains(fish.getKey())) continue;
                if (localGroup && !DriftNetTargeting.nearbyOnSide(fish.getValue(), here, acceptingTiles)) continue;
                int score = DriftNetTargeting.score(fish.getValue(), here, acceptingTiles, positions.values());
                if (score != Integer.MAX_VALUE) score += ThreadLocalRandom.current().nextInt(3);
                if (score < bestScore)
                {
                    bestScore = score;
                    best = fish.getKey();
                }
            }
            return best;
        }).orElse(null);
        if (targetIndex == null)
        {
            setStatus("Waiting for shoals near the nets");
            return;
        }
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withId(NpcID.FOSSIL_FISH_SHOAL)
                .where(fish -> fish.getIndex() == targetIndex).firstOnClientThread();
        if (npc == null) return;
        setStatus("Chasing shoal near an open net");
        profile.beforeAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true);
        boolean deadClicked = false;
        if (confirmedProd && System.currentTimeMillis() >= driftNextEmptyClickAt) {
            var local = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Player player = Microbot.getClient().getLocalPlayer();
                if (player == null) return null;
                WorldPoint template = WorldPoint.fromLocalInstance(Microbot.getClient(), player.getLocalLocation());
                if (template.getRegionID() != DRIFT_UNDERWATER_REGION || template.getX() < 3737)
                    return null;
                return Perspective.localToCanvas(Microbot.getClient(), player.getLocalLocation(), template.getPlane())
                        == null ? null : player.getLocalLocation();
            }).orElse(null);
            if (local != null) {
                Rs2Walker.walkFastLocal(local);
                deadClicked = true;
                sleep(ThreadLocalRandom.current().nextInt(250, 501));
                driftNextEmptyClickAt = System.currentTimeMillis()
                        + ThreadLocalRandom.current().nextInt(60000, 240001);
            }
        }
        driftPendingFish = targetIndex;
        driftPendingFishTick = tick;
        boolean chased = npc.click("Chase");
        if (chased) {
            profile.afterAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, !deadClicked);
            if (System.currentTimeMillis() >= driftNextDoubleClickAt) {
                Rs2NpcModel repeat = Microbot.getRs2NpcCache().query().withId(NpcID.FOSSIL_FISH_SHOAL)
                        .where(fish -> fish.getIndex() == targetIndex).firstOnClientThread();
                if (repeat != null) {
                    profile.beforeAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true);
                    if (repeat.click("Chase")) {
                        profile.afterAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, !deadClicked);
                        driftNextDoubleClickAt = System.currentTimeMillis()
                                + ThreadLocalRandom.current().nextInt(15000, 60001);
                    }
                }
            }
        }
        else driftPendingFish = -1;
    }

    public void onStatChanged(StatChanged event)
    {
        if (event == null)
        {
            return;
        }
        if (event.getSkill() == Skill.FISHING)
        {
            if (!fishingXpInitialized)
            {
                startFishingXp = event.getXp();
                fishingXpInitialized = true;
            }
            fishingXpGained = Math.max(0, event.getXp() - startFishingXp);
        }
        else if (event.getSkill() == Skill.HUNTER)
        {
            if (!hunterXpInitialized)
            {
                startHunterXp = event.getXp();
                hunterXpInitialized = true;
            }
            hunterXpGained = Math.max(0, event.getXp() - startHunterXp);
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }

        String message = event.getMessage();
        if (inDriftNetArea
                && event.getType() == ChatMessageType.SPAM
                && DRIFT_PROD_MESSAGE.equals(message))
        {
            Actor target = Microbot.getClient().getLocalPlayer() == null
                    ? null
                    : Microbot.getClient().getLocalPlayer().getInteracting();
            if (target instanceof NPC && ((NPC) target).getId() == NpcID.FOSSIL_FISH_SHOAL)
            {
                tagDriftFish((NPC) target);
            }
            else if (driftPendingFish >= 0)
            {
                driftTaggedFish.put(driftPendingFish, Microbot.getClient().getTickCount());
            }
            else
            {
                driftArmInteraction = true;
            }
        }
    }

    public void onGameStateChanged(GameStateChanged event)
    {
        if (event == null)
        {
            return;
        }
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            annette = null;
        }

        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
            case HOPPING:
            case LOADING:
                resetDriftTracker();
                break;
            case LOGGED_IN:
                updateDriftAreaFlag();
                updateDriftVarbits();
                break;
            default:
                break;
        }
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        updateDriftVarbits();
    }

    public void onInteractingChanged(InteractingChanged event)
    {
        if (!driftArmInteraction || event == null || Microbot.getClient().getLocalPlayer() == null)
        {
            return;
        }
        if (event.getSource() == Microbot.getClient().getLocalPlayer()
                && event.getTarget() instanceof NPC
                && ((NPC) event.getTarget()).getId() == NpcID.FOSSIL_FISH_SHOAL)
        {
            tagDriftFish((NPC) event.getTarget());
            driftArmInteraction = false;
        }
    }

    public void onGameTick(GameTick event)
    {
        if (!inDriftNetArea)
        {
            return;
        }

        List<DriftNet> closed = driftNets.stream()
                .filter(DriftNet::isNotAcceptingFish)
                .collect(Collectors.toList());

        driftTaggedFish.entrySet().removeIf(entry ->
                entry.getValue() + config.driftTagTimeout() < Microbot.getClient().getTickCount()
                        || isDriftFishNextToClosedNet(entry.getKey(), closed));

        driftNets.forEach(net -> net.setPrevTickStatus(net.getStatus()));
        driftArmInteraction = false;
    }

    public void onNpcSpawned(NpcSpawned event)
    {
        if (event != null && event.getNpc() != null && event.getNpc().getId() == NpcID.FOSSIL_FISH_SHOAL)
        {
            driftFish.add(event.getNpc().getIndex());
        }
    }

    public void onNpcDespawned(NpcDespawned event)
    {
        if (event != null && event.getNpc() != null)
        {
            driftFish.remove(event.getNpc().getIndex());
            driftTaggedFish.remove(event.getNpc().getIndex());
        }
    }

    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (event == null || event.getGameObject() == null)
        {
            return;
        }
        GameObject object = event.getGameObject();
        if (object.getId() == ObjectID.FOSSIL_MERMAID_DRIFTNETS)
        {
            annette = object;
        }
        for (DriftNet net : driftNets)
        {
            if (net.getObjectId() == object.getId())
            {
                net.setNet(object);
            }
        }
    }

    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (event == null || event.getGameObject() == null)
        {
            return;
        }
        GameObject object = event.getGameObject();
        if (object == annette)
        {
            annette = null;
        }
        for (DriftNet net : driftNets)
        {
            if (net.getNet() == object)
            {
                net.setNet(null);
            }
        }
    }

    private void updateDriftAreaFlag()
    {
        if (Microbot.getClient() == null)
        {
            inDriftNetArea = false;
            return;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null || !Microbot.getClient().getTopLevelWorldView().getScene().isInstance())
        {
            inDriftNetArea = false;
            return;
        }

        WorldPoint point = WorldPoint.fromLocalInstance(Microbot.getClient(), localPlayer.getLocalLocation());
        inDriftNetArea = point != null && point.getRegionID() == DRIFT_UNDERWATER_REGION;
    }

    private void updateDriftVarbits()
    {
        if (!inDriftNetArea || Microbot.getClient() == null)
        {
            return;
        }
        for (DriftNet net : driftNets)
        {
            net.setStatus(DriftNetStatus.of(Microbot.getClient().getVarbitValue(net.getStatusVarbit())));
            net.setCount(Microbot.getClient().getVarbitValue(net.getCountVarbit()));
        }
    }

    private void tagDriftFish(NPC fish)
    {
        if (fish != null)
        {
            driftTaggedFish.put(fish.getIndex(), Microbot.getClient().getTickCount());
        }
    }

    private boolean isDriftFishNextToClosedNet(int index, List<DriftNet> closed)
    {
        Rs2NpcModel fish = Microbot.getRs2NpcCache().query()
                .withId(NpcID.FOSSIL_FISH_SHOAL)
                .where(npc -> npc.getIndex() == index)
                .firstOnClientThread();

        if (fish == null || fish.getLocalLocation() == null || Microbot.getClient() == null)
        {
            return false;
        }

        WorldPoint fishTile = WorldPoint.fromLocalInstance(
                Microbot.getClient(),
                fish.getLocalLocation());

        return closed.stream().anyMatch(net -> net.getAdjacentTiles().contains(fishTile));
    }

    private void resetDriftTracker()
    {
        driftPendingFish = -1;
        driftPendingFishTick = 0;
        driftLastRunAttemptTick = -100;
        driftCameraApplied = false;
        driftNextEmptyClickAt = 0;
        driftNextDoubleClickAt = 0;
        driftFish.clear();
        driftTaggedFish.clear();
        driftArmInteraction = false;
        inDriftNetArea = false;
        annette = null;
        for (DriftNet net : driftNets)
        {
            net.setNet(null);
            net.setStatus(null);
            net.setPrevTickStatus(null);
            net.setCount(0);
        }
    }

    private void initializeXpIfNeeded()
    {
        if (Microbot.getClient() == null)
        {
            return;
        }

        if (!fishingXpInitialized)
        {
            int xp = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getSkillExperience(Skill.FISHING)).orElse(0);
            if (xp >= 0)
            {
                startFishingXp = xp;
                fishingXpInitialized = true;
            }
        }
        if (!hunterXpInitialized)
        {
            int xp = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getSkillExperience(Skill.HUNTER)).orElse(0);
            if (xp >= 0)
            {
                startHunterXp = xp;
                hunterXpInitialized = true;
            }
        }
    }

    private void resetRuntime()
    {
        status = "Starting";
        fishingXpInitialized = false;
        startFishingXp = 0;
        fishingXpGained = 0;
        hunterXpInitialized = false;
        startHunterXp = 0;
        hunterXpGained = 0;

        resetDriftTracker();
        driftTravel = null;
        driftCameraApplied = false;
        driftNetFetchAttempts = 0;
        driftWithdrawRequestedAt = 0;
        driftWithdrawAmountSent = false;
    }

    private void setStatus(String value)
    {
        status = value == null ? "" : value;
        Microbot.status = status;
    }

    public String getStatus()
    {
        if (profile != null && profile.isBreakActive())
        {
            return profile.getProfileStatus();
        }
        return status;
    }

    public long getRuntimeMs()
    {
        return profile == null ? 0L : profile.getSessionElapsedMs();
    }

    public String getNextBreak()
    {
        return profile == null ? "..." : profile.getBreakDisplay();
    }

    public int getFishingXpGained()
    {
        return fishingXpGained;
    }

    public long getFishingXpPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(fishingXpGained * 3_600_000.0 / runtime);
    }

    public int getHunterXpGained()
    {
        return hunterXpGained;
    }

    public long getHunterXpPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(hunterXpGained * 3_600_000.0 / runtime);
    }

    public List<DriftNet> getDriftNets()
    {
        return driftNets;
    }

    @Override
    public void shutdown()
    {
        runInputsActive = false;
        super.shutdown();
        if (profile != null)
        {
            profile.shutdown();
        }
        resetDriftTracker();
        driftTravel = null;
        driftCameraApplied = false;
        driftNetFetchAttempts = 0;
        driftWithdrawRequestedAt = 0;
        driftWithdrawAmountSent = false;
        Rs2Walker.clearWalkingRoute("Drift net shutdown");
    }
}
