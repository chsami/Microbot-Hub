package net.runelite.client.plugins.microbot.combathotkeys;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@PluginDescriptor(
        name = PluginDescriptor.Cicire + "Combat hotkeys",
        description = "A plugin to bind hotkeys to combat stuff",
        tags = {"combat", "hotkeys", "microbot"},
        authors = {"Cicire"},
        version = CombatHotkeysPlugin.version,
        minClientVersion = "2.0.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        iconUrl = "https://chsami.github.io/Microbot-Hub/CombatHotkeysPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/CombatHotkeysPlugin/assets/card.jpg"
)
@Slf4j
public class CombatHotkeysPlugin extends Plugin implements KeyListener {
    // v1.1.2 — fix: replaced runOnSeperateThread (silently drops calls when a prior
    //          task is still running on the shared ClientThread executor) with a
    //          dedicated single-thread ExecutorService owned by this plugin.
    //          Added debug logging + on-screen overlay panel to trace hotkey dispatch.
    public static final String version = "1.1.2";

    // -------------------------------------------------------------------------
    // DEBUG STATE — read by CombatHotkeysOverlay to render the debug panel
    // -------------------------------------------------------------------------

    /** True while debug mode is on (toggled via config). */
    @Getter
    volatile boolean debugMode = false;

    /** Last hotkey name that reached keyPressed. */
    @Getter
    final AtomicReference<String> lastKeyReceived = new AtomicReference<>("-");

    /** Timestamp of the last keyPressed hit (epoch ms). */
    @Getter
    volatile long lastKeyTimestamp = 0;

    /** Last action that was dispatched to the executor. */
    @Getter
    final AtomicReference<String> lastActionDispatched = new AtomicReference<>("-");

    /** How many hotkey actions have been submitted to the executor total. */
    @Getter
    final AtomicInteger totalActionsSubmitted = new AtomicInteger(0);

    /** How many hotkey actions completed without throwing. */
    @Getter
    final AtomicInteger totalActionsSucceeded = new AtomicInteger(0);

    /** How many hotkey actions threw an exception. */
    @Getter
    final AtomicInteger totalActionsFailed = new AtomicInteger(0);

    /** Last error message from the executor, if any. */
    @Getter
    final AtomicReference<String> lastError = new AtomicReference<>("-");

    // -------------------------------------------------------------------------
    // PRIVATE EXECUTOR
    // runOnSeperateThread() uses a single scheduledFuture on the ClientThread
    // singleton.  If *any* other plugin or the script loop has submitted a task
    // that hasn't finished yet the gate `if (!scheduledFuture.isDone()) return`
    // silently drops our call.  A plugin-owned executor has no such contention.
    // -------------------------------------------------------------------------
    private ExecutorService hotkeyExecutor;

    @Inject
    private CombatHotkeysConfig config;

    @Provides
    CombatHotkeysConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CombatHotkeysConfig.class);
    }

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CombatHotkeysOverlay overlay;

    @Inject
    private CombatHotkeysScript script;

    // -------------------------------------------------------------------------
    // LIFECYCLE
    // -------------------------------------------------------------------------

    @Override
    protected void startUp() throws AWTException {
        hotkeyExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CombatHotkeys-executor");
            t.setDaemon(true);
            return t;
        });
        log.info("[CombatHotkeys] Plugin starting — executor created");

        keyManager.registerKeyListener(this);

        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
        log.info("[CombatHotkeys] Plugin started successfully (v{})", version);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        keyManager.unregisterKeyListener(this);
        overlayManager.remove(overlay);

        if (hotkeyExecutor != null) {
            hotkeyExecutor.shutdownNow();
            hotkeyExecutor = null;
        }
        log.info("[CombatHotkeys] Plugin shut down");
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Submit an action to the plugin-owned executor.
     *
     * Every submission is logged so we can tell in the debug overlay (and in
     * the RuneLite log) whether the keypress is reaching the dispatcher at all,
     * whether the executor accepted it, and whether it threw.
     */
    private void dispatch(String actionName, Runnable action) {
        if (hotkeyExecutor == null || hotkeyExecutor.isShutdown()) {
            log.warn("[CombatHotkeys] dispatch('{}') — executor is null/shutdown, ignoring", actionName);
            lastError.set("executor null/shutdown for: " + actionName);
            return;
        }

        lastActionDispatched.set(actionName);
        totalActionsSubmitted.incrementAndGet();
        log.debug("[CombatHotkeys] Submitting '{}' to executor", actionName);

        hotkeyExecutor.submit(() -> {
            try {
                log.debug("[CombatHotkeys] Executing '{}'", actionName);
                action.run();
                totalActionsSucceeded.incrementAndGet();
                log.debug("[CombatHotkeys] '{}' completed OK", actionName);
            } catch (Exception ex) {
                totalActionsFailed.incrementAndGet();
                lastError.set(actionName + ": " + ex.getMessage());
                log.error("[CombatHotkeys] '{}' threw an exception: {}", actionName, ex.getMessage(), ex);
            }
        });
    }

    /** Record which key was just pressed and log it. */
    private void recordKeyHit(String keyName) {
        lastKeyReceived.set(keyName);
        lastKeyTimestamp = Instant.now().toEpochMilli();
        log.debug("[CombatHotkeys] keyPressed matched: '{}' | loggedIn={} | thread={}",
                keyName,
                Microbot.isLoggedIn(),
                Thread.currentThread().getName());
    }

    // -------------------------------------------------------------------------
    // KEY LISTENER
    // -------------------------------------------------------------------------

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Refresh debug flag from config on every keypress so toggling it in
        // the config panel takes effect immediately without a restart.
        debugMode = config.debugMode();

        if (!Microbot.isLoggedIn()) {
            if (debugMode) {
                log.debug("[CombatHotkeys] keyPressed — not logged in, ignoring (keyCode={})", e.getKeyCode());
            }
            return;
        }

        if (config.dance().matches(e)) {
            recordKeyHit("dance");
            e.consume();
            script.dance = !script.dance;
            log.debug("[CombatHotkeys] dance toggled -> {}", script.dance);
        }

        // ------------------------------------------------------------------
        // OFFENSIVE PRAYERS
        // ------------------------------------------------------------------
        if (config.offensiveMeleeKey().matches(e)) {
            recordKeyHit("offensiveMelee");
            e.consume();
            final Rs2PrayerEnum prayer = config.offensiveMeleePrayer().getPrayer();
            dispatch("toggle prayer " + prayer.getName(), () -> Rs2Prayer.toggle(prayer));
        }

        if (config.offensiveRangeKey().matches(e)) {
            recordKeyHit("offensiveRange");
            e.consume();
            final Rs2PrayerEnum prayer = config.offensiveRangePrayer().getPrayer();
            dispatch("toggle prayer " + prayer.getName(), () -> Rs2Prayer.toggle(prayer));
        }

        if (config.offensiveMagicKey().matches(e)) {
            recordKeyHit("offensiveMagic");
            e.consume();
            final Rs2PrayerEnum prayer = config.offensiveMagicPrayer().getPrayer();
            dispatch("toggle prayer " + prayer.getName(), () -> Rs2Prayer.toggle(prayer));
        }

        if (config.specialAttackKey().matches(e)) {
            recordKeyHit("specialAttack");
            e.consume();
            dispatch("toggle spec", () -> Rs2Combat.setSpecState(!Rs2Combat.getSpecState()));
        }

        // ------------------------------------------------------------------
        // DEFENSIVE / PROTECTION PRAYERS
        // ------------------------------------------------------------------
        if (config.protectFromMagic().matches(e)) {
            recordKeyHit("protectMagic");
            e.consume();
            dispatch("toggle prayer PROTECT_MAGIC", () -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC));
        }

        if (config.protectFromMissles().matches(e)) {
            recordKeyHit("protectRange");
            e.consume();
            dispatch("toggle prayer PROTECT_RANGE", () -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE));
        }

        if (config.protectFromMelee().matches(e)) {
            recordKeyHit("protectMelee");
            e.consume();
            dispatch("toggle prayer PROTECT_MELEE", () -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE));
        }

        // ------------------------------------------------------------------
        // FOOD & POTIONS
        // ------------------------------------------------------------------
        if (config.eatBestFood().matches(e)) {
            recordKeyHit("eatBestFood");
            e.consume();
            dispatch("eat best food", Rs2Player::useFood);
        }

        if (config.eatFastFood().matches(e)) {
            recordKeyHit("eatFastFood");
            e.consume();
            dispatch("eat fast food", Rs2Player::useFastFood);
        }

        if (config.drinkPrayerPotion().matches(e)) {
            recordKeyHit("drinkPrayerPotion");
            e.consume();
            dispatch("drink prayer potion", Rs2Player::drinkPrayerPotion);
        }

        // ------------------------------------------------------------------
        // GEAR SWAPS
        // ------------------------------------------------------------------
        if (config.gear1().matches(e)) {
            recordKeyHit("gear1");
            e.consume();
            final String list = config.gearList1();
            dispatch("equip gear 1", () -> equipGear(list));
        }

        if (config.gear2().matches(e)) {
            recordKeyHit("gear2");
            e.consume();
            final String list = config.gearList2();
            dispatch("equip gear 2", () -> equipGear(list));
        }

        if (config.gear3().matches(e)) {
            recordKeyHit("gear3");
            e.consume();
            final String list = config.gearList3();
            dispatch("equip gear 3", () -> equipGear(list));
        }

        if (config.gear4().matches(e)) {
            recordKeyHit("gear4");
            e.consume();
            final String list = config.gearList4();
            dispatch("equip gear 4", () -> equipGear(list));
        }

        if (config.gear5().matches(e)) {
            recordKeyHit("gear5");
            e.consume();
            final String list = config.gearList5();
            dispatch("equip gear 5", () -> equipGear(list));
        }

        // ------------------------------------------------------------------
        // ALCHEMY
        // ------------------------------------------------------------------
        if (config.highAlchemyKey().matches(e)) {
            recordKeyHit("highAlchemy");
            e.consume();
            final String item = config.itemToAlch();
            dispatch("high alch " + item, () -> Rs2Magic.alch(item, 50, 75));
        }
    }

    private void equipGear(String gearListConfig) {
        if (gearListConfig == null || gearListConfig.isBlank()) {
            log.warn("[CombatHotkeys] equipGear called with empty/null gear list");
            return;
        }
        String[] itemIDs = gearListConfig.split(",");
        for (String value : itemIDs) {
            value = value.trim();
            if (value.isEmpty()) continue;
            try {
                int itemId = Integer.parseInt(value);
                log.debug("[CombatHotkeys] Equipping item id={}", itemId);
                Rs2Inventory.equip(itemId);
                int delay = Rs2Random.between(0, config.maxDelay());
                sleep(delay);
            } catch (NumberFormatException ex) {
                log.error("[CombatHotkeys] Invalid item ID in gear list: '{}'", value);
                lastError.set("bad gear ID: " + value);
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    // -------------------------------------------------------------------------
    // MENU ENTRY EVENTS (dance tile marking)
    // -------------------------------------------------------------------------

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (event.getOption().equals("Walk here") && config.yesDance())
        {
            Microbot.getClient().getMenu().createMenuEntry(-1)
                    .setOption("Dancing -> mark tile 2")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        final var target = Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile();
                        if (target != null)
                        {
                            final var location = target.getWorldLocation();
                            Microbot.getConfigManager().setConfiguration(
                                    "combathotkeys",
                                    "tile2",
                                    location
                            );
                        }
                    });

            Microbot.getClient().getMenu().createMenuEntry(-1)
                    .setOption("Dancing -> mark tile 1")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        final var target = Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile();
                        if (target != null)
                        {
                            final var location = target.getWorldLocation();
                            Microbot.getConfigManager().setConfiguration(
                                    "combathotkeys",
                                    "tile1",
                                    location
                            );
                        }
                    });
        }
    }
}
