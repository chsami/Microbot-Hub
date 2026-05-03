package net.runelite.client.plugins.microbot.qualityoflife;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.globval.WidgetIndices;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WintertodtActions;
import net.runelite.client.plugins.microbot.qualityoflife.managers.CraftingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.FiremakingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.FletchingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.GemCuttingManager;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.*;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.bank.BankpinScript;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pvp.PvpScript;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtOverlay;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtScript;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.reflect.FieldUtils;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtScript.isInWintertodtRegion;
import static net.runelite.client.plugins.microbot.util.Global.awaitExecutionUntil;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "QoL",
        description = "Quality of Life Plugin",
        tags = {"QoL", "microbot"},
        version = QoLPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL

)
@Slf4j
public class QoLPlugin extends Plugin implements KeyListener {
    public static final String version = "1.8.13";
    public static final List<NewMenuEntry> bankMenuEntries = new LinkedList<>();
    public static final List<NewMenuEntry> furnaceMenuEntries = new LinkedList<>();
    public static final List<NewMenuEntry> anvilMenuEntries = new LinkedList<>();
    private static final AtomicBoolean uiUpdateQueued = new AtomicBoolean(false);
    private static final AtomicBoolean uiRestoreQueued = new AtomicBoolean(false);
    private static final AtomicBoolean uiQueuedDuringSplash = new AtomicBoolean(false);
    private static final AtomicBoolean uiUpdatePendingAfterRestore = new AtomicBoolean(false);

    private static final AtomicBoolean loggedMissingSwitcherOn = new AtomicBoolean(false);
    private static final AtomicBoolean loggedMissingSwitcherOff = new AtomicBoolean(false);
    private static final AtomicBoolean loggedThemeException = new AtomicBoolean(false);
    private static final AtomicBoolean loggedRestoreException = new AtomicBoolean(false);
    private static final AtomicBoolean loggedToggleReflectionException = new AtomicBoolean(false);
    private static final AtomicBoolean loggedMissingUiManagerKey = new AtomicBoolean(false);
    private static final AtomicBoolean loggedMicrobotPluginCacheFailure = new AtomicBoolean(false);
    private static final AtomicBoolean loggedMicrobotPluginFallbackScan = new AtomicBoolean(false);

    private static final AtomicReference<QoLPlugin> selfRef = new AtomicReference<>();
    private static final AtomicReference<MicrobotPlugin> microbotPluginRef = new AtomicReference<>();

    // Use explicit synchronization everywhere (avoid mixing synchronizedMap + synchronized blocks).
    private static final Map<JToggleButton, IconState> originalToggleIcons = new WeakHashMap<>();
    private static final Map<JLabel, Color> originalLabelColors = new WeakHashMap<>();
    private static final Set<JLabel> touchedLabels = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<String, Object> originalUiManagerValues = new HashMap<>();
    private static final String[] UI_KEYS_TO_PATCH = new String[]{
            "Component.accentColor",
            "ProgressBar.selectionForeground",
            "ProgressBar.selectionBackground",
            "Button.default.focusColor"
    };
    private static final int HALF_ROTATION = 1024;
    private static final int FULL_ROTATION = 2048;
    private static final int PITCH_INDEX = 0;
    private static final int YAW_INDEX = 1;
    private static final BufferedImage SWITCHER_ON_IMG = getImageFromConfigResource("switcher_on");
    private static final BufferedImage SWITCHER_OFF_IMG = getImageFromConfigResource("switcher_off");
    private static final BufferedImage STAR_ON_IMG = getImageFromConfigResource("star_on");
    private static volatile Color lastAccentApplied = null;
    private static volatile Color lastToggleColorApplied = null;
    private static volatile ImageIcon lastToggleOnIcon = null;
    private static volatile Window lastWindowPatched = null;
    public static InventorySetup loadoutToLoad = null;
    private static GameState lastGameState = GameState.UNKNOWN;
    private final int[] deltaCamera = new int[3];
    private final int[] previousCamera = new int[3];

    public static NewMenuEntry workbenchMenuEntry;
    public static boolean recordActions = false;
    public static boolean executeBankActions = false;
    public static boolean executeFurnaceActions = false;
    public static boolean executeAnvilActions = false;
    public static boolean executeWorkbenchActions = false;
    public static boolean executeLoadoutActions = false;
    private final String BANK_OPTION = "Bank";
    private final String SMELT_OPTION = "Smelt";
    private final String SMITH_OPTION = "Smith";
    @Inject
    public ConfigManager configManager;
    @Inject
    WintertodtScript wintertodtScript;
    @Inject
    private QoLConfig config;
    @Inject
    private QoLScript qoLScript;
    @Inject
    private AutoRunScript autoRunScript;
    @Inject
    private SpecialAttackScript specialAttackScript;
    @Inject
    private AutoItemDropperScript autoItemDropperScript;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private QoLOverlay qoLOverlay;
    @Inject
    private WintertodtOverlay wintertodtOverlay;
    @Inject
    private QolCannonScript cannonScript;
    @Inject
    @Getter
    private PvpScript pvpScript;
    @Inject
    FletchingManager fletchingManager;
    @Inject
    FiremakingManager firemakingManager;
    @Inject
    GemCuttingManager gemCuttingManager;
    @Inject
    CraftingManager craftingManager;
    @Inject
    EventBus eventBus;

    @Inject
    BankpinScript bankpinScript;
    @Inject
    private PotionManagerScript potionManagerScript;
    @Inject
    private AutoPrayer autoPrayer;

    @Inject
    private KeyManager keyManager;

    @Provides
    QoLConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(QoLConfig.class);
    }

    private static BufferedImage getImageFromConfigResource(String imgName) {
        try {
            Class<?> clazz = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
            return ImageUtil.loadImageResource(clazz, imgName.concat(".png"));
        } catch (Exception e) {
            log.debug("QoL: failed to load ConfigPanel image {}", imgName, e);
            return null;
        }
    }

    private static ImageIcon remapImage(BufferedImage image, Color color) {
        if (image == null || color == null) {
            return null;
        }
        BufferedImage img = new BufferedImage(image.getWidth(), image.getHeight(), 2);
        Graphics2D graphics = img.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.setColor(color);
        graphics.setComposite(AlphaComposite.getInstance(10, 1));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return new ImageIcon(img);
    }

    private static MicrobotPlugin findMicrobotPluginOnce()
    {
        MicrobotPlugin cached = microbotPluginRef.get();
        if (cached != null)
        {
            return cached;
        }

        MicrobotPlugin found = (MicrobotPlugin) Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> plugin instanceof MicrobotPlugin)
                .findAny().orElse(null);
        if (found != null)
        {
            microbotPluginRef.compareAndSet(null, found);
        }
        return found;
    }

    @Override
    protected void startUp() throws AWTException {
        selfRef.set(this);
        // Cache MicrobotPlugin once (avoid scanning plugin list in steady-state UI updates).
        try
        {
            findMicrobotPluginOnce();
        }
        catch (Exception ex)
        {
            // Best-effort cache; updateUiElements() can still fall back if needed.
            if (loggedMicrobotPluginCacheFailure.compareAndSet(false, true))
            {
                log.debug("QoL: failed to cache MicrobotPlugin during startup", ex);
            }
        }
        if (overlayManager != null) {
            overlayManager.add(qoLOverlay);
            overlayManager.add(wintertodtOverlay);
        }
        if (config.useSpecWeapon()) {
            Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
            Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
            Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
        }
        if (config.autoRun()) {
            Microbot.enableAutoRunOn = true;
        }
        if (config.autoStamina()) {
            Microbot.useStaminaPotsIfNeeded = true;
            Microbot.runEnergyThreshold = config.staminaThreshold() * 100;
        }
        autoRunScript.run(config);
        specialAttackScript.run(config);
        qoLScript.run(config);
        wintertodtScript.run(config);
        cannonScript.run(config);
        autoItemDropperScript.run(config);
        eventBus.register(fletchingManager);
        eventBus.register(firemakingManager);
        eventBus.register(gemCuttingManager);
        eventBus.register(craftingManager);
        bankpinScript.run(config);
        potionManagerScript.run(config);
        autoPrayer.run(config);
        keyManager.registerKeyListener(this);
        // pvpScript.run(config);
        uiQueuedDuringSplash.set(false);
        awaitExecutionUntil(() -> queueUpdateUiElementsDuringSplash(), () -> !SplashScreen.isOpen(), 600);
        // Splash closed; allow future splash-guarded queues.
        uiQueuedDuringSplash.set(false);
    }

    @Override
    protected void shutDown() {
        qoLScript.shutdown();
        autoRunScript.shutdown();
        specialAttackScript.shutdown();
        cannonScript.shutdown();
        autoItemDropperScript.shutdown();
        overlayManager.remove(qoLOverlay);
        overlayManager.remove(wintertodtOverlay);
        eventBus.unregister(fletchingManager);
        eventBus.unregister(firemakingManager);
        eventBus.unregister(gemCuttingManager);
        eventBus.unregister(craftingManager);
        potionManagerScript.shutdown();
        autoPrayer.shutdown();
        keyManager.unregisterKeyListener(this);

        // Best-effort restore of any UI theming we applied.
        try
        {
            if (SwingUtilities.isEventDispatchThread())
            {
                restoreUiElements();
            }
            else
            {
                FutureTask<Void> restoreTask = new FutureTask<>(() ->
                {
                    restoreUiElements();
                    return null;
                });
                SwingUtilities.invokeLater(restoreTask);
                try
                {
                    restoreTask.get(750, TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException ignored)
                {
                    log.warn("QoL: UI restore timed out during shutdown; continuing teardown.");
                    // Restore UIManager defaults immediately to reduce lingering accent.
                    if (SwingUtilities.isEventDispatchThread())
                    {
                        restoreOriginalUiDefaultsOnly();
                    }
                    else
                    {
                        SwingUtilities.invokeLater(QoLPlugin::restoreOriginalUiDefaultsOnly);
                    }
                    // Best-effort restore is already queued via `restoreTask`.
                }
            }
        }
        catch (Exception ex)
        {
            // shutdown path: avoid throwing; log once and continue teardown
            if (loggedRestoreException.compareAndSet(false, true))
            {
                log.warn("QoL: UI restore during shutdown failed", ex);
            }
        }
        finally
        {
            // Always clear the ref so queued updates don't run post-shutdown.
            selfRef.compareAndSet(this, null);
            microbotPluginRef.set(null);
        }
    }

    @Subscribe(
            priority = -999
    )
    public void onProfileChanged(ProfileChanged event) {
        log.info("Profile changed");
        log.info("Updating UI elements");
        // Wait for the splash screen to close before updating the UI elements
        uiQueuedDuringSplash.set(false);
        awaitExecutionUntil(() -> queueUpdateUiElementsDuringSplash(), () -> !SplashScreen.isOpen(), 1000);
        uiQueuedDuringSplash.set(false);

    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        ChatMessageType chatMessageType = chatMessage.getType();

        if (!Microbot.isLoggedIn()) return;
        if (isInWintertodtRegion()
                && (chatMessageType == ChatMessageType.GAMEMESSAGE || chatMessageType == ChatMessageType.SPAM)) {
            wintertodtScript.onChatMessage(chatMessage);
        }


    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (!Microbot.isLoggedIn()) return;
        if (config.neverLogout()) {
            NeverLogoutScript.onGameTick(event);
        }
        if (config.useSpecWeapon()) {
            if (Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() != config.specWeapon()) {
                Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
                Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
                Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.UNKNOWN && lastGameState == GameState.UNKNOWN) {
            queueUpdateUiElements();
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            resetMenuEntries();
        }

        if (event.getGameState() == GameState.LOGGED_IN) {
            if (config.fixCameraPitch())
                CameraScript.fixPitch();
            if (config.fixCameraZoom())
                CameraScript.fixZoom();
        }
        lastGameState = event.getGameState();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if ((config.resumeFletchingKindling() || config.resumeFeedingBrazier())) {
            if (event.getActor() == Microbot.getClient().getLocalPlayer()) {
                if (config.wintertodtActions() != WintertodtActions.NONE) {
                    updateWintertodtInterupted(true);
                }

            }
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        MenuEntry menuEntry = event.getMenuEntry();
        if (recordActions) {
            if (Rs2Bank.isOpen() && config.useDoLastBank()) {
                handleMenuOptionClicked(menuEntry, bankMenuEntries, "Close");
            } else if ((Rs2Widget.isProductionWidgetOpen() || Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen()) && config.useDoLastFurnace()) {
                handleMenuOptionClicked(menuEntry, furnaceMenuEntries, "Make", "Smelt", "Craft");
            } else if (Rs2Widget.isSmithingWidgetOpen() && config.useDoLastAnvil()) {
                handleMenuOptionClicked(menuEntry, anvilMenuEntries, "Smith");
            }
        }

        if ("Track".equals(event.getMenuOption())) {
            event.consume();
        }
        if (event.getMenuOption().contains("HA Profit")) {
            event.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                customHaProfitOnClicked(menuEntry);

                return null;
            });
        }

        if ((config.resumeFletchingKindling() || config.resumeFeedingBrazier()) && isInWintertodtRegion()) {
            if (event.getMenuOption().contains("Fletch") && event.getMenuTarget().isEmpty() && config.resumeFletchingKindling()) {
                WintertodtActions action = WintertodtActions.FLETCH;
                action.setMenuEntry(createCachedMenuEntry(menuEntry));
                updateLastWinthertodtAction(action);
                updateWintertodtInterupted(false);
                Microbot.log("Setting action to Fletch Kindle");
            }
            if (event.getMenuOption().contains("Feed") && config.resumeFeedingBrazier()) {
                WintertodtActions action = WintertodtActions.FEED;
                action.setMenuEntry(createCachedMenuEntry(menuEntry));
                updateLastWinthertodtAction(action);
                updateWintertodtInterupted(false);
            }
            if (event.getMenuOption().contains("Chop") || event.getMenuOption().contains("Walk")) {
                updateLastWinthertodtAction(WintertodtActions.NONE);
                updateWintertodtInterupted(false);
            }

        }
        if (config.smartWorkbench() && event.getMenuOption().contains("Smart Work-at") && event.getMenuEntry().getIdentifier() == ObjectID.WORKBENCH_43754) {
            if (Rs2Inventory.anyPouchEmpty() && Rs2Inventory.hasItem(ItemID.GUARDIAN_ESSENCE)) {
                event.consume();
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.fillPouches();
                    Microbot.getRs2TileObjectCache().query().interact(ObjectID.WORKBENCH_43754);
                    return null;
                });

            }
        }
        if (config.smartGotrMine() && event.getMenuOption().contains("Smart Mine") && event.getMenuEntry().getIdentifier() == ObjectID.HUGE_GUARDIAN_REMAINS) {
            if (Rs2Inventory.anyPouchEmpty() && Rs2Inventory.hasItem(ItemID.GUARDIAN_ESSENCE)) {
                event.consume();
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.fillPouches();
                    Microbot.getRs2TileObjectCache().query().interact(ObjectID.HUGE_GUARDIAN_REMAINS);
                    return null;
                });

            }
        }
        if (config.smartRunecraft() && event.getMenuOption().contains("Smart Craft-rune") && event.getMenuTarget().contains("Altar")) {
            if (Rs2Inventory.anyPouchFull()) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.waitForInventoryChanges(50000);
                    Global.sleepUntil(() -> !Rs2Inventory.anyPouchFull(), () -> {
                                Rs2Inventory.emptyPouches();
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query().withName("Altar").interact());
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            , 10000, 200);
                    return null;
                });

            }
        }
    }

    private void handleMenuOptionClicked(MenuEntry menuEntry, List<NewMenuEntry> menuEntriesList, String... stopOptions) {
        NewMenuEntry cachedMenuEntry = createCachedMenuEntry(menuEntry);
        menuEntriesList.add(cachedMenuEntry);
        if (Arrays.stream(stopOptions).anyMatch(menuEntry.getOption()::contains)) {
            recordActions = false;
            Microbot.log("<col=5F1515>Stopped recording actions</col>");
        }
    }

    private NewMenuEntry createCachedMenuEntry(MenuEntry menuEntry) {
        NewMenuEntry cachedMenuEntry = new NewMenuEntry(
                menuEntry.getOption(),
                menuEntry.getTarget(),
                menuEntry.getIdentifier(),
                menuEntry.getType(),
                menuEntry.getParam0(),
                menuEntry.getParam1(),
                menuEntry.isForceLeftClick()
        );
        cachedMenuEntry.setItemId(menuEntry.getItemId());
        cachedMenuEntry.setWidget(menuEntry.getWidget());
        return cachedMenuEntry;
    }

    @Subscribe
    private void onNpcChanged(NpcChanged event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcChanged(event);
        }
    }

    @Subscribe
    private void onNpcDespawned(NpcDespawned event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcDespawned(event);
        }
    }

    @Subscribe
    private void onNpcSpawned(NpcSpawned event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcSpawned(event);
        }
    }


    // TODO: Rework this method to reduce the size and make it more manageable
    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        String option = event.getOption();
        String target = event.getTarget();
        MenuEntry menuEntry = event.getMenuEntry();
        boolean bankChestCheck = "Bank".equals(option) || ("Use".equals(option) && target.toLowerCase().contains("bank chest"));

        if (config.quickHighAlch() && menuEntry.getItemId() != -1 && !menuEntry.getTarget().isEmpty() && menuEntry.getParam1() == WidgetIndices.ResizableModernViewport.INVENTORY_CONTAINER && menuEntry.getType() != MenuAction.WIDGET_TARGET_ON_WIDGET) {
            Rs2ItemModel item = Rs2Inventory.getItemInSlot(menuEntry.getParam0());
            if (item != null) {
                if (item.isHaProfitable()) {
                    event.getMenuEntry().setOption("<col=FFA500>HA Profit</col>");
                }
            }


        }

        if (config.rightClickCameraTracking() && menuEntry.getNpc() != null && menuEntry.getNpc().getId() > 0) {
            addMenuEntry(event, "Track", target, this::customTrackOnClicked);
        }
        if (config.useDoLastCooking() && "Cook".equals(option) && (target.contains("Range") || target.contains("Fire"))) {
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customCookingOnClicked);
        }

        if (config.useDoLastBank()) {
            if ("Talk-to".equals(option)) {
                for (MenuEntry e : Microbot.getClient().getMenuEntries()) {
                    if ("Bank".equals(e.getOption()) && e.getTarget().equals(target)) {
                        menuEntry.setDeprioritized(true);
                        break;
                    }
                }
            }
            if (bankChestCheck && event.getItemId() == -1) {
                menuEntry.onClick(this::recordNewActions);
                addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customBankingOnClicked);
            }
        }

        if (config.useDoLastFurnace() && "Smelt".equals(option) && target.contains("Furnace")) {
            menuEntry.onClick(this::recordNewActions);
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customFurnaceOnClicked);
        }

        if (config.useDoLastAnvil() && "Smith".equals(option) && target.contains("Anvil")) {
            menuEntry.onClick(this::recordNewActions);
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customAnvilOnClicked);
        }

        if (config.useQuickTeleportToHouse() && menuEntry.getOption().contains("Open") && menuEntry.getTarget().toLowerCase().contains("rune pouch")) {
            if (Rs2Magic.isSpellbook(Rs2Spellbook.MODERN)) {
                addMenuEntry(event, "<col=FFA500>Teleport to House</col>", target, this::quickTeleportToHouse);
            }
        }

        if (config.smartWorkbench() && menuEntry.getOption().contains("Work-at") && menuEntry.getIdentifier() == ObjectID.WORKBENCH_43754) {
            menuEntry.setOption("<col=FFA500>Smart Work-at</col>");
        }
        if (config.smartGotrMine() && menuEntry.getOption().contains("Mine") && menuEntry.getIdentifier() == ObjectID.HUGE_GUARDIAN_REMAINS) {
            menuEntry.setOption("<col=FFA500>Smart Mine</col>");
        }
        if (config.smartRunecraft() && menuEntry.getOption().contains("Craft-rune") && menuEntry.getTarget().contains("Altar")) {
            menuEntry.setOption("<col=FFA500>Smart Craft-rune</col>");
        }

        if (config.displayInventorySetups() && bankChestCheck && event.getItemId() == -1) {
            addLoadoutMenuEntries(event, target);
        }
    }

    private void customHaProfitOnClicked(MenuEntry entry) {
        NewMenuEntry highAlch = new NewMenuEntry("Cast", "High Alch", 0, MenuAction.WIDGET_TARGET, -1, 14286892, false);
        NewMenuEntry highAlchItem = new NewMenuEntry("Cast", "High Alch", 0, MenuAction.WIDGET_TARGET_ON_WIDGET, entry.getParam0(), WidgetIndices.ResizableModernViewport.INVENTORY_CONTAINER, false);
        highAlchItem.setItemId(entry.getItemId());
        Rs2Tab.switchToMagicTab();
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), highAlch);
        Global.sleep(Rs2Random.randomGaussian(150, 50));
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), highAlchItem);
        Rs2Player.waitForXpDrop(Skill.MAGIC, 1000);
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

    }

    private void addLoadoutMenuEntries(MenuEntryAdded event, String target) {
        if (config.displaySetup1() && config.Setup1() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup1().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup1()));
        }
        if (config.displaySetup2() && config.Setup2() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup2().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup2()));
        }
        if (config.displaySetup3() && config.Setup3() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup3().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup3()));
        }
        if (config.displaySetup4() && config.Setup4() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup4().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup4()));
        }
    }


    @Subscribe
    public void onConfigChanged(ConfigChanged ev) {
        if ("smoothRotation".equals(ev.getKey()) && config.smoothCameraTracking() && Microbot.isLoggedIn()) {
            previousCamera[YAW_INDEX] = Microbot.getClient().getMapAngle();
        }
        if (ev.getKey().equals("accentColor") || ev.getKey().equals("toggleButtonColor") || ev.getKey().equals("pluginLabelColor")) {
            updateUiElements();
        }
        if (ev.getKey().equals("resumeFletchingKindling")) {
            if (config.resumeFletchingKindling()) {
                configManager.setConfiguration("QoL", "quickFletchKindling", true);
            }
        }
        if (ev.getKey().equals("useSpecWeapon") || ev.getKey().equals("specWeapon")) {
            if (config.useSpecWeapon()) {
                Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
                Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
                Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
            } else {
                Microbot.getSpecialAttackConfigs().reset();
            }
        }

        if (ev.getKey().equals("autoRun")) {
            Microbot.enableAutoRunOn = config.autoRun();
        }

        if (ev.getKey().equals("autoStamina")) {
            Microbot.useStaminaPotsIfNeeded = config.autoStamina();
        }

        if (ev.getKey().equals("staminaThreshold")) {
            Microbot.runEnergyThreshold = config.staminaThreshold() * 100;
        }
    }

    @Subscribe
    public void onBeforeRender(BeforeRender render) {
        if (!Microbot.isLoggedIn() || !config.smoothCameraTracking()) {
            return;
        }
        applySmoothingToAngle(YAW_INDEX);
    }

    // TODO: These OnClick methods should be moved to a separate class to reduce the size and make this class more manageable

    private void customLoadoutOnClicked(MenuEntry event, InventorySetup loadout) {
        recordActions = false;
        loadoutToLoad = loadout;
        executeLoadoutActions = true;
    }

    private void customBankingOnClicked(MenuEntry event) {
        recordActions = false;
        if (bankMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Banking</col>");
        executeBankActions = true;
    }

    private void customTrackOnClicked(MenuEntry event) {
        if (Rs2Camera.isTrackingNpc()) {
            Rs2Camera.stopTrackingNpc();
            Microbot.log("<col=5F1515>Stopped tracking old NPC, try again to track new NPC</col>");
            return;
        }
        Rs2Camera.trackNpc(Objects.requireNonNull(event.getNpc()).getId());
    }

    private void customFurnaceOnClicked(MenuEntry event) {
        recordActions = false;
        if (furnaceMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Furnace</col>");
        executeFurnaceActions = true;
    }

    private void customAnvilOnClicked(MenuEntry event) {
        recordActions = false;
        if (anvilMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Anvil</col>");
        executeAnvilActions = true;
    }

    private void customWorkbenchOnClicked(MenuEntry event) {
        Microbot.log("<col=245C2D>Workbench</col>");

    }

    private void recordNewActions(MenuEntry event) {
        recordActions = true;
        String option = event.getOption();
        if (BANK_OPTION.equals(option) || "Use".equals(option) && event.getTarget().toLowerCase().contains("bank chest")) {
            bankMenuEntries.clear();
        } else if (SMELT_OPTION.equals(option)) {
            furnaceMenuEntries.clear();
        } else if (SMITH_OPTION.equals(option)) {
            anvilMenuEntries.clear();
        }
        Microbot.log("<col=245C2D>Recording actions for: </col>" + option);
    }

    private void customCookingOnClicked(MenuEntry event) {
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Global.sleepUntilTrue(Rs2Widget::isProductionWidgetOpen);
            if (Rs2Widget.isProductionWidgetOpen()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
            Microbot.log("<col=245C2D>Cooking</col>");

            return null;
        });

    }

    private void quickTeleportToHouse(MenuEntry entry) {
        if (!Rs2Inventory.hasRunePouch()) return;

        if (!Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE)) {
            Microbot.log("<col=5F1515>Missing Required Runes</col>");
            return;
        }

        Microbot.log("<col=245C2D>Casting: </col>Teleport to House");
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
            return null;
        });
    }


    private void applySmoothingToAngle(int index) {
        int currentAngle = index == YAW_INDEX ? Microbot.getClient().getMapAngle() : 0;
        int newDeltaAngle = getSmallestAngle(previousCamera[index], currentAngle);
        deltaCamera[index] += newDeltaAngle;

        int deltaChange = lerp(deltaCamera[index], 0, 0.8f);
        int changed = previousCamera[index] + deltaChange;

        deltaCamera[index] -= deltaChange;
        if (index == YAW_INDEX) {
            Microbot.getClient().setCameraYawTarget(changed);
        }
        previousCamera[index] += deltaChange;
    }

    private int lerp(int x, int y, double alpha) {
        return x + (int) Math.round((y - x) * alpha);
    }

    private int getSmallestAngle(int x, int y) {
        return ((y - x + HALF_ROTATION) % FULL_ROTATION) - HALF_ROTATION;
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, Consumer<MenuEntry> callback) {
        int index = Microbot.getClient().getMenuEntries().length;
        Microbot.getClient().createMenuEntry(index)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(event.getMenuEntry().getType())
                .onClick(callback);
    }

    private void addLoadoutMenuEntry(MenuEntryAdded event, String option, String target, Consumer<MenuEntry> callback) {
        int index = Microbot.getClient().getMenuEntries().length - 1;
        Microbot.getClient().createMenuEntry(index)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(event.getMenuEntry().getType())
                .onClick(callback);
    }

    public void updateLastWinthertodtAction(WintertodtActions action) {
        configManager.setConfiguration("QoL", "wintertodtActions", action);
    }

    public void updateWintertodtInterupted(boolean interupted) {
        configManager.setConfiguration("QoL", "interrupted", interupted);
    }


    /**
     * Updates the UI elements by modifying various fields and components based on the provided configuration.
     * <p>
     * This method updates the accent color, the plugin toggle button's ON_SWITCHER field, and modifies the color of labels
     * and toggle buttons for plugins in the plugin list. If any part of the process fails (e.g., the ConfigPlugin is not found),
     * it logs an appropriate error message and returns false.
     *
     * @return true if the UI elements are successfully updated, false otherwise.
     */
    private boolean updateUiElements() {
        // Swing/FlatLaf layout is not thread-safe; enforce EDT execution.
        if (!SwingUtilities.isEventDispatchThread()) {
            QoLPlugin self = selfRef.get();
            if (self == null)
            {
                return false;
            }
            // If a run is already queued and we still have a live plugin, consider it pending.
            if (uiUpdateQueued.get())
            {
                return true;
            }
            return queueUpdateUiElements();
        }

        try {
            // Find the ConfigPlugin instance from the plugin manager
            MicrobotPlugin microbotPlugin = findMicrobotPluginOnce();
            if (microbotPlugin == null && loggedMicrobotPluginFallbackScan.compareAndSet(false, true))
            {
                log.debug("QoL: MicrobotPlugin ref was empty; fell back to plugin manager scan.");
            }

            // If ConfigPlugin is not found, log an error and return false
            if (microbotPlugin == null) {
                Microbot.log("Config Plugin not found");
                return false;
            }
            microbotPluginRef.set(microbotPlugin);

            // Get the plugin list panel from the ConfigPlugin instance
            JPanel pluginListPanel = getPluginListPanel(microbotPlugin);
            Window pluginWindow = SwingUtilities.getWindowAncestor(pluginListPanel);
            if (pluginWindow != lastWindowPatched)
            {
                // If the config window closed while we had an accent applied, restore defaults now.
                if (pluginWindow == null && lastAccentApplied != null)
                {
                    restoreOriginalUiDefaultsOnly();
                    lastAccentApplied = null;
                }
                lastWindowPatched = pluginWindow;
                synchronized (originalToggleIcons)
                {
                    originalToggleIcons.clear();
                }
                synchronized (originalLabelColors)
                {
                    originalLabelColors.clear();
                }
                synchronized (touchedLabels)
                {
                    touchedLabels.clear();
                }
                lastAccentApplied = null;
                lastToggleColorApplied = null;
                lastToggleOnIcon = null;
            }

            // Best-effort accent color behavior (no static-final mutation / no Unsafe).
            try
            {
                Color accent = config.accentColor();
                if (accent != null)
                {
                    ColorUIResource accentRes = new ColorUIResource(accent);
                    rememberOriginalUiDefaults();
                    // Only apply+refresh when accent actually changed.
                    if (!accent.equals(lastAccentApplied))
                    {
                        UIManager.put("Component.accentColor", accentRes);
                        UIManager.put("ProgressBar.selectionForeground", accentRes);
                        UIManager.put("ProgressBar.selectionBackground", accentRes);
                        UIManager.put("Button.default.focusColor", accentRes);
                        lastAccentApplied = accent;

                        // Refresh UI tree to apply defaults.
                        try
                        {
                            if (pluginWindow != null)
                            {
                                SwingUtilities.updateComponentTreeUI(pluginWindow);
                                pluginWindow.invalidate();
                                pluginWindow.validate();
                                pluginWindow.repaint();
                            }
                        }
                        catch (Exception ex)
                        {
                            if (loggedThemeException.compareAndSet(false, true))
                            {
                                log.warn("QoL: UI refresh after accent update failed", ex);
                            }
                        }
                    }
                }
                else if (lastAccentApplied != null)
                {
                    // Accent cleared; restore original defaults (if we captured them) and refresh UI.
                    restoreOriginalUiDefaultsOnly();
                    lastAccentApplied = null;

                    try
                    {
                        if (pluginWindow != null)
                        {
                            SwingUtilities.updateComponentTreeUI(pluginWindow);
                            pluginWindow.invalidate();
                            pluginWindow.validate();
                            pluginWindow.repaint();
                        }
                    }
                    catch (Exception ex)
                    {
                        if (loggedThemeException.compareAndSet(false, true))
                        {
                            log.warn("QoL: UI refresh after accent restore failed", ex);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                if (loggedThemeException.compareAndSet(false, true))
                {
                    log.warn("QoL: UI theme update failed", ex);
                }
            }

            // Set the plugin list using the retrieved plugin list panel
            List<?> currentPluginList = getPluginList(pluginListPanel);

            // If the plugin list is still null, log an error and return false
            if (currentPluginList == null) {
                Microbot.log("Plugin list is null, waiting for it to be initialized");
                return false;
            }

            // Pass 1: capture originals (before applying any changes).
            for (Object plugin : currentPluginList)
            {
                try
                {
                    if (plugin instanceof JPanel)
                    {
                        for (Component component : ((JPanel) plugin).getComponents())
                        {
                            if (component instanceof JLabel)
                            {
                                JLabel label = (JLabel) component;
                                synchronized (originalLabelColors)
                                {
                                    originalLabelColors.computeIfAbsent(label, l -> l.getForeground());
                                }
                            }
                        }
                    }

                    JToggleButton onOffToggle = (JToggleButton) FieldUtils.readDeclaredField(plugin, "onOffToggle", true);
                    if (onOffToggle == null)
                    {
                        continue;
                    }
                    synchronized (originalToggleIcons)
                    {
                        originalToggleIcons.computeIfAbsent(onOffToggle, t -> new IconState(t.getIcon(), t.getSelectedIcon()));
                    }
                }
                catch (Exception ex)
                {
                    // Best-effort: one broken row shouldn't abort the whole update.
                    if (loggedToggleReflectionException.compareAndSet(false, true))
                    {
                        log.debug("QoL: reflection lookup for plugin toggle failed; UI theming may be partial.", ex);
                    }
                }
            }

            // Pass 2: apply theming changes.
            final Color labelColor = config.pluginLabelColor();
            final ImageIcon onIcon = getCachedToggleOnIcon(config.toggleButtonColor());
            for (Object plugin : currentPluginList) {
                // If the plugin is a JPanel, update the color of any JLabel components within it
                if (plugin instanceof JPanel) {
                    for (Component component : ((JPanel) plugin).getComponents()) {
                        if (component instanceof JLabel) {
                            JLabel label = (JLabel) component;
                            // Set the label color based on the config
                            if (labelColor != null)
                            {
                                if (!labelColor.equals(label.getForeground()))
                                {
                                    synchronized (touchedLabels)
                                    {
                                        touchedLabels.add(label);
                                    }
                                    label.setForeground(labelColor);
                                }
                            }
                        }
                    }
                }

                // Get the on/off toggle button for the plugin and update its selected icon
                JToggleButton onOffToggle;
                try
                {
                    onOffToggle = (JToggleButton) FieldUtils.readDeclaredField(plugin, "onOffToggle", true);
                    if (onOffToggle == null)
                    {
                        continue;
                    }
                }
                catch (Exception ex)
                {
                    continue;
                }
                // Only recolor the "ON" (selected) icon. Do not overwrite the "OFF" icon,
                // otherwise disabled plugins will also appear enabled.
                Icon offIcon = onOffToggle.getIcon();
                if (onIcon == null)
                {
                    if (loggedMissingSwitcherOn.compareAndSet(false, true))
                    {
                        log.warn("QoL: missing ConfigPanel switcher_on.png; leaving plugin toggle icons unchanged.");
                    }
                    continue;
                }
                onOffToggle.setSelectedIcon(onIcon);
                if (offIcon != null) {
                    onOffToggle.setIcon(offIcon);
                } else if (SWITCHER_OFF_IMG != null) {
                    // Fallback: ensure OFF icon is distinct if missing.
                    onOffToggle.setIcon(new ImageIcon(SWITCHER_OFF_IMG));
                } else {
                    if (loggedMissingSwitcherOff.compareAndSet(false, true))
                    {
                        log.warn("QoL: missing ConfigPanel switcher_off.png; OFF icon fallback unavailable.");
                    }
                }
            }

            return true;
        } catch (Exception e) {
            // Log any exceptions that occur during the UI update process
            String errorMessage = "QoL Error updating UI elements: " + e.getMessage();
            log.error(errorMessage, e);
            Microbot.log(errorMessage);
            return false;
        }
    }

    private static ImageIcon getCachedToggleOnIcon(Color toggleColor)
    {
        if (SWITCHER_ON_IMG == null)
        {
            return null;
        }

        if (toggleColor == null)
        {
            lastToggleColorApplied = null;
            lastToggleOnIcon = null;
            return null;
        }

        if (!toggleColor.equals(lastToggleColorApplied) || lastToggleOnIcon == null)
        {
            lastToggleOnIcon = remapImage(SWITCHER_ON_IMG, toggleColor);
            lastToggleColorApplied = toggleColor;
        }

        return lastToggleOnIcon;
    }

    private static final class IconState
    {
        private final Icon icon;
        private final Icon selectedIcon;

        private IconState(Icon icon, Icon selectedIcon)
        {
            this.icon = icon;
            this.selectedIcon = selectedIcon;
        }
    }

    private static void rememberOriginalUiDefaults()
    {
        synchronized (originalUiManagerValues)
        {
            if (!originalUiManagerValues.isEmpty())
            {
                return;
            }

            for (String k : UI_KEYS_TO_PATCH)
            {
                Object v = UIManager.get(k);
                originalUiManagerValues.put(k, v);
                if (v == null && !UIManager.getDefaults().containsKey(k) && loggedMissingUiManagerKey.compareAndSet(false, true))
                {
                    log.debug("QoL: UIManager key '{}' not present in defaults; accent patch may be FlatLaf-version dependent.", k);
                }
            }
        }
    }

    private static void restoreOriginalUiDefaultsOnly()
    {
        synchronized (originalUiManagerValues)
        {
            if (originalUiManagerValues.isEmpty())
            {
                return;
            }

            for (String k : UI_KEYS_TO_PATCH)
            {
                UIManager.put(k, originalUiManagerValues.get(k));
            }
        }
    }

    private static void queueRestoreUiElements()
    {
        if (!uiRestoreQueued.compareAndSet(false, true))
        {
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                restoreUiElements();
            }
            finally
            {
                uiRestoreQueued.set(false);
            }
        });
    }

    private static void restoreUiElements()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            log.warn("QoL: restoreUiElements called off-EDT; skipping.");
            return;
        }

        // Restore UIManager defaults.
        synchronized (originalUiManagerValues)
        {
            if (!originalUiManagerValues.isEmpty())
            {
                for (String k : UI_KEYS_TO_PATCH)
                {
                    UIManager.put(k, originalUiManagerValues.get(k));
                }
            }
        }

        // Restore per-component UI state (best-effort; components may be gone/rebuilt).
        synchronized (originalToggleIcons)
        {
            for (Map.Entry<JToggleButton, IconState> e : originalToggleIcons.entrySet())
            {
                JToggleButton t = e.getKey();
                IconState s = e.getValue();
                if (t != null && s != null)
                {
                    t.setIcon(s.icon);
                    t.setSelectedIcon(s.selectedIcon);
                }
            }
        }

        synchronized (originalLabelColors)
        {
            for (Map.Entry<JLabel, Color> e : originalLabelColors.entrySet())
            {
                JLabel l = e.getKey();
                boolean touched;
                synchronized (touchedLabels)
                {
                    touched = touchedLabels.contains(l);
                }
                if (l != null && touched)
                {
                    l.setForeground(e.getValue());
                }
            }
        }

        // Refresh UI tree to apply restored defaults.
        try
        {
            QoLPlugin self = selfRef.get();
            MicrobotPlugin microbotPlugin = microbotPluginRef.get();
            if (self != null && microbotPlugin != null)
            {
                JPanel pluginListPanel = self.getPluginListPanel(microbotPlugin);
                Window w = SwingUtilities.getWindowAncestor(pluginListPanel);
                if (w != null)
                {
                    SwingUtilities.updateComponentTreeUI(w);
                    w.invalidate();
                    w.validate();
                    w.repaint();
                }
            }
        }
        catch (Exception ex)
        {
            if (loggedRestoreException.compareAndSet(false, true))
            {
                log.warn("QoL: UI restore refresh failed", ex);
            }
        }

        // Clear caches so re-enable re-captures fresh state.
        synchronized (originalToggleIcons)
        {
            originalToggleIcons.clear();
        }
        synchronized (originalLabelColors)
        {
            originalLabelColors.clear();
        }
        synchronized (touchedLabels)
        {
            touchedLabels.clear();
        }
        // Intentionally keep captured defaults for plugin lifetime so we can restore later.
        lastAccentApplied = null;
        lastToggleColorApplied = null;
        lastToggleOnIcon = null;
        lastWindowPatched = null;

        // If an update was requested while restore was in-progress, run it now.
        if (uiUpdatePendingAfterRestore.compareAndSet(true, false))
        {
            queueUpdateUiElements();
        }
    }

    private static boolean queueUpdateUiElements()
    {
        // Don't interleave apply with restore.
        if (uiRestoreQueued.get())
        {
            uiUpdatePendingAfterRestore.set(true);
            return false;
        }

        // Prevent re-entrant scheduling during layout/validate cascades.
        if (!uiUpdateQueued.compareAndSet(false, true))
        {
            return selfRef.get() != null;
        }

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                QoLPlugin self = selfRef.get();
                if (self != null)
                {
                    self.updateUiElements();
                }
            }
            finally
            {
                uiUpdateQueued.set(false);
            }
        });

        return true;
    }

    private static boolean queueUpdateUiElementsDuringSplash()
    {
        // Prevent repeated enqueue spam while waiting for splash to close.
        if (!uiQueuedDuringSplash.compareAndSet(false, true))
        {
            return false;
        }
        return queueUpdateUiElements();
    }


    private JPanel getPluginListPanel(MicrobotPlugin microbotPlugin) throws ClassNotFoundException {

        Class<?> pluginListPanelClass = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel");
        if (microbotPlugin == null)
        {
            throw new IllegalStateException("MicrobotPlugin instance is null");
        }
        return (JPanel) microbotPlugin.getInjector().getProvider(pluginListPanelClass).get();
    }

    private List<?> getPluginList(JPanel pluginListPanel) throws IllegalAccessException {
        return (List<?>) FieldUtils.readDeclaredField(pluginListPanel, "pluginList", true);
    }

    public static void resetMenuEntries() {
        bankMenuEntries.clear();
        furnaceMenuEntries.clear();
        anvilMenuEntries.clear();
        recordActions = false;
        executeBankActions = false;
        executeFurnaceActions = false;
        executeAnvilActions = false;
        executeWorkbenchActions = false;
        executeLoadoutActions = false;
    }

    @Subscribe
    public void onPlayerChanged(PlayerChanged event) {
        if (config.aggressiveAntiPkMode() && autoPrayer.isFollowingPlayer(event.getPlayer())) {
            autoPrayer.handleAggressivePrayerOnGearChange(event.getPlayer(), config);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (config.grandExchangeHotkey().matches(e)) {
            e.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (config.grandExchangeHotkey().matches(e)) {
            e.consume();
            try {
                String clipboardText = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if (clipboardText != null && !clipboardText.isEmpty()) {
                    Microbot.getClientThread().invoke(() -> {
                        Microbot.getClient().setVarcStrValue(VarClientID.MESLAYERINPUT, clipboardText);
                        Microbot.getClient().runScript(ScriptID.GE_ITEM_SEARCH);
                    });
                }
            } catch (Exception ex) {
                Microbot.log("Failed to paste from clipboard: " + ex.getMessage());
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (config.grandExchangeHotkey().matches(e)) {
            e.consume();
        }
    }
}
