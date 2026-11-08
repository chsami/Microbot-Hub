package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.InventoryID;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

enum State {
    GOING_TO_GE,
    GETTING_COINS,
    MONITORING_COPILOT
}

public class FlipperScript extends Script {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlipperScript.class);
	/** Outside this package's logger on purpose: that one is held at WARN to keep the game chat quiet, and the few
	 *  diagnostic lines written here are meant for client.log, where they appear whatever that level is. */
	private static final org.slf4j.Logger diagnostics =
		org.slf4j.LoggerFactory.getLogger(FlipperPlugin.DIAGNOSTICS_LOGGER);
	private static final int DEFAULT_ACTION_COOLDOWN = 1200;
	private static final int ACTION_COOLDOWN_VARIANCE = 600;
	private static final int DEFAULT_INTERACTION_TIMEOUT = 33000;
	private static final int INTERACTION_TIMEOUT_VARIANCE = 11000;
	private static final int INVENTORY_WAIT_TIMEOUT = 5000;
	private static final int SCHEDULE_INTERVAL_MS = 600;
	// A closed exchange or a stray GE page used to stall this state machine silently.
	private static final int GE_CLOSED_RECOVER_MS = 4000;
	private static final int STRAY_PAGE_RECOVER_MS = 8000;
	private static final int KEY_PRESS_DELAY_MIN = 250;
	private static final int KEY_PRESS_DELAY_MAX = 400;

	private final WorldArea grandExchangeArea = new WorldArea(3136, 3465, 61, 54, 0);
    State state = State.GOING_TO_GE;

    private volatile Plugin flippingCopilot;
	private volatile Object suggestionManager;
    private volatile Object highlightController;
    private volatile long tradingGeneration;
    private long lastActionTime = 0;
    private long actionCooldown = DEFAULT_ACTION_COOLDOWN;
	private long interactionTimeout = DEFAULT_INTERACTION_TIMEOUT;
	private long offerScreenOpenTime = 0;
	private int offerScreenActionCount = 0;
	private long geClosedSince = 0;
	private long strayPageSince = 0;
    private final WaitingMouse waitingMouse = new WaitingMouse();
    private final CompletedOfferRecovery completedOfferRecovery = new CompletedOfferRecovery();
    private final CopilotFunds copilotFunds = new CopilotFunds();
    private final WaitingMousePresets waitingMousePresets = new WaitingMousePresets();
    private final GeflipperMouseMotion mouseMotion = new GeflipperMouseMotion();
    private final FinishSession finishSession = new FinishSession();
    /** True while the current finish session is a mule sell-down rather than the user's Finish. */
    private volatile boolean muleFinish;
    private volatile boolean receiverFinishRequested;
    private volatile boolean receiverFinishProfileInterrupted;
    private FlipperConfig.MuleMode observedMuleMode;
    private final MuleRuntime muleRuntime = new MuleRuntime(this::muleActionsAllowed, this::randomizeMouseSpeed,
        this::muleReceiverEnabled);
    private final MuleSession muleSession = new MuleSession(muleRuntime);
    private final MuleLedger muleLedger = new MuleLedger();
    private volatile java.util.function.Supplier<MuleLedger.Store> muleLedgerStores;
    {
        // Read-only taps: the receiver reports what it verified and the ledger only remembers it for display.
        muleSession.observeReceiver(new MuleSession.ReceiverObserver() {
            @Override public void received(long account, long gp) { muleLedger.recordReceived(account, gp); }
            @Override public void holdings(long account, long inventoryCoins, long bankCoins, boolean bankKnown) {
                muleLedger.observeHoldings(account, inventoryCoins, bankCoins, bankKnown);
            }
            @Override public void receivedForProfile(long profileId, long account, long gp) {
                muleLedger.recordReceivedForProfile(profileId, account, gp);
            }
            @Override public void holdingsForProfile(long profileId, long account, long inventoryCoins,
                                                       long bankCoins, boolean bankKnown) {
                muleLedger.observeHoldingsForProfile(profileId, account, inventoryCoins, bankCoins, bankKnown);
            }
        });
    }
    private volatile MuleConnectionMonitor muleConnection;
    private String lastMuleStatusLogged = "";
    private boolean muleWasActive;
    private long muleConnectionGeneration;
    private final SecondScreenAfk secondScreenAfk = new SecondScreenAfk(new SecondScreenAfk.Port() {
        @Override public boolean loggedIn() { return Microbot.isLoggedIn(); }
        @Override public int currentWorld() {
            return Boolean.TRUE.equals(Microbot.isLoggedIn())
                ? readUi(() -> Microbot.getClient() == null ? 0 : Microbot.getClient().getWorld()) : 0;
        }
        @Override public boolean exchangeOrBankOpen() {
            GeUiState ui = readGeUi();
            return ui.exchangeOpen || ui.bankOpen;
        }
        @Override public void closeInterfaces() {
            if (Rs2GrandExchange.isOpen()) {
                Rs2GrandExchange.closeExchange();
            } else if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
            }
        }
        @Override public void moveMouseOffScreen() {
            moveMouseOffScreenForAfk();
        }
        @Override public void logout() {
            if (!isRunning() || FlipperRunGuard.scriptsPaused() || InputArbiter.isHuman()) return;
            Rs2Player.logout();
        }
        @Override public void login(int world) {
            if (!isRunning() || FlipperRunGuard.scriptsPaused() || Microbot.isLoggedIn()
                || net.runelite.client.plugins.microbot.util.security.LoginManager.isLoginAttemptActive()) return;
            if (world > 0) {
                net.runelite.client.plugins.microbot.util.security.LoginManager.login(world);
            } else {
                net.runelite.client.plugins.microbot.util.security.LoginManager.login();
            }
        }
    });
    private volatile BooleanSupplier systemUpdateHandler;
    private volatile boolean systemUpdateHandled;
    private boolean systemUpdateRefusalLogged;
    private long lastSystemUpdateCheck;
    private long lastSystemUpdateSeen;
    private volatile int lastSystemUpdateSeconds = -1;
    /** Mule before an OSRS update: wait for Finish to list everything, then one transfer, then stop. */
    enum UpdateMule { NONE, AFTER_FINISH, RUNNING, DONE }
    private volatile UpdateMule updateMule = UpdateMule.NONE;
    /** A "Mule now" press waiting for the script thread to hand it to the Mule session; 0 = none. */
    private volatile int muleNowRequest;
    /** When the update is due, on the monotonic clock. */
    private long updateDueAt;
    /** No new transfer starts with less than this left before the update. */
    static final long UPDATE_MULE_MIN_START_MS = 90_000;
    /** A transfer still before its trade window stops this long before the update. */
    static final long UPDATE_MULE_MARGIN_MS = 30_000;
    // Overlay only, refreshed each sender tick.
    private volatile int displayKeepGp = -1;
    private volatile int displayTriggerGp = -1;
    private long unlistedWaitingSince;
    // Log-only bookkeeping: what Finish has been waiting for, since when, and when that was last logged.
    private volatile String finishWaitUnmet = "";
    private long finishWaitSince;
    private long finishWaitLoggedAt;
    private static final long FINISH_WAIT_LOG_MS = 30_000;
    private static final long SYSTEM_UPDATE_POLL_MS = 4000;
    private static final long UNLISTED_STUCK_MS = 120_000;
    private volatile Runnable finishCallback;
    /** Set by the plugin after Finish: the script has stopped and only the logout is awaited. */
    private volatile boolean awaitingFinishLogout;
    private volatile boolean finishComplete;
    private volatile long completedFinishGeneration;
    private volatile Runnable settingsAvailabilityChanged;
    private volatile Runnable waitingMouseFrequencyChanged;
    private boolean lastSettingsAvailable;
    private int lastWaitingMouseFrequency = -1;

	private int[] grandExchangeSlotIds = new int[] {
		InterfaceID.GeOffers.INDEX_0,
		InterfaceID.GeOffers.INDEX_1,
		InterfaceID.GeOffers.INDEX_2,
		InterfaceID.GeOffers.INDEX_3,
		InterfaceID.GeOffers.INDEX_4,
		InterfaceID.GeOffers.INDEX_5,
		InterfaceID.GeOffers.INDEX_6,
		InterfaceID.GeOffers.INDEX_7
	};

	@Inject
	private FlipperConfig config;

	@Inject
	private KeyManager keyManager;
    /** Only one FlipperScript may ever run at a time: Microbot starts the script on every enable and
     * local reload and the old instances were never stopped, which left several bots trading. */
    private static final java.util.Set<java.util.concurrent.ScheduledFuture<?>> LIVE_FUTURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();


	public boolean run(FlipperConfig config) {
		this.config = config;
		return run();
	}

	private boolean isMouseMode() {
		return config != null && config.selectionMethod() == FlipperConfig.SelectionMethod.MOUSE;
	}

    public boolean run() {
        resetWaitingMouse();
        completedOfferRecovery.reset();
        copilotFunds.reset();
        if (!LIVE_FUTURES.isEmpty()) {
            // Only one instance may ever run. Microbot restarts this script on break cycles and
            // local reloads; tracking only the newest future left the older ones running, which
            // accumulated to several bots trading at once. Cancel every one still registered.
            log.warn("Cancelling {} earlier FlipperScript instance(s) so only one runs.", LIVE_FUTURES.size());
            for (java.util.concurrent.ScheduledFuture<?> f : LIVE_FUTURES) {
                try {
                    f.cancel(true);
                } catch (Exception ignored) {
                }
            }
            LIVE_FUTURES.clear();
        }
        finishSession.close();
        muleSession.reset();
        startMuleLedgerSession();
        closeMuleConnection();
        observedMuleMode = config == null ? FlipperConfig.MuleMode.OFF : config.muleMode();
        muleFinish = false;
        updateMule = UpdateMule.NONE;
        muleNowRequest = 0;
        receiverFinishRequested = false;
        receiverFinishProfileInterrupted = false;
        secondScreenAfk.reset();
        finishCallback = null;
        finishComplete = false;
        awaitingFinishLogout = false;
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            boolean waitingTick = false;
            boolean afkTick = false;
            try {
                reconcileMuleMode();
                if (tickMuleReceiver()) return;
				if (!Microbot.isLoggedIn()) {
                    if (tickSecondScreenAfkLoggedOut()) {
                        afkTick = true;
                        return;
                    }
                }
				if (!canRunWithoutChangingSettings()) {
                    suspendForUnavailableUi();
                    return;
                }
                if (!Microbot.isLoggedIn()) return;
                handleSystemUpdate();

                if (!initializeMuleOrCopilot()) return;
                if (!initialize()) {
					log.warn("FlipperScript initialization failed. Ensure Flipping Copilot is installed and enabled.");
					return;
				}

                GeUiState ui = readGeUi();
                if (finishSession.isRequested()) {
                    if (!finishSession.begin(flippingCopilot, copilotController(), suggestionManager)
                        || !finishSession.readyForCancellation()) return;
                    if (state == State.GOING_TO_GE && !ui.exchangeOpen && !ui.bankOpen) {
                        WorldPoint location = readUi(() -> Microbot.getClient().getLocalPlayer() == null
                            ? null : Rs2Player.getWorldLocation());
                        if (location == null) return;
                        if (!grandExchangeArea.contains(location)) {
                            Rs2GrandExchange.walkToGrandExchange();
                            return;
                        }
                    }
                    state = State.MONITORING_COPILOT;
                    if (processFinish(ui)) return;
                }
                if (tickSecondScreenAfkBeforeTrading()) {
                    afkTick = true;
                    return;
                }
                switch (state) {
                    case GOING_TO_GE:
                        if (ui.exchangeOpen && Rs2Inventory.onlyContains(ItemID.COINS)) {
                             state = State.MONITORING_COPILOT;
                             return;
                        }
						// The script can tick before the player object exists (the first
						// moments after login). Calling into Rs2Player then throws inside
						// ClientThread, which logs an ERROR even when the caller catches it,
						// so wait for the player to exist before asking for its location.
						if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
							return;
						}
						WorldPoint playerLocation;
						try {
							playerLocation = Rs2Player.getWorldLocation();
						} catch (Exception e) {
							// Backstop for other transient player states; retry next tick.
							return;
						}
						if (playerLocation == null) return;
                        if (!grandExchangeArea.contains(playerLocation)) {
                            Rs2GrandExchange.walkToGrandExchange();
                        }
                        state = State.GETTING_COINS;
                        break;
                    case GETTING_COINS:
                        if (Rs2Inventory.onlyContains(ItemID.COINS)) {
                            state = State.MONITORING_COPILOT;
                            return;
                        }
                        if (Rs2Bank.openBank()) {
                            Rs2Bank.depositAll();
                            waitForUi(Rs2Inventory::isEmpty);
                            Rs2Bank.withdrawAll(ItemID.COINS);
                            Rs2Inventory.waitForInventoryChanges(INVENTORY_WAIT_TIMEOUT);
                            Rs2Bank.closeBank();
                            waitForUi(() -> !isBankOpen());
                            state = State.MONITORING_COPILOT;
                        }
                        break;

                    case MONITORING_COPILOT:
						long currentTime = System.currentTimeMillis();
                        if (isSlotActionBlocked()) return;

						// 0a. Grand Exchange watchdog: this state had no way back from a closed
						// exchange - GOING_TO_GE was only set on shutdown/startup - so a closed or
						// hidden GE stalled the bot indefinitely and silently. Reopen it directly.
						if (!ui.exchangeOpen && !ui.bankOpen) {
							if (geClosedSince == 0) {
								geClosedSince = currentTime;
							} else if (currentTime - geClosedSince > GE_CLOSED_RECOVER_MS) {
								long closedFor = currentTime - geClosedSince;
								geClosedSince = 0;
								log.info("Grand Exchange closed for {}ms while running; reopening it.", closedFor);
								if (!Rs2GrandExchange.openExchange()) {
									log.info("Exchange could not be opened from here; walking to the Grand Exchange.");
									state = State.GOING_TO_GE;
								} else {
									waitForUi(this::isExchangeOpen, 3000);
								}
								lastActionTime = System.currentTimeMillis();
								actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
								return;
							}
						} else {
							geClosedSince = 0;
						}

						// 0b. Stray GE page watchdog: a mistimed click can open a GE info page
						// (for example the Convenience Fees text) which hides the offer list.
						// Nothing on that page is actionable, Copilot highlights nothing, and the
						// script would otherwise idle silently forever. Escape back to the list.
						if (ui.exchangeOpen && !ui.offerOpen && !ui.overviewOpen) {
							if (strayPageSince == 0) {
								strayPageSince = currentTime;
							} else if (currentTime - strayPageSince > STRAY_PAGE_RECOVER_MS) {
								long strayFor = currentTime - strayPageSince;
								strayPageSince = 0;
								log.info("GE is showing a non-offer page for {}ms; escaping back to the offer list.", strayFor);
                                BooleanSupplier escapeGuard = tradingClickGuard();
                                if (!escapeGuard.getAsBoolean() || !readUi(() -> {
                                    GeUiState current = readGeUi();
                                    return current.exchangeOpen && !current.offerOpen && !current.overviewOpen;
                                }) || !escapeGuard.getAsBoolean()) return;
                                dispatchTradingKey(KeyEvent.VK_ESCAPE);
								sleep(300, 600);
								lastActionTime = System.currentTimeMillis();
								actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
								return;
							}
						} else {
							strayPageSince = 0;
						}

							// 0. Offer screen watchdog & loop detection
						if (ui.offerOpen) {
							if (offerScreenOpenTime == 0) {
								offerScreenOpenTime = currentTime;
								offerScreenActionCount = 0;
							}

							// Check if "Too much money!" warning is shown on offer screen
							if (ui.tooMuchMoney) {
								log.warn("Offer has 'Too much money!' error. Backing out to GE overview.");
								backToOverview();
								return;
							}

							// If on offer screen and Copilot suggests ABORT, abort via offer screen button
							if (suggestionManager != null) {
								try {
									Object currentSuggestion = getSuggestion(suggestionManager);
									if (currentSuggestion != null) {
										Method isAbortMethod = currentSuggestion.getClass().getMethod("isAbortSuggestion");
										if ((Boolean) isAbortMethod.invoke(currentSuggestion)) {
											// A suggestion for another slot must never abort the offer being viewed.
                                            if (!abortOfferDetailsMatches(currentSuggestion)) {
                                                backToOverview();
                                                return;
                                            }
											Widget abortBtn = waitForOfferScreenAbortButton(2000);
											if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
												log.info("Aborting offer via offer screen button '{}'", abortBtn.getId());
                                                if (!clickAbortOfferDetails(currentSuggestion, abortBtn)) return;
												sleep(300, 500);

												// Check for confirmation dialog ('Are you sure...')
												if (waitForUi(this::isGeWarningOpen, 1200)) {
													GeWarningDialog.Result warning = confirmGeWarning();
													if (warning != GeWarningDialog.Result.CONFIRMED
														&& warning != GeWarningDialog.Result.NO_DIALOG) return;
												}

												// Wait for abort to register, then back to overview
												waitForUi(() -> !isOfferScreenOpen() || getOfferScreenAbortButton() == null, 2500);
												backToOverview();
											} else {
												// Check if already aborted / cancelled on offer screen
												Widget statusWidget = readUi(() ->
                                                    Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS_STATUS));
												String statusText = statusWidget != null ? statusWidget.getText() : "";
												if (statusText != null && (statusText.toLowerCase().contains("cancelled") || statusText.toLowerCase().contains("aborted"))) {
													log.info("Offer already cancelled on offer screen. Returning to overview.");
												} else if (isAbortSuggestionSettled(currentSuggestion)) {
													// Copilot drops the abort suggestion as soon as the abort
													// registers, so a changed suggestion means the work is done.
													log.info("Abort suggestion already satisfied; no abort button needed. Returning to overview.");
												} else {
													log.warn("Abort button not found on offer screen. Returning to overview.");
												}
												backToOverview();
											}
											lastActionTime = currentTime;
											actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
											return;
										}
									}
								} catch (GeUiState.UiUnavailable unavailable) {
                                    throw unavailable;
                                } catch (Exception ignored) {}
							}

							// If nothing at all has been actioned on the open offer screen, the
							// script is waiting on Copilot rather than making progress. Seen when
							// Copilot flips its suggestion to COLLECT while the offer screen is
							// open: Collect is a GE-overview action, so no highlight on the offer
							// screen can ever match it. Recover in 10s instead of holding the
							// screen (and the offer slot) for the full 30s.
							long stuckLimitMs = offerScreenActionCount == 0 ? 10000 : 30000;
							if (currentTime - offerScreenOpenTime > stuckLimitMs || offerScreenActionCount >= 10) {
								log.warn("Offer screen stuck (openTime={}ms, actions={}). Backing out to GE overview.",
									currentTime - offerScreenOpenTime, offerScreenActionCount);
								backToOverview();
								return;
							}
						} else {
							offerScreenOpenTime = 0;
							offerScreenActionCount = 0;
						}

                        // 1. If bank is open, close it (only coins are handled from bank at startup)
                        if (ui.bankOpen) {
                            Rs2Bank.closeBank();
                            waitForUi(() -> !isBankOpen(), 2500);
                            return;
                        }

                        // A displayed funds warning can omit highlights on completed receipts.
                        if (collectCompletedWhileWaiting()) return;

                        // Handle slot suggestions before chat/highlight fallbacks.
                        if (checkAndAbortOrModifyIfNeeded()) return;
						if (checkAndPressCopilotKeybind()) return;

                        // 4. Check for highlighted widgets
                        if (checkAndClickHighlightedWidgets()) return;

                        // 5. Check for highlighted NPCs
                        if (checkAndInteractHighlightedNpc()) return;

                        // 6. If neither GE nor Bank is open, open GE
                        if (!isExchangeOpen() && !isBankOpen()) {
                            Rs2GrandExchange.openExchange();
                            return;
                        }

                        // Optional movement comes last, after every trading/recovery action.
                        waitingTick = moveMouseWhileWaiting();
                        afkTick = tickSecondScreenAfkSettledWait();
                        break;
                }
            } catch (GeUiState.UiUnavailable unavailable) {
                suspendForUnavailableUi();
            } catch (VirtualMachineError fatal) {
                // Out of memory or stack: log it, then let it end the schedule.
                log.error("Fatal error in FlipperScript; GE Flipper stopped working. Restart the client.", fatal);
                throw fatal;
            } catch (Throwable ex) {
                // An Error (e.g. NoSuchMethodError after a client update) must not silently end the
                // scheduled loop, so it is caught here too and logged with its stack.
                reportTickError(ex);
            } finally {
                if (!waitingTick) resetWaitingMouse();
                if (!afkTick) secondScreenAfk.recordActivity();
                notifySettingsAvailabilityChanged();
                notifyWaitingMouseFrequencyChanged();
            }
        }, 0, SCHEDULE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LIVE_FUTURES.add(mainScheduledFuture);
        return true;
    }


    private String lastTickErrorKey = "";
    private long lastTickErrorLoggedAt;
    private static final long TICK_ERROR_REPEAT_MS = 60_000;

    /** Logs a tick failure with its stack the first time, then the same failure at most once a minute. */
    void reportTickError(Throwable error) {
        String key = error.getClass().getName() + ": " + error.getMessage();
        long now = System.currentTimeMillis();
        if (key.equals(lastTickErrorKey) && now - lastTickErrorLoggedAt < TICK_ERROR_REPEAT_MS) return;
        boolean repeat = key.equals(lastTickErrorKey);
        lastTickErrorKey = key;
        lastTickErrorLoggedAt = now;
        if (repeat) {
            log.error("Error in FlipperScript (repeating): {}", key);
        } else {
            log.error("Error in FlipperScript: {}", key, error);
        }
    }

    /** Only the settings button (or a system update) requests this transient session; config events do not. */
    boolean requestFinish(Runnable completed) {
        if (!isRunning() || completed == null) return false;
        if (muleFinish) {
            // A mule sell-down is already running in sell-only mode; adopt it as the user's Finish.
            muleFinish = false;
            muleSession.cancelLiquidation();
            // The sell-down waited for fills. The user's Finish only needs every item listed for sale.
            finishSession.stopWaitingForSales();
        } else if (!finishSession.request(false)) {
            // Listing is enough: waiting for every sell offer to fill kept Finish from ever stopping.
            return false;
        }
        finishCallback = completed;
        if (config != null && config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS
            && !Microbot.isLoggedIn()) secondScreenAfk.resumeForFinish();
        finishComplete = false;
        finishWaitUnmet = "";
        receiverFinishRequested = config != null
            && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER;
        // A transfer already under way when an OSRS update was announced carries on; resetting its runtime here
        // would drop a half-typed coin amount mid-trade.
        if (updateMule == UpdateMule.NONE || !muleSession.active()) muleRuntime.reset();
        blockedSlotActionKey = null;
        slotActionStatus = "";
        invalidateMouseMovement();
        notifySettingsAvailabilityChanged();
        return true;
    }

    void setSystemUpdateHandler(BooleanSupplier handler) {
        systemUpdateHandler = handler;
    }

    /** Seconds left on the OSRS update countdown, or -1. Throttled; never throws. */
    private int systemUpdateSeconds(long now) {
        if (now - lastSystemUpdateCheck < SYSTEM_UPDATE_POLL_MS) return lastSystemUpdateSeconds;
        lastSystemUpdateCheck = now;
        int seconds = -1;
        try {
            Widget widget = findUiWidget(SystemUpdate.MARKER, null, false);
            if (widget != null) {
                String text = readUi(widget::getText);
                seconds = SystemUpdate.parseSeconds(text);
            }
        } catch (RuntimeException unavailable) {
            seconds = -1;
        }
        if (seconds >= 0) lastSystemUpdateSeen = now;
        lastSystemUpdateSeconds = seconds;
        return seconds;
    }

    /** Starts Finish once per update countdown and relaxes it when the update is close. */
    private void handleSystemUpdate() {
        if (config == null) return;
        boolean finishOnUpdate = config.autoFinishOnSystemUpdate();
        boolean muleOnUpdate = muleBeforeUpdateEnabled();
        if (!finishOnUpdate && !muleOnUpdate) return;
        long now = System.currentTimeMillis();
        int seconds = systemUpdateSeconds(now);
        if (seconds < 0) {
            if (now - lastSystemUpdateSeen > 120_000) {
                systemUpdateHandled = false;
                systemUpdateRefusalLogged = false;
                // The update passed (or was called off). A transfer already under way finishes on its own.
                if (updateMule != UpdateMule.RUNNING) updateMule = UpdateMule.NONE;
            }
            return;
        }
        if (muleOnUpdate && updateMule == UpdateMule.NONE) {
            updateDueAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + seconds * 1000L;
            if (finishOnUpdate) {
                updateMule = UpdateMule.AFTER_FINISH;
                diagnostics.info("OSRS update in {}s: GP above the keep amount goes to the Mule once Finish has listed "
                    + "everything.", seconds);
            } else {
                startUpdateMule();
            }
        }
        if (!finishOnUpdate) return;
        if (seconds <= SystemUpdate.STOP_WAITING_FOR_SALES_SECONDS) finishSession.stopWaitingForSales();
        if (systemUpdateHandled) return;
        BooleanSupplier handler = systemUpdateHandler;
        if (handler == null || (finishSession.isRequested() && !muleFinish)) return;
        // Latch only once Finish really started; a refused request is retried.
        if (handler.getAsBoolean()) {
            systemUpdateHandled = true;
            systemUpdateRefusalLogged = false;
            log.warn("OSRS system update in {}s: finishing offers and stopping GE Flipper.", seconds);
        } else if (!systemUpdateRefusalLogged) {
            systemUpdateRefusalLogged = true;
            log.warn("OSRS system update in {}s, but Finish could not start yet. Retrying.", seconds);
        }
    }

    private boolean muleBeforeUpdateEnabled() {
        return config != null && config.muleBeforeUpdate()
            && config.muleMode() == FlipperConfig.MuleMode.FLIPPER_SENDER
            && config.muleRsn() != null && !config.muleRsn().trim().isEmpty();
    }

    /** Hands the Mule session one transfer down to the keep amount, unless the update is already too close. */
    private void startUpdateMule() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        if (now > updateDueAt - UPDATE_MULE_MIN_START_MS) {
            updateMule = UpdateMule.DONE;
            diagnostics.info("OSRS update is too close to mule first; skipping the transfer.");
            return;
        }
        muleSession.requestTransferBeforeUpdate(updateDueAt - UPDATE_MULE_MARGIN_MS);
        updateMule = UpdateMule.RUNNING;
        diagnostics.info("OSRS update in {}s: sending GP above the keep amount to the Mule first.",
            (updateDueAt - now) / 1000);
    }

    void setSettingsAvailabilityChanged(Runnable listener) {
        settingsAvailabilityChanged = listener;
    }

    private void notifySettingsAvailabilityChanged() {
        boolean available = isRunning() && !isFinishing();
        if (available == lastSettingsAvailable) return;
        lastSettingsAvailable = available;
        Runnable listener = settingsAvailabilityChanged;
        if (listener != null) listener.run();
    }

    int waitingMouseFrequency(FlipperConfig settings) {
        return settings == null ? 0 : waitingMousePresets.frequency(settings);
    }

    String waitingMouseDescription(FlipperConfig settings) {
        if (settings == null) return "Waiting mouse randomization";
        String description = waitingMousePresets.description(settings);
        return settings.waitingMousePreset() == FlipperConfig.RandomizationPreset.DAY_FATIGUE
            && settings.randomizeMouseSpeed()
            ? description + " This preset controls the slider; select Custom to edit it."
            : description;
    }

    void resetWaitingMousePresets() {
        waitingMousePresets.reset();
    }

    void setWaitingMouseFrequencyChanged(Runnable listener) {
        waitingMouseFrequencyChanged = listener;
    }

    private void notifyWaitingMouseFrequencyChanged() {
        int frequency = waitingMouseFrequency(config);
        if (frequency == lastWaitingMouseFrequency) return;
        lastWaitingMouseFrequency = frequency;
        Runnable listener = waitingMouseFrequencyChanged;
        if (listener != null) listener.run();
    }

    boolean isFinishing() { return finishSession.isRequested() && !muleFinish; }
    void setAwaitingFinishLogout(boolean awaiting) { awaitingFinishLogout = awaiting; }
    boolean isMuleActive() { return muleSession.active(); }
    public long getTotalMuledGp() { return muleSession.totalTransferredGp(); }
    /** Receiver totals for the overlays: session, all time, last-seen coins and GP per hour. */
    public MuleLedger.Snapshot getMuleLedgerSnapshot() { return muleLedger.snapshot(); }
    /** Chooses where the receiver's all-time totals persist; the next run reads them. */
    void attachMuleLedgerStore(MuleLedger.Store store) { muleLedger.attachStore(store); }
    void attachMuleLedgerStore(java.util.function.Supplier<MuleLedger.Store> stores) { muleLedgerStores = stores; }
    private void startMuleLedgerSession() {
        java.util.function.Supplier<MuleLedger.Store> stores = muleLedgerStores;
        if (stores == null) muleLedger.startSession();
        else muleLedger.startSession(stores.get());
    }
    public MuleConnectionMonitor.Snapshot getMuleConnectionStatus() {
        MuleConnectionMonitor monitor = muleConnection;
        return monitor == null
            ? new MuleConnectionMonitor.Snapshot(MuleConnectionMonitor.State.OFF, false, false, 0)
            : monitor.snapshot();
    }
    public String getMuleCapitalStatus() { return muleSession.capitalStatus(); }
    // Overlay reads below: cached values only, never a game or network action.
    /** The last verified working-capital estimate, or -1. */
    public long getMuleCapitalGp() { return muleSession.lastCapitalGp(); }
    public int getMuleKeepGp() { return displayKeepGp; }
    /** The amount that starts a transfer, or -1 when it is not a valid amount. */
    public int getMuleTriggerGp() { return displayTriggerGp; }
    public long getLastMuleGp() { return muleSession.lastTransferGp(); }
    /** Milliseconds since the last completed transfer, or -1 when none this session. */
    public long getLastMuleAgeMs() {
        long at = muleSession.lastTransferAt();
        return at < 0 ? -1 : Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - at);
    }
    /** Seconds left on the OSRS update countdown, or -1. */
    public int getSystemUpdateSeconds() {
        int seconds = lastSystemUpdateSeconds;
        if (seconds < 0) return -1;
        return (int) Math.max(0, seconds - (System.currentTimeMillis() - lastSystemUpdateCheck) / 1000);
    }
    /** True while GE Flipper still plans to mule before the OSRS update. */
    public boolean isMuleBeforeUpdatePlanned() {
        UpdateMule update = updateMule;
        return update == UpdateMule.AFTER_FINISH || update == UpdateMule.RUNNING;
    }
    /** A short word for what GE Flipper is doing right now. */
    public String getActivity() {
        if (awaitingFinishLogout) return "Waiting for logout";
        if (muleSession.active()) return updateMule == UpdateMule.RUNNING ? "Muling before update" : "Muling";
        if (muleFinish) return "Freeing GP for mule";
        if (finishSession.isRequested()) return updateMule != UpdateMule.NONE ? "Finishing for update" : "Finishing";
        if (config != null && config.secondScreenAfkMode() != FlipperConfig.SecondScreenAfkMode.OFF
            && !secondScreenAfk.status().isEmpty()) return "AFK check-in";
        return "Flipping";
    }
    public String getMuleSettingsStatus() {
        if (config == null || config.muleMode() == FlipperConfig.MuleMode.OFF) return "Muling: Off";
        String label = config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER ? "Flipper link: " : "Mule link: ";
        return label + getMuleConnectionStatus().label();
    }
    private synchronized MuleConnectionMonitor muleConnection() {
        if (muleConnection == null) muleConnection = new MuleConnectionMonitor(muleRuntime::probeMuleHealth);
        return muleConnection;
    }
    private synchronized void closeMuleConnection() {
        muleConnectionGeneration++;
        MuleConnectionMonitor monitor = muleConnection;
        muleConnection = null;
        if (monitor != null) monitor.close();
    }
    private void refreshSenderConnection() {
        final long generation;
        synchronized (this) {
            if (config == null || config.muleMode() != FlipperConfig.MuleMode.FLIPPER_SENDER || !isRunning()
                || Microbot.getClientThread() == null) return;
            generation = muleConnectionGeneration;
        }
        MuleIdentity identity = readUi(() -> {
            net.runelite.api.Client client = Microbot.getClient();
            if (client == null || client.getGameState() != net.runelite.api.GameState.LOGGED_IN
                || client.getLocalPlayer() == null) return new MuleIdentity(0, "");
            return new MuleIdentity(client.getAccountHash(), client.getLocalPlayer().getName());
        });
        publishSenderConnection(generation, identity);
    }
    private synchronized void publishSenderConnection(long generation, MuleIdentity identity) {
        if (generation != muleConnectionGeneration || !isRunning() || config == null
            || config.muleMode() != FlipperConfig.MuleMode.FLIPPER_SENDER) return;
        muleConnection().updateSender(identity.account, identity.name, config.muleRsn(), config.mulePort());
    }
    private static final class MuleIdentity {
        final long account;
        final String name;
        MuleIdentity(long account, String name) { this.account = account; this.name = name; }
    }
    void pauseMuleForProfileChange() {
        if (receiverFinishRequested && finishSession.isRequested()) receiverFinishProfileInterrupted = true;
        receiverFinishRequested = false;
        muleSession.reset();
        // A replacement store captures this profile's local path; stale callbacks cannot redirect writes.
        startMuleLedgerSession();
        closeMuleConnection();
    }

    void recordMuleTradeRequest(String requester, long account) {
        if (!isRunning() || config == null || isFinishing()
            || config.muleMode() != FlipperConfig.MuleMode.MULE_RECEIVER) return;
        muleSession.recordTradeRequest(requester, account, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    void invalidateMuleActions() {
        muleRuntime.reset();
        invalidateMouseMovement();
    }

    /** Return and receiver shutdown remain permitted after a user disables muling or requests Finish. */
    private boolean muleReceiverEnabled() {
        return isRunning() && config != null && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER
            && !receiverFinishRequested;
    }

    private boolean muleActionsAllowed() {
        return isRunning() && config != null
            && (config.muleMode() != FlipperConfig.MuleMode.OFF || muleSession.active() || receiverFinishRequested)
            && (!finishSession.isRequested() || muleFinish || muleSession.active() || receiverFinishRequested
                || updateMule == UpdateMule.RUNNING);
    }

    /** Runs before the logged-in guard so disabling an offline receiver also stops its listener. */
    private void reconcileMuleMode() {
        FlipperConfig.MuleMode mode = config == null ? FlipperConfig.MuleMode.OFF : config.muleMode();
        if (mode == observedMuleMode) return;
        closeMuleConnection();
        if (muleFinish) endMuleLiquidation();
        muleSession.stopReceiver();
        observedMuleMode = mode;
    }
    void resetSecondScreenAfk() { secondScreenAfk.reset(); }

    /** True while GE Flipper is intentionally staying logged out (Mule standby or Second-screen AFK). */
    public boolean isIntentionalLogoutActive() {
        if (!isRunning() || config == null) return false;
        if (config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER && !muleSession.active()) {
            return muleSession.receiverHoldingLogout();
        }
        return config.secondScreenAfkMode() != FlipperConfig.SecondScreenAfkMode.OFF
            && secondScreenAfk.holdingLogout();
    }

    private boolean secondScreenAfkSuppressed() {
        return config == null
            || config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER
            || finishSession.isRequested()
            || isMuleActive()
            || muleSession.liquidationRequested() && getMuleConnectionStatus().availableForPreparation;
    }

    private boolean tickSecondScreenAfkLoggedOut() {
        if (config == null || config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF) {
            secondScreenAfk.reset();
            return false;
        }
        ScriptHeartbeatRegistry.recordHeartbeat(getClass().getName());
        // Finish may bring back the login this controller already owned, but cannot start a new break.
        boolean finishingOwnedLogin = finishSession.isRequested()
            && secondScreenAfk.phase() == SecondScreenAfk.Phase.LOGGING_IN
            && config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS
            && !isMuleActive();
        boolean owns = secondScreenAfk.tickLoggedOut(
            config.secondScreenAfkMode(),
            config.secondScreenAfkMinutes(),
            secondScreenAfkSuppressed() && !finishingOwnedLogin,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        if (owns) resetWaitingMouse();
        return owns;
    }

    private boolean tickSecondScreenAfkBeforeTrading() {
        if (config == null || config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF) {
            secondScreenAfk.reset();
            return false;
        }
        boolean owns = secondScreenAfk.tickBeforeTrading(
            config.secondScreenAfkMode(),
            config.secondScreenAfkMinutes(),
            secondScreenAfkSuppressed(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        if (owns) resetWaitingMouse();
        return owns;
    }

    private boolean tickSecondScreenAfkSettledWait() {
        if (config == null || config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF) {
            secondScreenAfk.reset();
            return false;
        }
        return secondScreenAfk.onSettledWaitTick(
            config.secondScreenAfkMode(),
            config.secondScreenAfkMinutes(),
            secondScreenAfkSuppressed(),
            isSafeSecondScreenWait(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    private MuleSession.ReceiverSettings receiverSettings() {
        return new MuleSession.ReceiverSettings(config.mulePort(), config.muleWorld(),
            MulePolicy.TileSpec.auto(), config.muleRsn());
    }

    /** The Mule role works while logged out, so it runs before any logged-in guard. */
    private boolean tickMuleReceiver() {
        if (receiverFinishProfileInterrupted && finishSession.isRequested()) return true;
        boolean receiver = config != null && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER;
        if (receiver && finishSession.isRequested() && !muleFinish) receiverFinishRequested = true;
        if (receiverFinishRequested) {
            closeMuleConnection();
            if (muleSession.active()) return false;
            ScriptHeartbeatRegistry.recordHeartbeat(getClass().getName());
            resetWaitingMouse();
            if (muleSession.finishReceiver(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))) {
                if (!finishComplete) {
                    completedFinishGeneration = finishSession.generation();
                    finishComplete = true;
                    Runnable callback = finishCallback;
                    if (callback != null) callback.run();
                }
            } else {
                finishComplete = false;
            }
            return true;
        }
        // Finish an existing sender's return journey before starting the receiver role.
        if (!receiver || muleSession.active()) return false;
        ScriptHeartbeatRegistry.recordHeartbeat(getClass().getName());
        final long generation;
        synchronized (this) { generation = muleConnectionGeneration; }
        muleSession.tickReceiver(receiverSettings(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        logMuleProgress(true);
        synchronized (this) {
            if (generation == muleConnectionGeneration && isRunning() && config != null
                && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER && !receiverFinishRequested) {
                muleConnection().updateReceiver(muleRuntime.muleServerHealth());
            }
        }
        resetWaitingMouse();
        return true;
    }

    /** Writes each new mule step to client.log, so a cancelled transfer or an unexpected hop shows its reason. */
    private void logMuleProgress(boolean receiver) {
        String cancelled = muleSession.takeCancelReason();
        // Shown in chat once: the return trip replaces the status straight away.
        if (!cancelled.isEmpty()) log.warn(cancelled);
        boolean active = receiver || muleSession.active();
        String status = muleSession.status();
        if ((active || muleWasActive) && status != null && !status.isEmpty() && !status.equals(lastMuleStatusLogged)) {
            diagnostics.info(status);
            lastMuleStatusLogged = status;
        } else if (!active && !muleWasActive) {
            lastMuleStatusLogged = "";
        }
        muleWasActive = active;
    }

    /**
     * "Keep during update": blank uses the keep amount (-1). A typo also falls back to the keep amount, which never
     * sends more than the player expects.
     */
    static int updateKeepGp(FlipperConfig config) {
        String text = config == null ? null : config.muleUpdateKeepGp();
        if (text == null || text.trim().isEmpty()) return -1;
        return MulePolicy.parseAmountSetting(text);
    }

    /**
     * "Mule now" from the settings button: sends the "Mule now amount" to the Mule, freeing up GP first if needed.
     * Returns why it can't, or null once the request is queued for the script thread. Any thread.
     */
    String requestMuleNow(FlipperConfig config) {
        if (!isRunning() || config == null) return "switch GE Flipper on first.";
        if (config.muleMode() != FlipperConfig.MuleMode.FLIPPER_SENDER) {
            return "set 'Mule mode' to Flipper (sends GP) first.";
        }
        if (config.muleRsn() == null || config.muleRsn().trim().isEmpty()) return "enter your Mule's name (RSN) first.";
        if (finishSession.isRequested() && !muleFinish) return "End / Finish is running.";
        if (muleSession.active() || muleNowRequest > 0 || muleSession.manualTransferPending()) {
            return "a transfer is already under way.";
        }
        if (!getMuleConnectionStatus().availableForPreparation) {
            return "the Mule isn't connected. Start the Mule's client with 'Mule mode' set to Mule, then try again.";
        }
        int amount = MulePolicy.parseAmountSetting(config.muleNowAmount());
        if (amount < MulePolicy.MIN_TRANSFER_GP) {
            return "enter an amount in 'Mule now amount' first (a plain number is millions: 100 = 100M).";
        }
        muleNowRequest = amount;
        return null;
    }

    /** Whether the "Mule now" button can be pressed. Cached state only, for the settings panel. */
    boolean muleNowAvailable(FlipperConfig config) {
        return isRunning() && config != null && config.muleMode() == FlipperConfig.MuleMode.FLIPPER_SENDER
            && !muleSession.active() && muleNowRequest == 0 && !muleSession.manualTransferPending()
            && (!finishSession.isRequested() || muleFinish);
    }

    private boolean initializeMuleOrCopilot() {
        refreshSenderConnection();
        FlipperConfig.MuleMode muleMode = config == null ? FlipperConfig.MuleMode.OFF : config.muleMode();
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        int capitalLimit = config == null ? -1 : MulePolicy.parseAmountSetting(config.muleThresholdGp());
        int trigger = config == null ? -1 : MulePolicy.parseTrigger(config.muleTriggerGp(), capitalLimit);
        displayKeepGp = capitalLimit;
        displayTriggerGp = trigger < 0 ? -1 : Math.max(trigger, capitalLimit);
        MuleSession.SenderSettings settings = new MuleSession.SenderSettings(
            muleMode == FlipperConfig.MuleMode.FLIPPER_SENDER,
            trigger,
            capitalLimit,
            updateKeepGp(config),
            config == null ? "" : config.muleRsn(),
            "auto",
            config == null ? MulePolicy.DEFAULT_PORT : config.mulePort(),
            config == null ? 0 : config.muleWorld(),
            MulePolicy.TileSpec.auto());
        int muleNow = muleNowRequest;
        if (muleNow > 0) {
            muleNowRequest = 0;
            muleSession.requestManualTransfer(muleNow);
        }
        boolean userFinish = finishSession.isRequested() && !muleFinish;
        UpdateMule update = updateMule;
        // The update transfer, and one already under way when the update was announced, are finished, not cancelled.
        boolean updateTransfer = update == UpdateMule.RUNNING || (update != UpdateMule.NONE && muleSession.active());
        boolean ownsTick = muleSession.tickSender(settings, userFinish && !updateTransfer, now);
        if (update == UpdateMule.RUNNING) {
            if (!muleSession.active() && now > updateDueAt) muleSession.cancelTransferBeforeUpdate();
            if (!muleSession.transferBeforeUpdateBusy()) {
                updateMule = UpdateMule.DONE;
                String result = muleSession.status();
                diagnostics.info("OSRS update: mule step finished{}", result == null || result.isEmpty() ? "." : " (" + result + ")");
            }
        }
        logMuleProgress(false);
        manageMuleLiquidation(now);
        if (ownsTick) {
            resetWaitingMouse();
            return false;
        }
        return true;
    }

    /** Temporarily frees the estimated surplus, stopping as soon as it can be transferred. */
    private void manageMuleLiquidation(long now) {
        boolean connected = getMuleConnectionStatus().availableForPreparation;
        boolean wanted = muleSession.liquidationRequested() && connected;
        if (wanted && !finishSession.isRequested()) {
            if (finishSession.request(true)) {
                muleFinish = true;
                finishCallback = () -> { };
                finishComplete = false;
                blockedSlotActionKey = null;
                invalidateMouseMovement();
                log.info("Mule: freeing up GP above 'Keep for flipping' for the transfer.");
            }
        } else if (muleFinish && !wanted) {
            // A cancelled offer's refund is not valued until collected. Collect it before deciding
            // that the estimated surplus disappeared, without cancelling further retained buys.
            if (connected && !muleSession.active()) {
                FinishOffers pending = readFinishOffers();
                if (pending != null && !pending.noCollectables) return;
            }
            endMuleLiquidation();
        } else if (muleFinish && finishComplete) {
            // Everything is sold and collected but the trigger amount is still not reached.
            muleSession.liquidationStalled(now);
            endMuleLiquidation();
        }
    }

    private void endMuleLiquidation() {
        muleFinish = false;
        finishSession.close();
        finishCallback = null;
        finishComplete = false;
    }
    boolean isFinishComplete() {
        if (!finishComplete || receiverFinishProfileInterrupted) return false;
        if (receiverFinishRequested) {
            return finishSession.isRequested() && completedFinishGeneration == finishSession.generation()
                && !muleSession.active();
        }
        if (completedFinishGeneration != finishSession.generation()
            || !finishSession.healthyFreshWait(getSuggestion(suggestionManager))) {
            // A declined EDT stop must let the worker verify readiness and queue another stop.
            finishComplete = false;
            return false;
        }
        return true;
    }

    void pauseFinishForProfileChange() {
        if (!finishSession.isRequested()) return;
        finishSession.pauseForProfileChange();
        finishComplete = false;
        clearCachedTradingState();
    }

    private Object copilotController() {
        try {
            Field field = flippingCopilot.getClass().getDeclaredField("suggestionController");
            field.setAccessible(true);
            return field.get(flippingCopilot);
        } catch (ReflectiveOperationException | NullPointerException unavailable) {
            return null;
        }
    }

    /** A retained recommendation is not permission to trade after Copilot is paused or disabled. */
    boolean copilotTradingHealthy() {
        Plugin copilot = flippingCopilot;
        Object manager = suggestionManager;
        if (copilot == null || manager == null || Microbot.getPluginManager() == null
            || !Microbot.getPluginManager().isPluginActive(copilot)
            || !Microbot.getPluginManager().isPluginEnabled(copilot)) return false;
        try {
            Object controller = copilotController();
            if (controller == null) return false;
            Object paused = controller.getClass().getMethod("getPausedManager").invoke(controller);
            return paused != null && Boolean.FALSE.equals(paused.getClass().getMethod("isPaused").invoke(paused))
                && manager.getClass().getMethod("getSuggestionError").invoke(manager) == null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private boolean finishPermitsTrade() {
        return !isMuleActive()
            && (!finishSession.isRequested() || finishSession.canTrade(getSuggestion(suggestionManager)));
    }

    private FinishOffers readFinishOffers() {
        return readUi(() -> FinishOffers.capture(
            Microbot.getClient().getGrandExchangeOffers(),
            Microbot.getClient().getItemContainer(InventoryID.INVENTORY),
            id -> {
                if (id <= 0) return false;
                try {
                    net.runelite.client.game.ItemManager itemManager = Microbot.getItemManager();
                    if (itemManager == null) return true;
                    int canonicalId = itemManager.canonicalize(id);
                    ItemComposition comp = itemManager.getItemComposition(canonicalId > 0 ? canonicalId : id);
                    if (comp != null && comp.getNote() != -1 && comp.getLinkedNoteId() > 0) {
                        comp = itemManager.getItemComposition(comp.getLinkedNoteId());
                    }
                    return comp == null || comp.isTradeable();
                } catch (Exception e) {
                    return true;
                }
            }));
    }

    private boolean finishBuyStillActive(int slot) {
        return readUi(() -> finishSession.isActive() && isRunning()
            && finishSession.readyForCancellation()
            && !FlipperRunGuard.scriptsPaused() && !InputArbiter.isHuman()
            && Microbot.isLoggedIn() && readGeUi().overviewOpen
            && FinishOffers.isActiveBuy(Microbot.getClient().getGrandExchangeOffers(), slot));
    }

    private void cancelFinishBuy(int slot) {
        int id = grandExchangeSlotIds[slot];
        // Finish cancels every buy. The verified explicit operation does not depend on Copilot's swap.
        SlotActionExecutor.Result result = SlotActionExecutor.execute(FlipperConfig.SlotAction.MENU_OPTION,
            SlotActionExecutor.Action.ABORT, id, new SlotActionExecutor.Ui() {
                public boolean slotSwapEnabled() { return false; }
                public net.runelite.api.Point actionPoint(int widgetId, SlotActionExecutor.Action action) {
                    return finishBuyStillActive(slot) ? slotActionPoint(widgetId, action) : null;
                }
                public boolean hover(net.runelite.api.Point point) { return true; }
                public SlotActionExecutor.Readiness awaitDefaultAction(int widgetId, SlotActionExecutor.Action action,
                                                  net.runelite.api.Point point) { return SlotActionExecutor.Readiness.CANCELLED; }
                public boolean clickDefaultAction(int widgetId, SlotActionExecutor.Action action,
                                                  net.runelite.api.Point point) { return false; }
                public boolean invokeAction(int widgetId, SlotActionExecutor.Action action,
                                            net.runelite.api.Point point) {
                    if (Thread.currentThread().isInterrupted() || !finishBuyStillActive(slot)
                        || slotActionPoint(widgetId, action) == null) return false;
                    return invokeTradingMouse(new NewMenuEntry().option(action.option).target("")
                        .identifier(action.identifier).type(MenuAction.CC_OP).param0(2).param1(widgetId)
                        .itemId(-1).forceLeftClick(false),
                        new Rectangle(point.getX() - 1, point.getY() - 1, 2, 2),
                        () -> finishBuyStillActive(slot) && point.equals(slotActionPoint(widgetId, action)));
                }
            });
        lastActionTime = System.currentTimeMillis();
        actionCooldown = DEFAULT_ACTION_COOLDOWN;
        if (result == SlotActionExecutor.Result.ACTED) {
            slotActionStatus = "";
            log.info("Finish: buy cancellation requested; waiting for the offer state to update.");
        } else {
            slotActionStatus("Finish: buy cancellation is unavailable. Check the GE overview or handle it manually.");
        }
    }

    private int finishCollectOperation() {
        return readUi(() -> {
            if (!readGeUi().overviewOpen) return -1;
            return FinishCollect.operation(Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL),
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
        });
    }

    private Rectangle finishCollectBounds() {
        return readUi(() -> {
            if (!readGeUi().overviewOpen) return null;
            return FinishCollect.bounds(Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL),
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
        });
    }

    /** The existing loop owns this recovery; it never changes Copilot or active offers. */
    boolean collectCompletedWhileWaiting() {
        Object manager = suggestionManager;
        Object controller = copilotController();
        Object suggestion = readUi(() -> WaitingMouse.copilotWaiting(manager, controller)
            ? getSuggestion(manager) : null);
        boolean funds = suggestion != null && copilotFunds.current(flippingCopilot, manager, suggestion);
        if (!completedOfferRecovery.due(manager, funds,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))) return false;
        int op = finishCollectOperation();
        Rectangle bounds = readUi(() -> completedWaitCollectBounds(manager, controller, suggestion));
        if (op <= 0 || bounds == null) return false;
        resetWaitingMouse();
        if (!invokeTradingMouse(new NewMenuEntry().option("Collect to inventory").target("")
            .identifier(op).type(MenuAction.CC_OP).param0(0).param1(InterfaceID.GeOffers.COLLECTALL)
            .itemId(-1).forceLeftClick(false), bounds,
            () -> copilotFunds.current(flippingCopilot, manager, suggestion)
                && readUi(() -> bounds.equals(completedWaitCollectBounds(manager, controller, suggestion))))) return false;
        lastActionTime = System.currentTimeMillis();
        actionCooldown = DEFAULT_ACTION_COOLDOWN;
        log.info("Funds recovery: collecting completed Grand Exchange offers.");
        return true;
    }

    /** ClientThread only. Rechecked after movement before the native collection operation. */
    Rectangle completedWaitCollectBounds(Object manager, Object controller, Object suggestion) {
        if (manager == null || manager != suggestionManager || controller != copilotController()
            || suggestion == null || suggestion != getSuggestion(manager)
            || state != State.MONITORING_COPILOT || !isRunning() || !Microbot.isLoggedIn()
            || Thread.currentThread().isInterrupted() || FlipperRunGuard.scriptsPaused()
            || InputArbiter.isHuman() || finishSession.isRequested()
            || blockedSlotActionKey != null || flippingCopilot == null
            || Microbot.getPluginManager() == null
            || !Microbot.getPluginManager().isPluginActive(flippingCopilot)
            || !Microbot.getPluginManager().isPluginEnabled(flippingCopilot)
            || !WaitingMouse.copilotWaiting(manager, controller)) return null;
        GeUiState ui = readGeUi();
        if (!ui.exchangeOpen || !ui.overviewOpen || ui.offerOpen || ui.bankOpen
            || Microbot.getClient().isMenuOpen() || Microbot.targetMenu != null
            || Microbot.getClient().getVarcIntValue(5) != 0
            || GeWarningDialog.hasVisibleContent(Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP))) return null;
        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        if (!CompletedOfferRecovery.hasCompletedOffers(offers)) return null;
        FinishOffers inventory = FinishOffers.capture(offers,
            Microbot.getClient().getItemContainer(InventoryID.INVENTORY));
        if (inventory == null || inventory.emptyInventorySlots == 0) return null;
        return FinishCollect.bounds(Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL),
            Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
    }

    private void collectFinishOffers() {
        int op = finishCollectOperation();
        Rectangle bounds = finishCollectBounds();
        FinishOffers offers = readFinishOffers();
        if (op <= 0 || bounds == null || offers == null || offers.emptyInventorySlots == 0) {
            slotActionStatus("Finish: cannot collect into inventory. Make space or collect manually, then resume.");
            return;
        }
        if (Thread.currentThread().isInterrupted() || !isRunning() || !finishSession.isActive()
            || !finishSession.readyForCancellation()
            || FlipperRunGuard.scriptsPaused() || InputArbiter.isHuman()
            || !bounds.equals(finishCollectBounds())) return;
        if (!invokeTradingMouse(new NewMenuEntry().option("Collect to inventory").target("")
            .identifier(op).type(MenuAction.CC_OP).param0(0).param1(InterfaceID.GeOffers.COLLECTALL)
            .itemId(-1).forceLeftClick(false), bounds)) return;
        slotActionStatus = "";
        lastActionTime = System.currentTimeMillis();
        actionCooldown = DEFAULT_ACTION_COOLDOWN;
    }

    /**
     * Log-only. Once a Finish has been stuck on the same unmet conditions for {@link #FINISH_WAIT_LOG_MS}, says what
     * it is waiting for, then repeats every {@link #FINISH_WAIT_LOG_MS}. A Finish that moves on, or has nothing
     * left to wait for, logs nothing. It never changes what Finish does and never throws.
     */
    void logFinishWait(FinishSession.CompletionSnapshot snapshot, long now) {
        try {
            String unmet = FinishSession.describeUnmet(snapshot, finishSession.waitsForAllSales());
            if (unmet.isEmpty() || !unmet.equals(finishWaitUnmet)) {
                finishWaitUnmet = unmet;
                finishWaitSince = now;
                finishWaitLoggedAt = now;
                return;
            }
            if (now - finishWaitLoggedAt < FINISH_WAIT_LOG_MS) return;
            finishWaitLoggedAt = now;
            diagnostics.warn("Finish: still waiting for {} ({} s so far). {}", unmet,
                (now - finishWaitSince) / 1000, finishSession.status());
        } catch (RuntimeException diagnosticFailure) {
            // A diagnostic must never get in the way of a Finish.
        }
    }

    /** Returns true when Finish owns this tick; normal sell suggestions may otherwise proceed. */
    private boolean processFinish(GeUiState ui) {
        if (isFinishComplete()) return true;
        finishComplete = false;
        if (!finishSession.isActive()
            && !finishSession.begin(flippingCopilot, copilotController(), suggestionManager)) return true;
        FinishOffers offers = readFinishOffers();
        Object suggestion = getSuggestion(suggestionManager);
        boolean allowed = finishPermitsTrade();
        boolean safeOverview = ui.overviewOpen && !ui.offerOpen && !ui.bankOpen
            && readUi(() -> !Microbot.getClient().isMenuOpen() && Microbot.targetMenu == null
                && Microbot.getClient().getVarcIntValue(5) == 0
                && !GeWarningDialog.hasVisibleContent(Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP)));
        boolean unlistedBypass = (unlistedWaitingSince > 0
            && System.currentTimeMillis() - unlistedWaitingSince > UNLISTED_STUCK_MS)
            || (lastSystemUpdateSeconds >= 0 && lastSystemUpdateSeconds <= SystemUpdate.STOP_WAITING_FOR_SALES_SECONDS);
        FinishSession.CompletionSnapshot ready = new FinishSession.CompletionSnapshot(safeOverview,
            offers != null && offers.buySlot < 0, offers != null && offers.noCollectables,
            offers != null && (offers.noUnlistedItems || unlistedBypass), offers != null && offers.noActiveSells,
            finishSession.healthyFreshWait(suggestion));
        if (!muleFinish && finishSession.completionReady(ready, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))
            && finishSession.healthyFreshWait(getSuggestion(suggestionManager))) {
            // Before an OSRS update: everything is listed and buys are cancelled, so the excess is liquid now.
            // Send it to the Mule, then come back here to stop. The Mule session owns the ticks meanwhile.
            if (updateMule == UpdateMule.AFTER_FINISH && muleBeforeUpdateEnabled()) startUpdateMule();
            if (updateMule == UpdateMule.RUNNING) return true;
            completedFinishGeneration = finishSession.generation();
            finishComplete = true;
            log.info("Finish: items are listed and current sell suggestions are complete. Stopping GE Flipper.");
            Runnable callback = finishCallback;
            if (callback != null) callback.run();
            return true;
        }
        if (offers == null) {
            slotActionStatus("Finish: offer or inventory data is unavailable. Waiting for a verified state.");
            return true;
        }
        if (!muleFinish) logFinishWait(ready, System.currentTimeMillis());
        if (!finishSession.readyForCancellation()) return true;
        if (muleFinish && safeOverview && !offers.noCollectables) {
            if (System.currentTimeMillis() - lastActionTime >= actionCooldown) collectFinishOffers();
            return true;
        }
        if (muleFinish && !muleSession.liquidationRequested()) return true;
        if (ui.offerOpen && (offers.buySlot >= 0 || !allowed)) {
            // Back also dismisses an active GE input prompt. ESC without a prompt
            // can close the exchange before the following mouse press reaches Back.
            backToOverview();
            return true;
        }
        if (safeOverview && offers.buySlot >= 0) {
            if (System.currentTimeMillis() - lastActionTime >= actionCooldown) cancelFinishBuy(offers.buySlot);
            return true;
        }
        if (safeOverview && !offers.noCollectables) {
            if (System.currentTimeMillis() - lastActionTime >= actionCooldown) collectFinishOffers();
            return true;
        }
        if (safeOverview && !offers.noUnlistedItems && finishSession.healthyFreshWait(suggestion)) {
            if (unlistedWaitingSince == 0) {
                unlistedWaitingSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - unlistedWaitingSince > UNLISTED_STUCK_MS) {
                slotActionStatus("Finish: items remain unlisted after waiting. Sell them manually or check Copilot.");
            } else {
                slotActionStatus("Finish: Copilot is waiting while items remain. Check its item strategy or sell them manually.");
            }
            return true;
        } else {
            unlistedWaitingSince = 0;
        }
        // Allow exchange/bank recovery, but never act on a stale BUY or MODIFY_BUY.
        if ((safeOverview || ui.offerOpen) && !allowed) return true;
        if (allowed) slotActionStatus = "";
        return false;
    }

	@Override
	public void shutdown()
	{
        // Cancel before clearing. Clearing alone dropped the reference and left the old
        // schedule running, so every break-restart added another live instance.
        for (java.util.concurrent.ScheduledFuture<?> f : LIVE_FUTURES) {
            try {
                f.cancel(true);
            } catch (Exception ignored) {
            }
        }
        if (mainScheduledFuture != null) {
            try {
                mainScheduledFuture.cancel(true);
            } catch (Exception ignored) {
            }
        }

        LIVE_FUTURES.clear();
        finishSession.close();
        muleSession.reset();
        muleLedger.flush();
        closeMuleConnection();
        observedMuleMode = null;
        receiverFinishRequested = false;
        receiverFinishProfileInterrupted = false;
        secondScreenAfk.reset();
        muleFinish = false;
        unlistedWaitingSince = 0;
        finishWaitUnmet = "";
        systemUpdateHandled = false;
        lastSystemUpdateSeconds = -1;
        updateMule = UpdateMule.NONE;
        if (!finishSession.status().isEmpty()) {
            log.warn("Finish stopped: Copilot's temporary sell-only mode could not be restored. Check Copilot manually.");
        }
        finishCallback = null;
        finishComplete = false;
        clearCachedTradingState();
		lastActionTime = 0;
		actionCooldown = DEFAULT_ACTION_COOLDOWN;
        if (scheduledFuture != null) scheduledFuture.cancel(true);
        // The base shutdown also resets client-wide pause/path/combat state. Only remove our heartbeat.
        ScriptHeartbeatRegistry.remove(getClass().getName());
	}

	private boolean initialize()
	{
		if (flippingCopilot != null && suggestionManager != null && highlightController != null) {
			return true;
		}

		Plugin _flippingCopilot = getFlippingCopilot();
		Object _suggestionManager = getSuggestionManager(_flippingCopilot);
		Object _highlightController = getHighlightController(_flippingCopilot);

        if (!Microbot.isLoggedIn()) {
            clearCachedTradingState();
            return false;
        }

		if (_flippingCopilot != null && _suggestionManager != null && _highlightController != null) {
			return true;
		}
		return false;
	}

	private Plugin getFlippingCopilot()
	{
		if (flippingCopilot == null)
		{
			flippingCopilot = Microbot.getPluginManager()
				.getPlugins()
				.stream()
				.filter(plugin -> {
					// Flip Assist and Flipping Copilot expose the same surface; drive whichever one is loaded.
					String simpleName = plugin.getClass().getSimpleName();
					return simpleName.equalsIgnoreCase("FlippingCopilotPlugin")
						|| simpleName.equalsIgnoreCase("FlipAssistPlugin");
				})
				.findFirst()
				.orElse(null);
		}
		return flippingCopilot;
	}

	private Object getHighlightController(Plugin flippingCopilot)
	{
		if (flippingCopilot == null) return null;
		if (highlightController == null)
		{
			try
			{
				Field highlightControllerField = flippingCopilot.getClass().getDeclaredField("highlightController");
				highlightControllerField.setAccessible(true);
				highlightController = highlightControllerField.get(flippingCopilot);
			}
			catch (Exception e)
			{
				log.error("Could not access HighlightController: {} - ", e.getClass().getSimpleName());
			}
		}
		return highlightController;
	}

	private Object getSuggestionManager(Plugin flippingCopilot)
	{
		if (flippingCopilot == null) return null;
		if (suggestionManager == null)
		{
			try
			{
				Field suggestionManagerField = flippingCopilot.getClass().getDeclaredField("suggestionManager");
				suggestionManagerField.setAccessible(true);
				suggestionManager = suggestionManagerField.get(flippingCopilot);
			}
			catch (Exception e)
			{
				log.error("Could not access SuggestionManager: {} - ", e.getClass().getSimpleName());
			}
		}
		return suggestionManager;
	}

    private <T> T readUi(Callable<T> read) {
        return GeUiState.requireRead(Microbot.getClientThread().runOnClientThreadOptional(
            () -> Optional.ofNullable(read.call())));
    }

    private boolean canRunWithoutChangingSettings() {
        return FlipperRunGuard.canRun(new FlipperRunGuard.Context() {
            public void heartbeat() { ScriptHeartbeatRegistry.recordHeartbeat(FlipperScript.this.getClass().getName()); }
            public boolean loggedIn() {
                boolean loggedIn = Microbot.isLoggedIn();
                if (!loggedIn) clearCachedTradingState();
                return loggedIn;
            }
            public boolean fatigueActive() { return SessionFatigue.isActive(); }
            public void startFatigueSession() { SessionFatigue.startSession(); }
            public boolean tutorialComplete() { return readUi(Rs2Player::hasCompletedTutorialIsland); }
            public boolean blockingEvent() { return Microbot.getBlockingEventManager().shouldBlockAndProcess(); }
            public boolean humanInput() { return InputArbiter.isHuman(); }
            public void releaseHeldKeys() { Rs2Keyboard.releaseHeldKeys(); }
            public boolean paused() { return FlipperRunGuard.scriptsPaused(); }
            public boolean interrupted() { return Thread.currentThread().isInterrupted(); }
        });
    }

    private GeUiState readGeUi() {
        return readUi(() -> GeUiState.capture(
            Microbot.getClient().getWidget(InterfaceID.GeOffers.CONTENTS),
            Microbot.getClient().getWidget(InterfaceID.GeOffers.INDEX),
            Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP),
            Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS),
            Microbot.getClient().getWidget(12, 1)));
    }

    private boolean isExchangeOpen() { return readGeUi().exchangeOpen; }
    private boolean isBankOpen() { return readGeUi().bankOpen; }
    private boolean isOfferScreenOpen() { return readGeUi().offerOpen; }

    boolean isUiWidgetVisible(int id) {
        return readUi(() -> {
            Widget widget = Microbot.getClient().getWidget(id);
            return widget != null && !widget.isHidden();
        });
    }

    private Widget findUiWidget(String text) { return findUiWidget(text, null, false); }

    Widget findUiWidget(String text, List<Widget> children, boolean exact) {
        return readUi(() -> Rs2Widget.findWidget(text, children, exact));
    }

    boolean waitForUi(BooleanSupplier condition) { return waitForUi(condition, 5000); }

    boolean waitForUi(BooleanSupplier condition, int timeoutMs) {
        return GeUiState.waitForUi(condition, timeoutMs, (poll, timeout) -> sleepUntil(poll, timeout));
    }

    void suspendForUnavailableUi() {
        resetWaitingMouse();
        geClosedSince = strayPageSince = offerScreenOpenTime = 0;
        offerScreenActionCount = 0;
    }

    void clearCachedTradingState() {
        if (finishSession.isRequested()) finishSession.suspend();
        completedOfferRecovery.reset();
        copilotFunds.reset();
        tradingGeneration++;
        flippingCopilot = null;
        suggestionManager = null;
        highlightController = null;
        blockedSlotActionKey = null;
        slotActionStatus = "";
        suspendForUnavailableUi();
    }

    void resetWaitingMouse() {
        waitingMouse.reset();
    }

    void invalidateMouseMovement() {
        tradingGeneration++;
        resetWaitingMouse();
    }

    private boolean randomizeMouseSpeed() {
        return config != null && config.randomizeMouseSpeed();
    }

    /** Cached checks only: never request ClientThread while a gesture owns the input loop. */
    private BooleanSupplier tradingClickGuard() {
        long generation = tradingGeneration;
        return () -> generation == tradingGeneration && isRunning()
            && !Thread.currentThread().isInterrupted() && !FlipperRunGuard.scriptsPaused()
            && !InputArbiter.isHuman() && Microbot.isLoggedIn();
    }

    private BooleanSupplier mouseMovementGuard() {
        BooleanSupplier guard = tradingClickGuard();
        return () -> guard.getAsBoolean() && randomizeMouseSpeed();
    }

    private net.runelite.api.Point tradingMousePoint(Rectangle bounds) {
        return readUi(() -> bounds != null && bounds.width > 0 && bounds.height > 0
            && Rs2UiHelper.isRectangleWithinCanvas(bounds)
            ? Rs2UiHelper.getClickingPoint(bounds, true) : null);
    }

    private boolean clickTradingMouse(Rectangle bounds, BooleanSupplier validation) {
        BooleanSupplier guard = tradingClickGuard();
        net.runelite.api.Point point = tradingMousePoint(bounds);
        return point != null && mouseMotion.clickValidated(point, randomizeMouseSpeed(), guard, validation);
    }

    boolean clickTradingWidget(Widget widget) {
        BooleanSupplier guard = tradingClickGuard();
        PromptClick target = readUi(() -> PromptClick.capture(widget));
        net.runelite.api.Point point = tradingMousePoint(target == null ? null : target.bounds);
        return point != null && mouseMotion.clickValidated(point, randomizeMouseSpeed(), guard,
            () -> readUi(target::matches));
    }

    /** ClientThread only. A reused chatbox position cannot authorize a different prompt. */
    static final class PromptClick {
        private final Widget widget;
        private final Rectangle bounds;
        private final String text;
        private final String[] actions;

        private PromptClick(Widget widget) {
            this.widget = widget;
            bounds = new Rectangle(widget.getBounds());
            text = widget.getText();
            String[] current = widget.getActions();
            actions = current == null ? null : current.clone();
        }

        static PromptClick capture(Widget widget) {
            if (widget == null || widget.isHidden() || widget.getBounds() == null) return null;
            PromptClick target = new PromptClick(widget);
            return target.matches() ? target : null;
        }

        boolean matches() {
            if (widget.isHidden() || !bounds.equals(widget.getBounds())
                || !Objects.equals(text, widget.getText()) || !Arrays.equals(actions, widget.getActions())) return false;
            Widget live = Microbot.getClient().getWidget(widget.getId());
            int index = widget.getIndex();
            return index < 0 ? live == widget : live != null && widget.getParent() == live
                && live.getChild(index) == widget && !live.isHidden();
        }
    }

    private boolean invokeTradingMouse(NewMenuEntry entry, Rectangle bounds) {
        return invokeTradingMouse(entry, bounds, () -> true);
    }

    private boolean invokeTradingMouse(NewMenuEntry entry, Rectangle bounds, BooleanSupplier validation) {
        BooleanSupplier guard = tradingClickGuard();
        net.runelite.api.Point point = tradingMousePoint(bounds);
        return point != null && mouseMotion.invokeValidated(entry, point, randomizeMouseSpeed(), guard, validation);
    }

    final class KeyboardTarget {
        private final BooleanSupplier guard = tradingClickGuard();
        private final PromptClick prompt;
        private final Plugin copilot = flippingCopilot;
        private final Object manager = suggestionManager;
        private final Object controller = copilotController();
        private final Object suggestion = getSuggestion(manager);
        private final String suggestionKey = keyboardSuggestionKey(suggestion);
        private final boolean numeric;
        private final int inputMode;
        private final int itemId;
        private final int selectedSlot;

        private KeyboardTarget(PromptClick prompt, boolean numeric) {
            this.prompt = prompt;
            this.numeric = numeric;
            inputMode = Microbot.getClient().getVarcIntValue(5);
            itemId = Microbot.getClient().getVarpValue(1151);
            selectedSlot = Microbot.getClient().getVarbitValue(VarbitID.GE_SELECTEDSLOT);
        }

        /** ClientThread only, including deferred Copilot callbacks. */
        private boolean matches() {
            return guard.getAsBoolean() && copilot == flippingCopilot && manager == suggestionManager
                && controller == copilotController() && suggestion != null && suggestion == getSuggestion(manager)
                && suggestionKey != null && suggestionKey.equals(keyboardSuggestionKey(suggestion))
                && copilotTradingHealthy() && finishPermitsTrade() && prompt.matches()
                && selectedSlot > 0 && selectedSlot <= grandExchangeSlotIds.length
                && (!numeric || (inputMode == 7 || inputMode == 30) && keyboardItemMatches(suggestion, itemId))
                && Microbot.getClient().getVarcIntValue(5) == inputMode
                && Microbot.getClient().getVarpValue(1151) == itemId
                && Microbot.getClient().getVarbitValue(VarbitID.GE_SELECTEDSLOT) == selectedSlot
                && readGeUi().offerOpen;
        }
    }

    private String keyboardSuggestionKey(Object suggestion) {
        if (suggestion == null) return null;
        try { return slotActionKey(suggestion); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private boolean keyboardItemMatches(Object suggestion, int itemId) {
        if (suggestion == null || itemId <= 0) return false;
        try { return Integer.valueOf(itemId).equals(suggestion.getClass().getMethod("getItemId").invoke(suggestion)); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    KeyboardTarget captureKeyboardTarget(Widget prompt) {
        return captureKeyboardTarget(prompt, true);
    }

    private KeyboardTarget captureKeyboardTarget(Widget prompt, boolean numeric) {
        return readUi(() -> {
            PromptClick captured = PromptClick.capture(prompt);
            if (captured == null) return null;
            KeyboardTarget target = new KeyboardTarget(captured, numeric);
            return target.matches() ? target : null;
        });
    }

    private long copilotButtonValue(Widget button, KeyboardTarget target) {
        return readUi(() -> {
            if (button == null || !target.matches() || button.isHidden()) return -1L;
            String text = button.getText();
            if (text == null) return -1L;
            String digits = text.replaceAll("[^0-9]", "");
            try { return digits.isEmpty() ? -1L : Long.parseLong(digits); }
            catch (NumberFormatException unavailable) { return -1L; }
        });
    }

    private boolean keyboardTargetValid(KeyboardTarget target) {
        return target != null && target.guard.getAsBoolean() && readUi(target::matches);
    }

    private boolean pressTradingKey(KeyboardTarget target, int key) {
        if (!keyboardTargetValid(target)) return false;
        dispatchTradingKey(key);
        return true;
    }

    private boolean typeTradingText(KeyboardTarget target, String text) {
        for (int index = 0; index < text.length(); index++) {
            if (!keyboardTargetValid(target)) return false;
            dispatchTradingCharacter(text.charAt(index));
            sleep(100, 200);
        }
        return keyboardTargetValid(target);
    }

    void dispatchTradingKey(int key) { Rs2Keyboard.keyPress(key); }
    void dispatchTradingCharacter(char key) { Rs2Keyboard.keyPress(key); }
    void pauseBeforeTradingKey() { sleep(KEY_PRESS_DELAY_MIN, KEY_PRESS_DELAY_MAX); }

    private void moveWaitingMouseOffScreen() {
        if (!randomizeMouseSpeed()) {
            Rs2Antiban.moveMouseOffScreen();
            return;
        }
        long waitingGeneration = waitingMouse.generation();
        BooleanSupplier guard = mouseMovementGuard();
        Object manager = suggestionManager;
        Object controller = copilotController();
        Dimension canvas = readUi(() -> new Dimension(Microbot.getClient().getCanvasWidth(),
            Microbot.getClient().getCanvasHeight()));
        if (canvas.width <= 0 || canvas.height <= 0) return;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int side = random.nextInt(4);
        int x = side == 0 ? -1 : side == 1 ? canvas.width + 1 : random.nextInt(canvas.width);
        int y = side == 2 ? -1 : side == 3 ? canvas.height + 1 : random.nextInt(canvas.height);
        mouseMotion.offscreen(new net.runelite.api.Point(x, y), true,
            () -> guard.getAsBoolean() && waitingGeneration == waitingMouse.generation()
                && config.waitingMouseOffScreen() && waitingMouseFrequency(config) > 0
                && state == State.MONITORING_COPILOT
                && manager == suggestionManager && WaitingMouse.copilotWaiting(manager, controller));
    }

    private boolean moveMouseWhileWaiting() {
        return waitingMouse.tick(config != null && config.waitingMouseOffScreen(),
            waitingMouseFrequency(config),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), new WaitingMouse.Context() {
                public boolean waiting() { return isSafeMouseWait(); }
                public boolean insideCanvas() {
                    return readUi(() -> WaitingMouse.insideCanvas(Microbot.getMouse().getMousePosition(),
                        PointerState.isOutside(), Microbot.getClient().getCanvasWidth(),
                        Microbot.getClient().getCanvasHeight()));
                }
                public boolean moveOffScreen() {
                    moveWaitingMouseOffScreen();
                    boolean outside = readUi(() -> {
                        java.awt.Point point = Microbot.getMouse().getMousePosition();
                        int width = Microbot.getClient().getCanvasWidth();
                        int height = Microbot.getClient().getCanvasHeight();
                        return point != null && width > 0 && height > 0
                            && !WaitingMouse.insideCanvas(point, PointerState.isOutside(), width, height);
                    });
                    if (outside) log.info("Waiting mouse: cursor is outside the game canvas.");
                    else log.debug("Waiting mouse: off-screen movement requested; exit not yet observed.");
                    return outside;
                }
            });
    }

    private boolean isSafeMouseWait() {
        if (config == null || !config.waitingMouseOffScreen() || waitingMouseFrequency(config) <= 0
            || state != State.MONITORING_COPILOT
            || Thread.currentThread().isInterrupted() || FlipperRunGuard.scriptsPaused()
            || InputArbiter.isHuman() || !Microbot.isLoggedIn() || Microbot.naturalMouse == null
            || flippingCopilot == null || Microbot.getPluginManager() == null
            || !Microbot.getPluginManager().isPluginActive(flippingCopilot)
            || !Microbot.getPluginManager().isPluginEnabled(flippingCopilot)) return false;
        return readUi(() -> {
            if (!Microbot.isLoggedIn() || FlipperRunGuard.scriptsPaused() || InputArbiter.isHuman()
                || !config.waitingMouseOffScreen() || waitingMouseFrequency(config) <= 0) return false;
            GeUiState ui = readGeUi();
            if (!ui.exchangeOpen || !ui.overviewOpen || ui.offerOpen || ui.bankOpen
                || Microbot.getClient().isMenuOpen() || Microbot.targetMenu != null
                || Microbot.getClient().getVarcIntValue(5) != 0
                || GeWarningDialog.hasVisibleContent(Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP))
                || blockedSlotActionKey != null || !slotActionStatus.isEmpty()) return false;
            List<Object> highlights = getHighlightOverlays(highlightController);
            if (highlights == null || !highlights.isEmpty()) return false;
            try {
                Field controller = flippingCopilot.getClass().getDeclaredField("suggestionController");
                controller.setAccessible(true);
                return WaitingMouse.copilotWaiting(suggestionManager, controller.get(flippingCopilot));
            } catch (ReflectiveOperationException unavailable) {
                return false;
            }
        });
    }

    private void moveMouseOffScreenForAfk() {
        try {
            boolean inside = readUi(() -> WaitingMouse.insideCanvas(Microbot.getMouse().getMousePosition(),
                PointerState.isOutside(), Microbot.getClient().getCanvasWidth(),
                Microbot.getClient().getCanvasHeight()));
            if (!inside) return;
            if (!randomizeMouseSpeed()) {
                Rs2Antiban.moveMouseOffScreen();
                return;
            }
            BooleanSupplier guard = mouseMovementGuard();
            Dimension canvas = readUi(() -> new Dimension(Microbot.getClient().getCanvasWidth(),
                Microbot.getClient().getCanvasHeight()));
            if (canvas.width <= 0 || canvas.height <= 0) return;
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            int side = random.nextInt(4);
            int x = side == 0 ? -1 : side == 1 ? canvas.width + 1 : random.nextInt(canvas.width);
            int y = side == 2 ? -1 : side == 3 ? canvas.height + 1 : random.nextInt(canvas.height);
            mouseMotion.offscreen(new net.runelite.api.Point(x, y), true, guard);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isSafeSecondScreenWait() {
        if (config == null || config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF
            || state != State.MONITORING_COPILOT
            || Thread.currentThread().isInterrupted() || FlipperRunGuard.scriptsPaused()
            || InputArbiter.isHuman() || !Microbot.isLoggedIn()
            || flippingCopilot == null || Microbot.getPluginManager() == null
            || !Microbot.getPluginManager().isPluginActive(flippingCopilot)
            || !Microbot.getPluginManager().isPluginEnabled(flippingCopilot)) return false;
        return readUi(() -> {
            if (!Microbot.isLoggedIn() || FlipperRunGuard.scriptsPaused() || InputArbiter.isHuman()
                || config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF) return false;
            GeUiState ui = readGeUi();
            if (!ui.exchangeOpen || !ui.overviewOpen || ui.offerOpen || ui.bankOpen
                || Microbot.getClient().isMenuOpen() || Microbot.targetMenu != null
                || Microbot.getClient().getVarcIntValue(5) != 0
                || GeWarningDialog.hasVisibleContent(Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP))
                || blockedSlotActionKey != null || !slotActionStatus.isEmpty()) return false;
            List<Object> highlights = getHighlightOverlays(highlightController);
            if (highlights == null || !highlights.isEmpty()) return false;
            try {
                Field controller = flippingCopilot.getClass().getDeclaredField("suggestionController");
                controller.setAccessible(true);
                return WaitingMouse.copilotWaiting(suggestionManager, controller.get(flippingCopilot));
            } catch (ReflectiveOperationException unavailable) {
                return false;
            }
        });
    }

    boolean backToOverview() {
        Widget button = readUi(() -> Microbot.getClient().getWidget(30474244));
        Rectangle bounds = readUi(() -> button == null || button.isHidden() || button.getBounds() == null
            ? null : new Rectangle(button.getBounds()));
        net.runelite.api.Point point = tradingMousePoint(bounds);
        BooleanSupplier guard = tradingClickGuard();
        if (point == null || !readUi(() -> backTargetMatches(button, bounds, point, false))
            || !mouseMotion.move(point, randomizeMouseSpeed(), guard)) return false;

        java.util.concurrent.atomic.AtomicReference<TradingClickSafety.MenuSnapshot> menu =
            new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<NewMenuEntry> action =
            new java.util.concurrent.atomic.AtomicReference<>();
        if (!waitForUi(() -> !guard.getAsBoolean() || readUi(() -> {
            if (!backTargetMatches(button, bounds, point, true)) return false;
            net.runelite.api.MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(entries);
            if (captured == null || !backMenuMatches(entries[entries.length - 1], button)) return false;
            net.runelite.api.MenuEntry entry = entries[entries.length - 1];
            // Back is a native widget action. Preserve that operation if the game
            // hides the interface after validation but before processing the press.
            action.set(new NewMenuEntry().option(entry.getOption()).target(entry.getTarget())
                .identifier(entry.getIdentifier()).type(entry.getType()).param0(entry.getParam0())
                .param1(entry.getParam1()).itemId(entry.getItemId()));
            menu.set(captured);
            return true;
        }), 1200) || !guard.getAsBoolean() || menu.get() == null || action.get() == null) return false;
        if (!mouseMotion.invokeStationary(action.get(), point, guard, () -> readUi(() -> {
            if (!backTargetMatches(button, bounds, point, true)) return false;
            net.runelite.api.MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            return menu.get().matches(entries) && backMenuMatches(entries[entries.length - 1], button);
        }))) return false;
        log.info("Returning to GE overview.");
        if (!waitForUi(() -> !isOfferScreenOpen(), 2500)) return false;
		offerScreenOpenTime = 0;
		offerScreenActionCount = 0;
		lastActionTime = System.currentTimeMillis();
		actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
        return true;
    }

    /** ClientThread only; recovery is allowed while a fresh sell suggestion is still pending. */
    boolean backTargetMatches(Widget button, Rectangle bounds, net.runelite.api.Point point, boolean hovered) {
        if (button == null || bounds == null || point == null || button.getId() != 30474244 || button.isHidden()
            || Microbot.getClient().getWidget(30474244) != button || !bounds.equals(button.getBounds())
            || !bounds.contains(point.getX(), point.getY()) || Microbot.getClient().isMenuOpen()
            || Microbot.targetMenu != null) return false;
        Widget contents = Microbot.getClient().getWidget(InterfaceID.GeOffers.CONTENTS);
        Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
        Widget details = Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS);
        return contents != null && !contents.isHidden()
            && ((setup != null && !setup.isHidden()) || (details != null && !details.isHidden()))
            && (!hovered || point.equals(Microbot.getClient().getMouseCanvasPosition()));
    }

    static boolean backMenuMatches(net.runelite.api.MenuEntry entry, Widget button) {
        if (button == null || !TradingClickSafety.safeDefault(entry)
            || (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)
            || !"back".equals(plainMenuOption(entry.getOption()))
            || !TradingClickSafety.targetsWidget(entry, button.getId(), button.getIndex(), button.getItemId())) return false;
        String[] actions = button.getActions();
        int operation = entry.getIdentifier();
        return actions != null && operation > 0 && operation <= actions.length
            && "back".equals(plainMenuOption(actions[operation - 1]));
    }

	public boolean isSlotActionSwapEnabled() {
		if (flippingCopilot != null) {
			try {
				Field cfgField = flippingCopilot.getClass().getDeclaredField("config");
				cfgField.setAccessible(true);
				Object copilotConfig = cfgField.get(flippingCopilot);
				if (copilotConfig != null) {
					Method m = copilotConfig.getClass().getMethod("slotActionSwap");
					Object val = m.invoke(copilotConfig);
					if (val instanceof Boolean) {
						return (Boolean) val;
					}
				}
			} catch (Exception ignored) {}
		}
		try {
			if (Microbot.getConfigManager() != null) {
				String val = Microbot.getConfigManager().getConfiguration("flippingcopilot", "slotActionSwap");
				if (val != null) {
					return Boolean.parseBoolean(val);
				}
			}
		} catch (Exception ignored) {}
		return false;
	}

	private Widget getOfferScreenAbortButton() {
        return readUi(this::findOfferScreenAbortButton);
    }

    private Widget findOfferScreenAbortButton() {
        // Widget IDs alone are insufficient: this position can render another details control.
        Widget direct = Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS_GRAPHIC6);
        if (hasAbortAction(direct)) return direct;
        java.util.Map<Widget, String> actions = Rs2Widget.findWidgetsWithAction("Abort", InterfaceID.GE_OFFERS, false);
        if (actions != null) {
            for (Widget widget : actions.keySet()) {
                if (hasAbortAction(widget) && (widget.getId() >>> 16) == InterfaceID.GE_OFFERS) return widget;
            }
        }
        Widget details = Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS);
        if (details != null) {
            for (Widget[] children : new Widget[][]{details.getChildren(), details.getDynamicChildren()}) {
                if (children == null) continue;
                for (Widget child : children) if (hasAbortAction(child)) return child;
            }
        }
        return null;
    }

    static boolean hasAbortAction(Widget widget) {
        if (widget == null || widget.isHidden() || widget.getActions() == null) return false;
        return Arrays.stream(widget.getActions()).anyMatch(FlipperScript::isAbortOption);
    }

    private static boolean isAbortOption(String action) {
        if (action == null) return false;
        String plain = action.replaceAll("<[^>]*>", "").trim();
        return plain.equalsIgnoreCase("Abort") || plain.equalsIgnoreCase("Abort offer");
    }

    /** Called on ClientThread by each action validator. */
    private boolean slotOfferMatches(int boxId, int itemId) {
        net.runelite.api.GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        return offers != null && boxId >= 0 && boxId < offers.length
            && TradingClickSafety.activeOfferMatches(offers[boxId], itemId);
    }

    boolean abortOfferDetailsMatches(Object suggestion) {
        if (suggestion == null) return false;
        try {
            int boxId = (Integer) suggestion.getClass().getMethod("getBoxId").invoke(suggestion);
            int itemId = (Integer) suggestion.getClass().getMethod("getItemId").invoke(suggestion);
            return readUi(() -> TradingClickSafety.abortDetailsMatches(
                Microbot.getClient().getGrandExchangeOffers(),
                Microbot.getClient().getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1, boxId, itemId));
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    private boolean clickAbortOfferDetails(Object suggestion, Widget button) throws ReflectiveOperationException {
        String key = slotActionKey(suggestion);
        Rectangle bounds = readUi(() -> hasAbortAction(button) && button.getBounds() != null
            ? new Rectangle(button.getBounds()) : null);
        net.runelite.api.Point point = tradingMousePoint(bounds);
        if (point == null) return false;
        BooleanSupplier guard = tradingClickGuard();
        return mouseMotion.clickValidated(point, randomizeMouseSpeed(), guard, () ->
            waitForUi(() -> !guard.getAsBoolean() || readUi(() -> {
                if (!sameSlotSuggestion(key) || !abortOfferDetailsMatches(suggestion)
                    || !hasAbortAction(button) || button != findOfferScreenAbortButton()
                    || button.getBounds() == null || !button.getBounds().contains(point.getX(), point.getY())
                    || Microbot.getClient().isMenuOpen() || Microbot.targetMenu != null) return false;
                net.runelite.api.MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
                if (entries == null || entries.length == 0) return false;
                net.runelite.api.MenuEntry top = entries[entries.length - 1];
                return top != null && top.getType() == MenuAction.CC_OP
                    && TradingClickSafety.targetsWidget(top, button.getId(), button.getIndex(), button.getItemId())
                    && isAbortOption(top.getOption());
            }), 1200) && guard.getAsBoolean());
    }

	/**
	 * Poll for the abort button instead of checking once. The GE details screen renders a
	 * tick or two after the slot click, so a single instant lookup reports a false
	 * "button not found" and the script backs out of a screen it could have used.
	 */
	private Widget waitForOfferScreenAbortButton(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		Widget btn = getOfferScreenAbortButton();
		while (btn == null && System.currentTimeMillis() < deadline) {
			sleep(100, 200);
			btn = getOfferScreenAbortButton();
		}
		return btn;
	}

	/**
	 * True when Copilot has already moved past the abort we were asked to perform.
	 * Copilot drops the abort suggestion as soon as the abort registers, so if the
	 * current suggestion is no longer an abort there is nothing left to click and the
	 * missing button is expected - not a fault worth warning about.
	 */
	private boolean isAbortSuggestionSettled(Object previousSuggestion) {
		if (suggestionManager == null) return false;
		try {
			Object current = getSuggestion(suggestionManager);
			if (current == null) return true;
			Method isAbortMethod = current.getClass().getMethod("isAbortSuggestion");
			return !((Boolean) isAbortMethod.invoke(current));
		} catch (Exception e) {
			return false;
		}
	}

	private boolean hasChatboxInput() {
        return readUi(() -> {
            Widget inputWidget = Microbot.getClient().getWidget(162, 44);
            String text = inputWidget == null ? null : inputWidget.getText();
            if (text != null && !text.trim().isEmpty() && !text.trim().equals("*")) return true;
            String varcStr = Microbot.getClient().getVarcStrValue(359);
            return varcStr != null && !varcStr.trim().isEmpty() && !varcStr.trim().equals("*");
        });
	}

	private KeyManager getKeyManager() {
		if (keyManager != null) return keyManager;
		try {
			if (Microbot.getInjector() != null) {
				keyManager = Microbot.getInjector().getInstance(KeyManager.class);
			}
		} catch (Exception e) {
			log.warn("Could not get KeyManager: {}", e.getClass().getSimpleName());
		}
		return keyManager;
	}

	private static class ExtendedKeyEvent extends KeyEvent {
		private final int extCode;

		public ExtendedKeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar) {
			super(source, id, when, modifiers, keyCode, keyChar);
			this.extCode = keyCode;
		}

		@Override
		public int getExtendedKeyCode() {
			return extCode;
		}
	}

	private void triggerCopilotQuickSet(KeyboardTarget target) {
        if (!keyboardTargetValid(target)) return;
		int keyCode = KeyEvent.VK_E;
		int modifiers = 0;
		char keyChar = 'e';

		// Retrieve configured hotkey from Flipping Copilot if available
		if (flippingCopilot != null) {
			try {
				Field cfgField = flippingCopilot.getClass().getDeclaredField("config");
				cfgField.setAccessible(true);
				Object copilotConfig = cfgField.get(flippingCopilot);
				if (copilotConfig != null) {
					Method qkMethod = copilotConfig.getClass().getMethod("quickSetKeybind");
					Object keybindObj = qkMethod.invoke(copilotConfig);
					if (keybindObj instanceof net.runelite.client.config.Keybind) {
						net.runelite.client.config.Keybind kb = (net.runelite.client.config.Keybind) keybindObj;
						if (kb.getKeyCode() != KeyEvent.VK_UNDEFINED) {
							keyCode = kb.getKeyCode();
							modifiers = kb.getModifiers();
							keyChar = Character.toLowerCase((char) keyCode);
						}
					}
				}
			} catch (Exception ignored) {}
		}

		Canvas canvas = Microbot.getClient().getCanvas();

		// Dispatch ExtendedKeyEvent (with overridden getExtendedKeyCode) to KeyManager and Canvas
		Component source = canvas != null ? canvas : new Canvas();
		long now = System.currentTimeMillis();
		ExtendedKeyEvent pressEvent = new ExtendedKeyEvent(source, KeyEvent.KEY_PRESSED, now, modifiers, keyCode, keyChar);
		ExtendedKeyEvent releaseEvent = new ExtendedKeyEvent(source, KeyEvent.KEY_RELEASED, now + 30, modifiers, keyCode, keyChar);

		KeyManager km = getKeyManager();
		if (km != null && keyboardTargetValid(target)) {
			km.processKeyPressed(pressEvent);
			km.processKeyReleased(releaseEvent);
		}
		if (canvas != null && keyboardTargetValid(target)) {
			canvas.dispatchEvent(pressEvent);
			canvas.dispatchEvent(releaseEvent);
		}

		// 3. Trigger Copilot's handleKeybind on keyListener via ClientThread
		if (flippingCopilot != null) {
			try {
				Field khField = flippingCopilot.getClass().getDeclaredField("keybindHandler");
				khField.setAccessible(true);
				Object keybindHandler = khField.get(flippingCopilot);
				if (keybindHandler != null) {
					Field klField = keybindHandler.getClass().getDeclaredField("keyListener");
					klField.setAccessible(true);
					Object keyListener = klField.get(keybindHandler);
					if (keyListener != null) {
						for (Method m : keyListener.getClass().getDeclaredMethods()) {
							if (m.getName().equals("handleKeybind")) {
								m.setAccessible(true);
								long generation = tradingGeneration;
								Microbot.getClientThread().invokeLater(() -> {
									if (generation != tradingGeneration || !target.matches()) return;
									try {
										m.invoke(keyListener, true, false, false);
									} catch (Exception ex) {
										log.debug("handleKeybind invoke failed: {}", ex.getClass().getSimpleName());
									}
								});
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				log.debug("Could not trigger handleKeybind via reflection: {}", e.getClass().getSimpleName());
			}
		}
	}

	private void setCopilotChatboxValueDirectly(long val, KeyboardTarget target) {
		if (val <= 0 || !keyboardTargetValid(target)) return;

		// 1. OfferHandler in keybindHandler
		if (flippingCopilot != null) {
			try {
				Field khField = flippingCopilot.getClass().getDeclaredField("keybindHandler");
				khField.setAccessible(true);
				Object keybindHandler = khField.get(flippingCopilot);
				if (keybindHandler != null) {
					Field ohField = keybindHandler.getClass().getDeclaredField("offerHandler");
					ohField.setAccessible(true);
					Object offerHandler = ohField.get(keybindHandler);
					if (offerHandler != null) {
						for (Method m : offerHandler.getClass().getMethods()) {
							if (m.getName().equals("setChatboxValue") && m.getParameterCount() == 1) {
								final long v = val;
								long generation = tradingGeneration;
								Microbot.getClientThread().invokeLater(() -> {
									if (generation != tradingGeneration || !target.matches()) return;
									try {
										m.invoke(offerHandler, v);
									} catch (Exception ignored) {}
								});
								return;
							}
						}
					}
				}
			} catch (Exception e) {
				log.debug("Could not set chatbox value via offerHandler: {}", e.getClass().getSimpleName());
			}
		}
	}

	private Object getSuggestion(Object suggestionManager)
	{
		if (suggestionManager == null) return null;
		try
		{
			Field suggestionField = suggestionManager.getClass().getDeclaredField("suggestion");
			suggestionField.setAccessible(true);
			return suggestionField.get(suggestionManager);
		}
		catch (Exception e)
		{
			log.error("Could not access Suggestion: {} ", e.getClass().getSimpleName());
			return null;
		}
	}

	private Object getSuggestionType(Object suggestion)
	{
		if (suggestion == null) return null;
		try
		{
			Field typeField = suggestion.getClass().getDeclaredField("type");
			typeField.setAccessible(true);
			return typeField.get(suggestion);
		}
		catch (Exception e)
		{
			log.error("Could not access suggestion type: {} - ", e.getClass().getSimpleName());
			return null;
		}
	}

	private List<Object> getHighlightOverlays(Object highlightController)
	{
		if (highlightController == null) return null;
		try
		{
			Field highlightOverlaysField = highlightController.getClass().getDeclaredField("highlightOverlays");
			highlightOverlaysField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<Object> highlightOverlays = (List<Object>) highlightOverlaysField.get(highlightController);
			return highlightOverlays;
		}
		catch (Exception e)
		{
			log.error("Could not access highlight overlays: {} - ", e.getClass().getSimpleName());
			return null;
		}
	}

	public static class HighlightTarget {
		private final Widget widget;
		private final Rectangle relativeBounds;

		public HighlightTarget(Widget widget, Rectangle relativeBounds) {
			this.widget = widget;
			this.relativeBounds = relativeBounds;
		}

		public Widget getWidget() {
			return widget;
		}

		public Rectangle getRelativeBounds() {
			return relativeBounds;
		}

		public Rectangle getClickBounds() {
			if (widget == null) return null;
			Rectangle b = widget.getBounds();
			if (b == null) return null;
			if (relativeBounds == null) return b;
			return new Rectangle(b.x + relativeBounds.x, b.y + relativeBounds.y, relativeBounds.width, relativeBounds.height);
		}

		public boolean isConfirmTarget() {
			if (widget != null) {
				String text = widget.getText();
				if (text != null && text.contains("Confirm")) return true;
				String[] actions = widget.getActions();
				if (actions != null && Arrays.stream(actions).filter(Objects::nonNull).anyMatch(a -> a.contains("Confirm"))) {
					return true;
				}
			}
			if (relativeBounds != null && relativeBounds.width >= 120 && relativeBounds.height >= 30) {
				// Size alone does not identify the Confirm button: Copilot also highlights
				// wide chatbox widgets, and clicking one of those instead of Confirm leaves
				// the offer screen open until it times out ("offer screen did not close
				// after confirm"). The GE Confirm button lives on the offer screen
				// interface, so require that before trusting the size heuristic.
				if (widget == null || (widget.getId() >> 16) != InterfaceID.GE_OFFERS) {
					return false;
				}
				return true;
			}
			return false;
		}
	}

	private List<HighlightTarget> getHighlightTargets(Object highlightController) {
		List<HighlightTarget> targets = new ArrayList<>();
		if (highlightController == null) return targets;
		List<Object> highlightOverlays = getHighlightOverlays(highlightController);
		if (highlightOverlays == null) return targets;

		for (Object highlightOverlay : highlightOverlays) {
			if (highlightOverlay == null) continue;
			try {
				Field widgetField = highlightOverlay.getClass().getDeclaredField("widget");
				widgetField.setAccessible(true);
				Widget widget = (Widget) widgetField.get(highlightOverlay);
				if (widget == null) continue;

				Rectangle relativeBounds = null;
				try {
					Field relBoundsField = highlightOverlay.getClass().getDeclaredField("relativeBounds");
					relBoundsField.setAccessible(true);
					relativeBounds = (Rectangle) relBoundsField.get(highlightOverlay);
				} catch (NoSuchFieldException ignored) {}

				targets.add(new HighlightTarget(widget, relativeBounds));
			} catch (NoSuchFieldException ignored) {
			} catch (Exception e) {
				log.error("Could not get target from overlay: {} - ", e.getClass().getSimpleName());
			}
		}
		return targets;
	}

	private HighlightTarget getTargetFromOverlay(Object highlightController, String suggestionType) {
		List<HighlightTarget> targets = getHighlightTargets(highlightController);
		if (targets.isEmpty()) return null;

		if (Objects.equals(suggestionType, "abort") || Objects.equals(suggestionType, "modify")) {
			return targets.stream()
				.filter(t -> t.getWidget() != null)
				.filter(t -> Arrays.stream(grandExchangeSlotIds).anyMatch(id -> id == t.getWidget().getId()))
				.findFirst()
				.orElse(null);
		} else {
			return targets.stream()
				.filter(t -> t.getWidget() != null && isUiWidgetVisible(t.getWidget().getId()))
				.findFirst()
				.orElse(null);
		}
	}

	private List<Widget> getHighlightWidgets(Object highlightController) {
		List<HighlightTarget> targets = getHighlightTargets(highlightController);
		List<Widget> highlightWidgets = new ArrayList<>();
		for (HighlightTarget target : targets) {
			if (target.getWidget() != null) {
				highlightWidgets.add(target.getWidget());
			}
		}
		return highlightWidgets;
	}

	private Widget getWidgetFromOverlay(Object highlightController, String suggestionType) {
		HighlightTarget target = getTargetFromOverlay(highlightController, suggestionType);
		return target != null ? target.getWidget() : null;
	}

    private volatile String blockedSlotActionKey;
    /** The chat label Flip Assist uses for its suggested item in the GE search. */
    private static final String FlipAssistItemLabel = "Flip Assist item: ";

    /**
     * The line that selects the suggested item in the exchange search box. Copilot puts one there;
     * Flip Assist puts one there under its own label, so both are tried. The result is returned
     * rather than assigned to a shared local, because the callers capture that local in a lambda.
     */
    Widget findSuggestedItemWidget() {
        Widget widget = findUiWidget("Copilot item:", null, false);
        if (widget == null) {
            widget = findUiWidget(FlipAssistItemLabel, null, false);
        }
        return widget;
    }

    private volatile String slotActionStatus = "";
    private static final int SLOT_ACTION_SETTLE_MS = 3500;

    public enum SuggestedAction { NONE, ABORT, MODIFY }

    public static SuggestedAction classifySuggestion(boolean isAbort, boolean isModify, boolean slotActionSwap) {
        if (isAbort) return SuggestedAction.ABORT;
        if (isModify) return SuggestedAction.MODIFY;
        return SuggestedAction.NONE;
    }

    public String getSlotActionStatus() {
        if (muleSession.active() || (config != null && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER)) {
            return muleSession.status();
        }
        if (!slotActionStatus.isEmpty()) return slotActionStatus;
        if (muleFinish && !muleSession.status().isEmpty()) return muleSession.status();
        if (finishSession.isRequested()) return finishSession.status();
        if (config != null && config.muleMode() == FlipperConfig.MuleMode.FLIPPER_SENDER
            && !muleSession.status().isEmpty()) {
            return muleSession.status();
        }
        if (!secondScreenAfk.status().isEmpty()) return secondScreenAfk.status();
        return "";
    }

    private void slotActionStatus(String message) {
        if (!message.equals(slotActionStatus) && !message.isEmpty()) log.warn(message);
        slotActionStatus = message;
    }

    private String slotActionKey(Object suggestion) throws ReflectiveOperationException {
        StringBuilder key = new StringBuilder(config.slotAction().name())
            .append(':').append(isSlotActionSwapEnabled());
        for (String getter : new String[]{"getType", "getBoxId", "getItemId", "getName", "getPrice", "getQuantity"}) {
            key.append(':').append(suggestion.getClass().getMethod(getter).invoke(suggestion));
        }
        return key.toString();
    }

    // This guard runs before watchdogs, hotkeys and generic highlight clicks. A failed slot
    // action must not fall through to a second click via another path on the following tick.
    private boolean isSlotActionBlocked() {
        if (blockedSlotActionKey == null) return false;
        try {
            Object suggestion = getSuggestion(suggestionManager);
            if (suggestion == null || blockedSlotActionKey.equals(slotActionKey(suggestion))) return true;
            blockedSlotActionKey = null;
            slotActionStatus = "";
            geClosedSince = strayPageSince = offerScreenOpenTime = 0;
            offerScreenActionCount = 0;
            return false;
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private boolean sameSlotSuggestion(String key) {
        if (!finishPermitsTrade() || !copilotTradingHealthy()) return false;
        try {
            Object suggestion = getSuggestion(suggestionManager);
            return suggestion != null && key.equals(slotActionKey(suggestion));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private boolean isModifySetupOpen() {
        return readGeUi().setupOpen;
    }

    private net.runelite.api.Point slotActionPoint(int slotId, SlotActionExecutor.Action action) {
        return readUi(() -> {
            Widget slot = Microbot.getClient().getWidget(slotId);
            Widget button = slot == null ? null : slot.getChild(2);
            if (button == null || button.isHidden()) return null;
            if (!SlotActionExecutor.supportsAction(button.getActions(), action)) return null;
            Rectangle bounds = button.getBounds();
            if (bounds == null || bounds.width < 2 || bounds.height < 2
                || !Rs2UiHelper.isRectangleWithinCanvas(bounds)) return null;
            return new net.runelite.api.Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        });
    }

    private boolean isSlotDefaultAction(int slotId, SlotActionExecutor.Action action,
                                       net.runelite.api.Point point, String key, int boxId, int itemId) {
        return readUi(() -> {
            if (Microbot.naturalMouse == null || Microbot.targetMenu != null || Microbot.getClient().isMenuOpen()
                || !isSlotActionSwapEnabled() || !sameSlotSuggestion(key) || isOfferScreenOpen()
                || !slotOfferMatches(boxId, itemId)) return false;
            Widget slot = Microbot.getClient().getWidget(slotId);
            Widget button = slot == null ? null : slot.getChild(2);
            if (button == null || button.isHidden()) return false;
            Rectangle bounds = button.getBounds();
            // Owned gestures can update PointerState a frame before the client mouse position (#648).
            net.runelite.api.Point mouse = Microbot.getClient().getMouseCanvasPosition();
            if (!SlotActionExecutor.cursorOnSlotButton(mouse, point, bounds)
                && !SlotActionExecutor.cursorOnSlotButton(PointerState.get(), point, bounds)) return false;
            return SlotActionExecutor.matchesDefaultAction(
                Microbot.getClient().getMenu().getMenuEntries(), slotId, action);
        });
    }

    private boolean checkAndAbortOrModifyIfNeeded() {
        if (!isExchangeOpen() || isOfferScreenOpen()) return false;
        if (flippingCopilot == null || highlightController == null || suggestionManager == null) return false;
        try {
            Object suggestion = getSuggestion(suggestionManager);
            if (suggestion == null) {
                slotActionStatus = "";
                return false;
            }
            boolean abort = (Boolean) suggestion.getClass().getMethod("isAbortSuggestion").invoke(suggestion);
            boolean modify = (Boolean) suggestion.getClass().getMethod("isModifySuggestion").invoke(suggestion);
            if (!abort && !modify) {
                slotActionStatus = "";
                return false;
            }
            if (!copilotTradingHealthy()) return true;
            // Consume the tick even during cooldown: generic slot highlights must not bypass this handler.
            if (System.currentTimeMillis() - lastActionTime < actionCooldown) return true;
            final String key = slotActionKey(suggestion);
            int boxId = (Integer) suggestion.getClass().getMethod("getBoxId").invoke(suggestion);
            int itemId = (Integer) suggestion.getClass().getMethod("getItemId").invoke(suggestion);
            if (boxId < 0 || boxId >= grandExchangeSlotIds.length) {
                blockedSlotActionKey = key;
                slotActionStatus("Invalid Copilot slot. Refresh suggestions or restart GE Flipper.");
                return true;
            }
            // Copilot may retain an abort/modify suggestion after the server has settled it.
            if (!readUi(() -> slotOfferMatches(boxId, itemId))) return true;
            final int slotId = grandExchangeSlotIds[boxId];
            final SlotActionExecutor.Action action = abort
                ? SlotActionExecutor.Action.ABORT : SlotActionExecutor.Action.MODIFY;
            SlotActionExecutor.Result result = SlotActionExecutor.execute(config.slotAction(), action, slotId,
                new SlotActionExecutor.Ui() {
                    private final BooleanSupplier hoverGuard = tradingClickGuard();
                    private final Object copilot = flippingCopilot;
                    private final Object manager = suggestionManager;
                    private final Object controller = copilotController();
                    private PromptClick target;
                    public boolean slotSwapEnabled() { return isSlotActionSwapEnabled(); }
                    public net.runelite.api.Point actionPoint(int id, SlotActionExecutor.Action a) {
                        return readUi(() -> {
                            if (!slotOfferMatches(boxId, itemId)) return null;
                            Widget slot = Microbot.getClient().getWidget(id);
                            Widget button = slot == null ? null : slot.getChild(2);
                            target = PromptClick.capture(button);
                            if (target == null || !SlotActionExecutor.supportsAction(target.actions, a)
                                || target.bounds.width < 2 || target.bounds.height < 2
                                || !Rs2UiHelper.isRectangleWithinCanvas(target.bounds)) return null;
                            return new net.runelite.api.Point((int) target.bounds.getCenterX(),
                                (int) target.bounds.getCenterY());
                        });
                    }
                    public boolean hover(net.runelite.api.Point point) {
                        // Mouse.move dispatches a single jump. Follow a smooth path on this
                        // script thread; never block the client thread for mouse movement.
                        return mouseMotion.move(point, randomizeMouseSpeed(), hoverGuard);
                    }
                    private boolean targetStillValid(SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        return hoverGuard.getAsBoolean() && readUi(() -> controller != null
                            && copilot == flippingCopilot && manager == suggestionManager
                            && controller == copilotController() && sameSlotSuggestion(key)
                            && isSlotActionSwapEnabled() && readGeUi().overviewOpen && !isOfferScreenOpen()
                            && !Microbot.getClient().isMenuOpen() && Microbot.targetMenu == null
                            && slotOfferMatches(boxId, itemId) && target != null && target.matches()
                            && SlotActionExecutor.supportsAction(target.actions, a)
                            && target.bounds.contains(point.getX(), point.getY())
                            && point.equals(PointerState.get()));
                    }
                    public SlotActionExecutor.Readiness awaitDefaultAction(int id, SlotActionExecutor.Action a,
                                                                            net.runelite.api.Point point) {
                        return SlotActionReadiness.await(() -> targetStillValid(a, point),
                            () -> isSlotDefaultAction(id, a, point, key, boxId, itemId),
                            FlipperScript.this::waitForUi, System::nanoTime, 1800);
                    }
                    public boolean clickDefaultAction(int id, SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        // Hover already completed. A second movement could change the default action again.
                        return mouseMotion.clickStationary(point, hoverGuard,
                            () -> targetStillValid(a, point)
                                && isSlotDefaultAction(id, a, point, key, boxId, itemId));
                    }
                    public boolean invokeFallbackAction(int id, SlotActionExecutor.Action a,
                                                        net.runelite.api.Point point) {
                        boolean sent = mouseMotion.invokeStationary(new NewMenuEntry().option(a.option).target("")
                            .identifier(a.identifier).type(MenuAction.CC_OP).param0(2).param1(id)
                            .itemId(-1).forceLeftClick(false), point, hoverGuard,
                            () -> targetStillValid(a, point));
                        if (sent) log.info("Copilot left-click readiness timed out; used the verified {} slot operation.",
                            a.option);
                        return sent;
                    }
                    public boolean invokeAction(int id, SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        return mouseMotion.invokeValidated(new NewMenuEntry().option(a.option).target("")
                            .identifier(a.identifier).type(MenuAction.CC_OP).param0(2).param1(id)
                            .itemId(-1).forceLeftClick(false), point, randomizeMouseSpeed(), hoverGuard,
                            () -> sameSlotSuggestion(key) && !isOfferScreenOpen()
                                && readUi(() -> slotOfferMatches(boxId, itemId)) && point.equals(slotActionPoint(id, a)));
                    }
                });
            lastActionTime = System.currentTimeMillis();
            actionCooldown = DEFAULT_ACTION_COOLDOWN;
            if (handleSlotActionFailure(result, action, key)) return true;
            slotActionStatus = "";
            log.info("Executed {} on slot {} using {}.", action.option, boxId + 1, config.slotAction().actionDescription);
            if (abort) {
                // Dispatch alone is not server acceptance; allow settlement before another request.
                waitForUi(() -> !sameSlotSuggestion(key) || !readUi(() -> slotOfferMatches(boxId, itemId)),
                    SLOT_ACTION_SETTLE_MS);
            }
            if (modify && !waitForUi(this::isModifySetupOpen, SLOT_ACTION_SETTLE_MS)
                && sameSlotSuggestion(key)) {
                // An invoke/left click is not proof that setup opened. Back out once, then
                // hold this suggestion until it changes, the mode changes, or the plugin restarts.
                if (isOfferScreenOpen()) backToOverview();
                blockedSlotActionKey = key;
                slotActionStatus("Modify setup did not open. Check Copilot left-click swap or restart GE Flipper.");
            }
            lastActionTime = System.currentTimeMillis();
            actionCooldown = DEFAULT_ACTION_COOLDOWN;
            return true;
        } catch (ReflectiveOperationException e) {
            slotActionStatus("Copilot suggestion unavailable. Refresh suggestions or restart GE Flipper.");
            log.debug("Could not read Copilot slot suggestion: {}", e.getClass().getSimpleName());
            return true;
        }
    }
    /** Transient failures consume this tick without permanently blocking the suggestion. */
    boolean handleSlotActionFailure(SlotActionExecutor.Result result, SlotActionExecutor.Action action, String key) {
        if (result == SlotActionExecutor.Result.ACTED) return false;
        if (result == SlotActionExecutor.Result.SWAP_DISABLED) {
            blockedSlotActionKey = key;
            slotActionStatus("Enable Copilot slot swap, or set Copilot left-click swap to Off in GE Flipper to use Slot menu action.");
        } else if (result == SlotActionExecutor.Result.SLOT_UNAVAILABLE) {
            slotActionStatus("No supported " + action.option + " action is available. Waiting; if this persists, "
                + "refresh Copilot suggestions or handle the offer manually.");
        } else {
            slotActionStatus("Waiting for Copilot's " + action.option + " left-click action. Will retry.");
        }
        return true;
    }

    private boolean checkAndPressCopilotKeybind() {
        if (!finishPermitsTrade()) return false;
		// 1. Search for a widget with text "Copilot item" (if it's time to select the item suggestion in the buy item window)
        Widget copilotWidget = findSuggestedItemWidget();
        if (copilotWidget != null && isUiWidgetVisible(copilotWidget.getId())) {
			log.info("Found chat widget Copilot item '{}'.", copilotWidget.getId());
			if (isMouseMode()) {
				log.info("Selecting Copilot item via mouse click.");
				if (!clickTradingWidget(copilotWidget)) return true;
			}
            KeyboardTarget target = captureKeyboardTarget(copilotWidget, false);
            if (target == null) return true;
            if (!isMouseMode()) {
                log.info("Selecting Copilot item via hotkey (ENTER).");
                if (!pressTradingKey(target, KeyEvent.VK_ENTER)) return true;
            }

			// Wait for item selection widget to disappear (fallback to enter if still visible after mouse click)
			if (!waitForUi(() -> !isUiWidgetVisible(copilotWidget.getId()), 2000)) {
                if (!pressTradingKey(target, KeyEvent.VK_ENTER)) return true;
				if (!waitForUi(() -> !isUiWidgetVisible(copilotWidget.getId()), 1500)) {
					// Fallback to mouse click if ENTER failed
					if (isUiWidgetVisible(copilotWidget.getId())) {
						if (!clickTradingWidget(copilotWidget)) return true;
						waitForUi(() -> !isUiWidgetVisible(copilotWidget.getId()), 1500);
					}
				}
			}
			offerScreenActionCount++;
			lastActionTime = System.currentTimeMillis();
			actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
			return true;
        }

		if (System.currentTimeMillis() - lastActionTime < actionCooldown) return false;

		// If it's time to set price/quantity

		Widget setPriceWidget = findUiWidget("Set a price for each item:", null, false);
		Widget setQuantityWidget = findUiWidget("How many do you wish to ", null, false);

		boolean isPricePrompt = setPriceWidget != null && isUiWidgetVisible(setPriceWidget.getId());
		boolean isQuantityPrompt = setQuantityWidget != null && isUiWidgetVisible(setQuantityWidget.getId());

        if (isPricePrompt || isQuantityPrompt) {
			Widget promptWidget = isPricePrompt ? setPriceWidget : setQuantityWidget;
			log.info("Found chat widget ({}) '{}'.", isPricePrompt ? "price" : "quantity", promptWidget.getId());

			// 1. First attempt: Click Copilot's prompt button if visible, or press hotkey
			Widget copilotButton = findUiWidget("to set to Copilot", null, false);
			boolean copilotButtonVisible = copilotButton != null && isUiWidgetVisible(copilotButton.getId());

            long valFromButton = -1;

            KeyboardTarget target;
			if (isMouseMode()) {
				if (copilotButtonVisible) {
					log.info("Clicking Copilot prompt button '{}' via mouse.", copilotButton.getId());
					if (!clickTradingWidget(copilotButton)) return true;
                    target = captureKeyboardTarget(promptWidget);
                    if (target == null) return true;
                    valFromButton = copilotButtonValue(copilotButton, target);
				} else {
                    target = captureKeyboardTarget(promptWidget);
                    if (target == null) return true;
                    valFromButton = copilotButtonValue(copilotButton, target);
					log.info("Copilot prompt button not visible, falling back to hotkey [E].");
					triggerCopilotQuickSet(target);
				}
				waitForUi(this::hasChatboxInput, 1500);
			} else {
                target = captureKeyboardTarget(promptWidget);
                if (target == null) return true;
                valFromButton = copilotButtonValue(copilotButton, target);
				// Hotkey mode: Strictly use hotkey [E] without mouse clicks
				log.info("Selecting Copilot suggestion via hotkey [E].");
				triggerCopilotQuickSet(target);

				// If hotkey didn't populate within 600ms, set directly via Copilot offerHandler without mouse
				if (!waitForUi(this::hasChatboxInput, 600)) {
					if (valFromButton > 0) {
						log.info("Setting chatbox value directly from Copilot suggestion without mouse.");
						setCopilotChatboxValueDirectly(valFromButton, target);
						waitForUi(this::hasChatboxInput, 600);
					}
				}
			}

			// Fallback: If input is still not populated, extract suggestion value and set directly without typing
			if (!hasChatboxInput()) {
				long val = valFromButton;

				// If not found from button text, check currentSuggestion (ensuring it matches the offer screen item)
				if (val <= 0) {
					Object currentSuggestion = getSuggestion(suggestionManager);
					if (currentSuggestion != null) {
						try {
								int currentOfferItemId = readUi(() -> Microbot.getClient().getVarpValue(1151));
							Method getItemIdMethod = currentSuggestion.getClass().getMethod("getItemId");
							int suggestionItemId = (Integer) getItemIdMethod.invoke(currentSuggestion);
							if (currentOfferItemId <= 0 || currentOfferItemId == suggestionItemId) {
								if (isPricePrompt) {
									Method getPriceMethod = currentSuggestion.getClass().getMethod("getPrice");
									val = (Long) getPriceMethod.invoke(currentSuggestion);
								} else if (isQuantityPrompt) {
									Method getQuantityMethod = currentSuggestion.getClass().getMethod("getQuantity");
									val = (Integer) getQuantityMethod.invoke(currentSuggestion);
								}
							} else {
								log.warn("Suggestion item does not match the offer screen. Skipping suggestion value.");
							}
							} catch (GeUiState.UiUnavailable unavailable) {
                                throw unavailable;
                            } catch (Exception e) {
							log.error("Failed to read suggestion value: {}", e.getClass().getSimpleName());
						}
					}
				}

				if (val > 0) {
					log.info("Setting the suggested {} directly on client thread.", isPricePrompt ? "price" : "quantity");
					setCopilotChatboxValueDirectly(val, target);
					waitForUi(this::hasChatboxInput, 800);
				}

				// Fallback: If still not populated, type the value into chatbox
				if (!hasChatboxInput() && val > 0) {
					log.info("Entering the suggested {} into the active prompt.", isPricePrompt ? "price" : "quantity");
					if (!typeTradingText(target, String.valueOf(val))) return true;
					waitForUi(this::hasChatboxInput, 1000);
				}
			}

			// Check if chatbox input was successfully populated
			if (!hasChatboxInput()) {
				log.warn("Failed to populate {} input! Cancelling prompt with ESC to prevent chat spam and backing out to GE overview.",
					isPricePrompt ? "price" : "quantity");
                if (!pressTradingKey(target, KeyEvent.VK_ESCAPE)) return true;
				sleep(200, 400);
				backToOverview();
				return true;
			}

			// Submit the value
            pauseBeforeTradingKey();
            if (!pressTradingKey(target, KeyEvent.VK_ENTER)) return true;
			waitForUi(() -> !isUiWidgetVisible(promptWidget.getId()), 2500);
			offerScreenActionCount++;
			lastActionTime = System.currentTimeMillis();
			actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
			return true;
        }

		return false;
    }

    private boolean isGeWarningOpen() {
        return readUi(() -> GeWarningDialog.isWarningVisible(
            Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP)));
    }

    private Rectangle geWarningYesBounds() {
        return readUi(() -> {
            Widget button = GeWarningDialog.findYesButton(
                Microbot.getClient().getWidget(InterfaceID.GeOffers.POPUP));
            if (button == null) return null;
            Rectangle bounds = button.getBounds();
            return Rs2UiHelper.isRectangleWithinCanvas(bounds) ? new Rectangle(bounds) : null;
        });
    }

    private GeWarningDialog.Result confirmGeWarning() {
        GeWarningDialog.Result result = GeWarningDialog.confirm(new GeWarningDialog.Ui() {
            public boolean warningVisible() { return isGeWarningOpen(); }
            public Rectangle yesButtonBounds() { return geWarningYesBounds(); }
            public boolean click(Rectangle bounds) {
                if (Thread.currentThread().isInterrupted() || !FlipperScript.this.isRunning()
                    || !finishPermitsTrade()
                    || !bounds.equals(geWarningYesBounds())) return false;
                // Inspect on ClientThread, but move/click and wait on the script worker.
                return clickTradingMouse(bounds,
                    () -> finishPermitsTrade() && bounds.equals(geWarningYesBounds()));
            }
            public boolean awaitDismissal() {
                return waitForUi(() -> !isGeWarningOpen(), 1500);
            }
        });
        if (result != GeWarningDialog.Result.NO_DIALOG) {
            lastActionTime = System.currentTimeMillis();
            actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
            if (result == GeWarningDialog.Result.CONFIRMED) {
                offerScreenActionCount++;
                log.info("Grand Exchange warning confirmed; popup closed.");
            } else if (result == GeWarningDialog.Result.CLICK_FAILED
                || result == GeWarningDialog.Result.NOT_DISMISSED) {
                log.warn("Grand Exchange warning confirmation did not complete. Will retry the popup.");
            }
        }
        return result;
    }

    private boolean checkAndClickHighlightedWidgets()
	{
		if (!finishPermitsTrade()) return false;
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastActionTime < actionCooldown) return false;

		if (flippingCopilot == null || highlightController == null) return false;

		try {
			if (confirmGeWarning() != GeWarningDialog.Result.NO_DIALOG) return true;

			HighlightTarget target = getTargetFromOverlay(highlightController, "");
			if (target != null && target.getWidget() != null && isUiWidgetVisible(target.getWidget().getId())) {
				Widget highlightedWidget = target.getWidget();
				Rectangle clickBounds = target.getClickBounds();
				log.info("Processing highlighted target: widgetId={}, clickBounds={}, relativeBounds={}",
					highlightedWidget.getId(), clickBounds, target.getRelativeBounds());

				// Suggestions can change after the main handler checked them. Route slot
				// MODIFY/ABORT highlights through the same verified action path in either mode.
				boolean isSlotWidget = Arrays.stream(grandExchangeSlotIds).anyMatch(id -> id == highlightedWidget.getId());
				if (isSlotWidget && suggestionManager != null) {
					Object currentSuggestion = getSuggestion(suggestionManager);
					if (currentSuggestion != null) {
						boolean abort = (Boolean) currentSuggestion.getClass().getMethod("isAbortSuggestion").invoke(currentSuggestion);
						boolean modify = (Boolean) currentSuggestion.getClass().getMethod("isModifySuggestion").invoke(currentSuggestion);
						if (abort || modify) {
							checkAndAbortOrModifyIfNeeded();
							return true;
						}
					}
				}

				// If GE close button is highlighted (container 30474242 or close button dynamic child)
				if (highlightedWidget.getId() == 30474242 && isExchangeOpen()) {
					Rs2GrandExchange.closeExchange();
					waitForUi(() -> !isExchangeOpen(), 2500);
					lastActionTime = currentTime;
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
				// If Bank close button is highlighted (container 786434 or close button dynamic child)
				if (highlightedWidget.getId() == 786434 && isBankOpen()) {
					Rs2Bank.closeBank();
					waitForUi(() -> !isBankOpen(), 2500);
					lastActionTime = currentTime;
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
				// If GE is on offer screen and has "Too much money!" warning, back out immediately
				if (readGeUi().tooMuchMoney) {
					log.warn("Offer has 'Too much money!' error. Backing out to GE overview.");
					backToOverview();
					return true;
				}

				boolean isConfirm = target.isConfirmTarget();
				if (!finishPermitsTrade()) return true;

                if (!clickHighlightedTarget(target)) return true;
				Rs2Random.wait(100, 200);
				lastActionTime = currentTime;
				actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);

				if (isOfferScreenOpen()) {
					offerScreenActionCount++;
				}

				// If confirming an offer, dismiss any price warning dialog and wait for offer screen to close
				if (isConfirm) {
					log.info("Clicked Confirm button. Checking for warning dialog or waiting for offer screen to close...");
					if (waitForUi(this::isGeWarningOpen, 1200)) {
						GeWarningDialog.Result warning = confirmGeWarning();
						if (warning != GeWarningDialog.Result.CONFIRMED
							&& warning != GeWarningDialog.Result.NO_DIALOG) return true;
					}
					if (!waitForUi(() -> !isOfferScreenOpen(), 4000)) {
						log.warn("Offer screen did not close after confirm. Backing out to overview.");
						backToOverview();
						return false;
					}
					log.info("Offer placed successfully; offer screen closed.");
				}

				return true;
			}
		}
		catch (GeUiState.UiUnavailable unavailable) {
            throw unavailable;
        }
		catch (Exception e)
		{
			log.error("Could not process highlight widgets: {} - ", e.getClass().getSimpleName());
		}

		return false;
	}

    private boolean clickHighlightedTarget(HighlightTarget target) throws ReflectiveOperationException {
        Object controller = highlightController;
        Object manager = suggestionManager;
        Object suggestion = getSuggestion(manager);
        String key = suggestion == null ? null : slotActionKey(suggestion);
        Widget widget = target.getWidget();
        Rectangle bounds = readUi(() -> {
            Rectangle current = target.getClickBounds();
            return current == null ? null : new Rectangle(current);
        });
        net.runelite.api.Point point = tradingMousePoint(bounds);
        BooleanSupplier guard = tradingClickGuard();
        if (point == null || !mouseMotion.move(point, randomizeMouseSpeed(), guard)) return false;

        // The client rebuilds menus after hover. Capture the safe top entry, then validate
        // that exact entry and the live recommendation before pressing without moving again.
        java.util.concurrent.atomic.AtomicReference<TradingClickSafety.MenuSnapshot> menu =
            new java.util.concurrent.atomic.AtomicReference<>();
        if (!waitForUi(() -> !guard.getAsBoolean() || readUi(() -> {
            if (!highlightedTargetMatches(controller, manager, widget, bounds, key, point)) return false;
            net.runelite.api.MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(entries);
            if (captured == null || !highlightedMenuMatches(entries[entries.length - 1], widget, point)) return false;
            menu.set(captured);
            return true;
        }), 1200) || !guard.getAsBoolean() || menu.get() == null) return false;
        return mouseMotion.clickStationary(point, guard, () -> readUi(() -> {
            if (!highlightedTargetMatches(controller, manager, widget, bounds, key, point)) return false;
            net.runelite.api.MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            return menu.get().matches(entries)
                && highlightedMenuMatches(entries[entries.length - 1], widget, point);
        }));
    }

    /** ClientThread only: Copilot can highlight visual layers above native GE controls. */
    boolean highlightedMenuMatches(net.runelite.api.MenuEntry entry, Widget highlighted,
                                   net.runelite.api.Point point) {
        if (highlighted == null || point == null || !TradingClickSafety.safeDefault(entry)) return false;
        if (highlighted.getId() == InterfaceID.GeOffers.COLLECTALL && highlighted.getIndex() == 2)
            return nativeCollectAllMenuMatches(entry, highlighted, point);
        if (TradingClickSafety.targetsWidget(entry, highlighted.getId(), highlighted.getIndex(),
            highlighted.getItemId())) return true;
        if (nativeBuyMenuMatches(entry, highlighted, point)) return true;
        if (highlighted.getId() != InterfaceID.GeOffers.SETUP
            || (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)) return false;

        String expected;
        switch (highlighted.getIndex()) {
            case 54: expected = "enter price"; break;
            case 51: expected = "enter quantity"; break;
            case 50: expected = "all"; break;
            case 58: expected = "confirm"; break;
            default: return false;
        }
        if (!expected.equals(plainMenuOption(entry.getOption()))) return false;
        Widget parent = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
        if (parent == null || parent.isHidden() || parent.getChild(highlighted.getIndex()) != highlighted) return false;
        Widget action;
        if (highlighted.getIndex() == 58 && entry.getParam1() == InterfaceID.GeOffers.SETUP_CONFIRM) {
            // Copilot draws Confirm on a setup child; the native action is a
            // separate static widget. Resolve that exact control, not any overlap.
            action = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP_CONFIRM);
        } else {
            if (entry.getParam1() != InterfaceID.GeOffers.SETUP || entry.getParam0() < 0) return false;
            action = parent.getChild(entry.getParam0());
        }
        if (action == null || action.isHidden() || action.getId() != entry.getParam1()
            || action.getIndex() != entry.getParam0()) return false;
        Rectangle bounds = action.getBounds();
        String[] actions = action.getActions();
        int operation = entry.getIdentifier();
        return bounds != null && bounds.contains(point.getX(), point.getY())
            && actions != null && operation > 0 && operation <= actions.length
            && expected.equals(plainMenuOption(actions[operation - 1]));
    }

    /** ClientThread only: the empty-slot buy icon overlays its native Create Buy offer control. */
    private boolean nativeBuyMenuMatches(net.runelite.api.MenuEntry entry, Widget highlighted,
                                        net.runelite.api.Point point) {
        if (highlighted.getIndex() != 0 || entry.getParam0() != 3 || entry.getIdentifier() != 1
            || entry.getParam1() != highlighted.getId()
            || (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)
            || !"create buy offer".equals(plainMenuOption(entry.getOption()))) return false;
        int slot = -1;
        for (int index = 0; index < grandExchangeSlotIds.length; index++) {
            if (grandExchangeSlotIds[index] == highlighted.getId()) {
                slot = index;
                break;
            }
        }
        if (slot < 0) return false;
        Widget overview = Microbot.getClient().getWidget(InterfaceID.GeOffers.INDEX);
        Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
        Widget details = Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS);
        if (overview == null || overview.isHidden() || (setup != null && !setup.isHidden())
            || (details != null && !details.isHidden())) return false;
        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        if (offers == null || offers.length != grandExchangeSlotIds.length || offers[slot] == null
            || offers[slot].getState() != GrandExchangeOfferState.EMPTY) return false;
        Object suggestion = getSuggestion(suggestionManager);
        if (suggestion == null) return false;
        try {
            Object type = suggestion.getClass().getMethod("getType").invoke(suggestion);
            String name = type instanceof Enum<?> ? ((Enum<?>) type).name()
                : type instanceof String ? (String) type : "";
            if (!"buy".equals(name.toLowerCase(Locale.ROOT))) return false;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
        Widget parent = Microbot.getClient().getWidget(highlighted.getId());
        Widget action = parent == null ? null : parent.getChild(3);
        if (parent == null || parent.isHidden() || parent.getChild(0) != highlighted || highlighted.isHidden()
            || action == null || action.isHidden() || action.getId() != entry.getParam1()
            || action.getIndex() != entry.getParam0()) return false;
        Rectangle visualBounds = highlighted.getBounds();
        Rectangle actionBounds = action.getBounds();
        String[] actions = action.getActions();
        return visualBounds != null && visualBounds.contains(point.getX(), point.getY())
            && actionBounds != null && actionBounds.contains(point.getX(), point.getY())
            && actions != null && actions.length > 0
            && "create buy offer".equals(plainMenuOption(actions[0]));
    }

    /** ClientThread only: Copilot highlights Collect-all's label above the native inventory button. */
    private boolean nativeCollectAllMenuMatches(net.runelite.api.MenuEntry entry, Widget highlighted,
                                               net.runelite.api.Point point) {
        if (entry.getParam1() != InterfaceID.GeOffers.COLLECTALL || entry.getParam0() != 0
            || entry.getIdentifier() != 1 || entry.getType() != MenuAction.CC_OP
            || !"collect to inventory".equals(plainMenuOption(entry.getOption()))) return false;
        Widget overview = Microbot.getClient().getWidget(InterfaceID.GeOffers.INDEX);
        Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
        Widget details = Microbot.getClient().getWidget(InterfaceID.GeOffers.DETAILS);
        if (overview == null || overview.isHidden() || (setup != null && !setup.isHidden())
            || (details != null && !details.isHidden())) return false;
        Widget parent = Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL);
        Widget action = parent == null ? null : parent.getChild(0);
        if (parent == null || parent.getId() != InterfaceID.GeOffers.COLLECTALL || parent.isHidden()
            || parent.getChild(2) != highlighted || highlighted.isHidden()
            || action == null || action.isHidden() || action.getId() != entry.getParam1()
            || action.getIndex() != entry.getParam0()) return false;
        Rectangle visualBounds = highlighted.getBounds();
        Rectangle actionBounds = action.getBounds();
        String[] actions = action.getActions();
        return visualBounds != null && visualBounds.contains(point.getX(), point.getY())
            && actionBounds != null && actionBounds.contains(point.getX(), point.getY())
            && actions != null && actions.length > 0
            && "collect to inventory".equals(plainMenuOption(actions[0]));
    }

    private static String plainMenuOption(String option) {
        return option == null ? "" : option.replaceAll("<[^>]*>", "").replace('\u00a0', ' ')
            .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** ClientThread validation; no movement or input lock is held while this runs. */
    boolean highlightedTargetMatches(Object controller, Object manager, Widget widget, Rectangle bounds,
                                     String key, net.runelite.api.Point point) {
        if (controller != highlightController || manager != suggestionManager || widget == null
            || bounds == null || point == null || widget.isHidden() || !finishPermitsTrade()
            || Microbot.getClient().isMenuOpen() || Microbot.targetMenu != null) return false;
        try {
            Object suggestion = getSuggestion(manager);
            if (!Objects.equals(key, suggestion == null ? null : slotActionKey(suggestion))) return false;
            if (suggestion != null && Arrays.stream(grandExchangeSlotIds).anyMatch(id -> id == widget.getId())
                && ((Boolean) suggestion.getClass().getMethod("isAbortSuggestion").invoke(suggestion)
                    || (Boolean) suggestion.getClass().getMethod("isModifySuggestion").invoke(suggestion))) return false;
            HighlightTarget current = getTargetFromOverlay(controller, "");
            net.runelite.api.Point mouse = Microbot.getClient().getMouseCanvasPosition();
            return current != null && current.getWidget() == widget && bounds.equals(current.getClickBounds())
                && bounds.contains(point.getX(), point.getY()) && point.equals(mouse);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

	private boolean checkAndInteractHighlightedNpc()
	{
		if (isExchangeOpen() || isBankOpen()) return false;
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastActionTime < actionCooldown) return false;
		if (flippingCopilot == null || highlightController == null) return false;

		try {
			List<Object> highlightOverlays = getHighlightOverlays(highlightController);
			if (highlightOverlays == null) return false;

			for (Object overlay : highlightOverlays) {
				if (overlay != null && overlay.getClass().getSimpleName().contains("NpcHighlightOverlay")) {
					Field npcField = overlay.getClass().getDeclaredField("npc");
					npcField.setAccessible(true);
					NPC npc = (NPC) npcField.get(overlay);
					if (npc != null && Rs2Npc.hasAction(npc.getId(), "Exchange")) {
						String name = new Rs2NpcModel(npc).getName();
						log.info("Found highlighted GE NPC: {}", name);
						Rs2Npc.interact(npc, "Exchange");
						waitForUi(this::isExchangeOpen, 3000);
						lastActionTime = currentTime;
						actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
						return true;
					}
				}
			}
		} catch (GeUiState.UiUnavailable unavailable) {
            throw unavailable;
        } catch (Exception e) {
			log.error("Could not interact with highlighted NPC: {}", e.getClass().getSimpleName());
		}
		return false;
	}
}
