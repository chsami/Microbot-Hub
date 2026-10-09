package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.api.InventoryID;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
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
    private final FinishSession finishSession = new FinishSession();
    private volatile Runnable finishCallback;
    private volatile boolean finishComplete;
    private volatile long completedFinishGeneration;

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
        finishCallback = null;
        finishComplete = false;
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            boolean waitingTick = false;
            try {
				if (!canRunWithoutChangingSettings()) {
                    suspendForUnavailableUi();
                    return;
                }
                if (!Microbot.isLoggedIn()) return;

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
								Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
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
											Widget abortBtn = waitForOfferScreenAbortButton(2000);
											if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
												log.info("Aborting offer via offer screen button '{}'", abortBtn.getId());
												Rs2Widget.clickWidget(abortBtn);
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
                        break;
                }
            } catch (GeUiState.UiUnavailable unavailable) {
                suspendForUnavailableUi();
            } catch (Exception ex) {
                log.error("Error in FlipperScript: {} - ", ex.getClass().getSimpleName());
            } finally {
                if (!waitingTick) resetWaitingMouse();
            }
        }, 0, SCHEDULE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LIVE_FUTURES.add(mainScheduledFuture);
        return true;
    }


    /** Only the settings button requests this transient session; configuration events do not. */
    boolean requestFinish(Runnable completed) {
        if (!isRunning() || completed == null || !finishSession.request(false)) return false;
        finishCallback = completed;
        finishComplete = false;
        blockedSlotActionKey = null;
        slotActionStatus = "";
        resetWaitingMouse();
        return true;
    }

    boolean isFinishing() { return finishSession.isRequested(); }
    boolean isFinishComplete() {
        if (!finishComplete) return false;
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

    private boolean finishPermitsTrade() {
        return !finishSession.isRequested() || finishSession.canTrade(getSuggestion(suggestionManager));
    }

    private FinishOffers readFinishOffers() {
        return readUi(() -> FinishOffers.capture(Microbot.getClient().getGrandExchangeOffers(),
            Microbot.getClient().getItemContainer(InventoryID.INVENTORY)));
    }

    private boolean finishBuyStillActive(int slot) {
        return readUi(() -> finishSession.isActive() && isRunning()
            && finishSession.readyForCancellation()
            && !Microbot.pauseAllScripts.get() && !InputArbiter.isHuman()
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
                public void hover(net.runelite.api.Point point) { }
                public boolean awaitDefaultAction(int widgetId, SlotActionExecutor.Action action,
                                                  net.runelite.api.Point point) { return false; }
                public boolean clickDefaultAction(int widgetId, SlotActionExecutor.Action action,
                                                  net.runelite.api.Point point) { return false; }
                public boolean invokeAction(int widgetId, SlotActionExecutor.Action action,
                                            net.runelite.api.Point point) {
                    if (Thread.currentThread().isInterrupted() || !finishBuyStillActive(slot)
                        || slotActionPoint(widgetId, action) == null) return false;
                    Microbot.doInvoke(new NewMenuEntry().option(action.option).target("")
                        .identifier(action.identifier).type(MenuAction.CC_OP).param0(2).param1(widgetId)
                        .itemId(-1).forceLeftClick(false),
                        new Rectangle(point.getX() - 1, point.getY() - 1, 2, 2));
                    return true;
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

    private Rectangle finishCollectBounds() {
        return readUi(() -> {
            if (!readGeUi().overviewOpen) return null;
            return FinishCollect.bounds(Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL),
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
        });
    }

    private void collectFinishOffers() {
        Rectangle bounds = finishCollectBounds();
        FinishOffers offers = readFinishOffers();
        if (bounds == null || offers == null || offers.emptyInventorySlots == 0) {
            slotActionStatus("Finish: cannot collect into inventory. Make space or collect manually, then resume.");
            return;
        }
        if (Thread.currentThread().isInterrupted() || !isRunning() || !finishSession.isActive()
            || !finishSession.readyForCancellation()
            || Microbot.pauseAllScripts.get() || InputArbiter.isHuman()
            || !bounds.equals(finishCollectBounds())) return;
        Microbot.doInvoke(new NewMenuEntry().option("Collect to inventory").target("")
            .identifier(1).type(MenuAction.CC_OP).param0(0).param1((465 << 16) | 6)
            .itemId(-1).forceLeftClick(false), bounds);
        slotActionStatus = "";
        lastActionTime = System.currentTimeMillis();
        actionCooldown = DEFAULT_ACTION_COOLDOWN;
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
        FinishSession.CompletionSnapshot ready = new FinishSession.CompletionSnapshot(safeOverview,
            offers != null && offers.buySlot < 0, offers != null && offers.noCollectables,
            offers != null && offers.noUnlistedItems, offers != null && offers.noActiveSells,
            finishSession.healthyFreshWait(suggestion));
        if (finishSession.completionReady(ready, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))
            && finishSession.healthyFreshWait(getSuggestion(suggestionManager))) {
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
        if (!finishSession.readyForCancellation()) return true;
        if (ui.offerOpen && (offers.buySlot >= 0 || !allowed)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
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
            slotActionStatus("Finish: Copilot is waiting while items remain. Check its item strategy or sell them manually.");
            return true;
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
            public boolean paused() { return Microbot.pauseAllScripts.get(); }
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

    private boolean isUiWidgetVisible(int id) {
        return readUi(() -> {
            Widget widget = Microbot.getClient().getWidget(id);
            return widget != null && !widget.isHidden();
        });
    }

    private Widget findUiWidget(String text) { return findUiWidget(text, null, false); }

    private Widget findUiWidget(String text, List<Widget> children, boolean exact) {
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

    private boolean moveMouseWhileWaiting() {
        return waitingMouse.tick(config != null && config.waitingMouseOffScreen(),
            config == null ? 0 : config.waitingMouseChance(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), new WaitingMouse.Context() {
                public boolean waiting() { return isSafeMouseWait(); }
                public boolean insideCanvas() {
                    return readUi(() -> WaitingMouse.insideCanvas(Microbot.getMouse().getMousePosition(),
                        PointerState.isOutside(), Microbot.getClient().getCanvasWidth(),
                        Microbot.getClient().getCanvasHeight()));
                }
                public void moveOffScreen() {
                    // Use the shared primitive on this worker without changing global antiban state.
                    Rs2Antiban.moveMouseOffScreen();
                    if (!insideCanvas()) log.info("Waiting mouse: cursor is outside the game canvas.");
                    else log.debug("Waiting mouse: off-screen movement requested; exit not yet observed.");
                }
            });
    }

    private boolean isSafeMouseWait() {
        if (config == null || !config.waitingMouseOffScreen() || config.waitingMouseChance() <= 0
            || state != State.MONITORING_COPILOT
            || Thread.currentThread().isInterrupted() || Microbot.pauseAllScripts.get()
            || InputArbiter.isHuman() || !Microbot.isLoggedIn() || Microbot.naturalMouse == null
            || flippingCopilot == null || Microbot.getPluginManager() == null
            || !Microbot.getPluginManager().isPluginActive(flippingCopilot)
            || !Microbot.getPluginManager().isPluginEnabled(flippingCopilot)) return false;
        return readUi(() -> {
            if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get() || InputArbiter.isHuman()
                || !config.waitingMouseOffScreen() || config.waitingMouseChance() <= 0) return false;
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

	private void backToOverview() {
		log.info("Returning to GE overview.");
		Rs2GrandExchange.backToOverview();
		if (isOfferScreenOpen()) {
			Rs2Widget.clickWidget(30474244);
		}
		waitForUi(() -> !isOfferScreenOpen(), 2500);
		offerScreenOpenTime = 0;
		offerScreenActionCount = 0;
		lastActionTime = System.currentTimeMillis();
		actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
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
		// 1. Direct widget ID for abort button on GE offer details screen (Interface 465, child 22 / DETAILS_GRAPHIC6)
		Widget abortBtn = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_GRAPHIC6);
		if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}
		abortBtn = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 22);
		if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}

		// 2. Search for any widget within GE_OFFERS with an "Abort" action
		try {
			java.util.Map<Widget, String> actionWidgets = Rs2Widget.findWidgetsWithAction("Abort", InterfaceID.GE_OFFERS, false);
			if (actionWidgets != null && !actionWidgets.isEmpty()) {
				for (Widget w : actionWidgets.keySet()) {
					if (w != null && isUiWidgetVisible(w.getId())) {
						return w;
					}
				}
			}
		} catch (GeUiState.UiUnavailable unavailable) {
            throw unavailable;
        } catch (Exception ignored) {}

		// 3. Search children of DETAILS container (InterfaceID.GeOffers.DETAILS)
		try {
			Widget detailsContainer = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS);
			if (detailsContainer != null) {
				Widget[] children = detailsContainer.getChildren();
				if (children != null) {
					for (Widget child : children) {
						if (child != null && isUiWidgetVisible(child.getId()) && child.getActions() != null) {
							for (String action : child.getActions()) {
								if (action != null && action.toLowerCase().contains("abort")) {
									return child;
								}
							}
						}
					}
				}
				Widget[] dynamicChildren = detailsContainer.getDynamicChildren();
				if (dynamicChildren != null) {
					for (Widget child : dynamicChildren) {
						if (child != null && isUiWidgetVisible(child.getId()) && child.getActions() != null) {
							for (String action : child.getActions()) {
								if (action != null && action.toLowerCase().contains("abort")) {
									return child;
								}
							}
						}
					}
				}
			}
		} catch (GeUiState.UiUnavailable unavailable) {
            throw unavailable;
        } catch (Exception ignored) {}

		// 4. Search by widget text as final fallback
		abortBtn = findUiWidget("Abort offer");
		if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}
		abortBtn = findUiWidget("Abort");
		if (abortBtn != null && isUiWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}

		return null;
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

	private void triggerCopilotQuickSet() {
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
		if (km != null) {
			km.processKeyPressed(pressEvent);
			km.processKeyReleased(releaseEvent);
		}
		if (canvas != null) {
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
									if (generation != tradingGeneration || !isRunning() || !Microbot.isLoggedIn()) return;
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

	private void setCopilotChatboxValueDirectly(long val) {
		if (val <= 0) return;

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
									if (generation != tradingGeneration || !isRunning() || !Microbot.isLoggedIn()) return;
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
    private Widget findSuggestedItemWidget() {
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
        return !slotActionStatus.isEmpty() ? slotActionStatus
            : finishSession.isRequested() ? finishSession.status() : "";
    }

    private void slotActionStatus(String message) {
        if (!message.equals(slotActionStatus) && !message.isEmpty()) log.warn(message);
        slotActionStatus = message;
    }

    private String slotActionKey(Object suggestion) throws ReflectiveOperationException {
        StringBuilder key = new StringBuilder(config.slotAction().name())
            .append(':').append(isSlotActionSwapEnabled());
        for (String getter : new String[]{"getType", "getBoxId", "getName", "getPrice", "getQuantity"}) {
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
        if (!finishPermitsTrade()) return false;
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
                                       net.runelite.api.Point point, String key) {
        return readUi(() -> {
            if (Microbot.naturalMouse == null || Microbot.targetMenu != null || Microbot.getClient().isMenuOpen()
                || !isSlotActionSwapEnabled() || !sameSlotSuggestion(key) || isOfferScreenOpen()) return false;
            net.runelite.api.Point mouse = Microbot.getClient().getMouseCanvasPosition();
            Widget slot = Microbot.getClient().getWidget(slotId);
            Widget button = slot == null ? null : slot.getChild(2);
            if (button == null || button.isHidden() || mouse == null
                || mouse.getX() != point.getX() || mouse.getY() != point.getY()
                || !button.getBounds().contains(point.getX(), point.getY())) return false;
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
            // Consume the tick even during cooldown: generic slot highlights must not bypass this handler.
            if (System.currentTimeMillis() - lastActionTime < actionCooldown) return true;
            final String key = slotActionKey(suggestion);
            int boxId = (Integer) suggestion.getClass().getMethod("getBoxId").invoke(suggestion);
            if (boxId < 0 || boxId >= grandExchangeSlotIds.length) {
                blockedSlotActionKey = key;
                slotActionStatus("Invalid Copilot slot. Refresh suggestions or restart GE Flipper.");
                return true;
            }
            final int slotId = grandExchangeSlotIds[boxId];
            final SlotActionExecutor.Action action = abort
                ? SlotActionExecutor.Action.ABORT : SlotActionExecutor.Action.MODIFY;
            SlotActionExecutor.Result result = SlotActionExecutor.execute(config.slotAction(), action, slotId,
                new SlotActionExecutor.Ui() {
                    public boolean slotSwapEnabled() { return isSlotActionSwapEnabled(); }
                    public net.runelite.api.Point actionPoint(int id, SlotActionExecutor.Action a) {
                        return slotActionPoint(id, a);
                    }
                    public void hover(net.runelite.api.Point point) {
                        // Mouse.move dispatches a single jump. Follow a smooth path on this
                        // script thread; never block the client thread for mouse movement.
                        if (Microbot.naturalMouse != null && !Thread.currentThread().isInterrupted()) {
                            Microbot.naturalMouse.moveTo(point.getX(), point.getY());
                        }
                    }
                    public boolean awaitDefaultAction(int id, SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        return waitForUi(() -> isSlotDefaultAction(id, a, point, key), 1800);
                    }
                    public boolean clickDefaultAction(int id, SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        if (!FlipperScript.this.isRunning() || Thread.currentThread().isInterrupted()
                            || !isSlotDefaultAction(id, a, point, key)) return false;
                        // Reuse the verified point; a rectangle would choose a different point.
                        Microbot.getMouse().click(point);
                        return true;
                    }
                    public boolean invokeAction(int id, SlotActionExecutor.Action a, net.runelite.api.Point point) {
                        if (Thread.currentThread().isInterrupted() || !sameSlotSuggestion(key)
                            || isOfferScreenOpen() || slotActionPoint(id, a) == null) return false;
                        Microbot.doInvoke(new NewMenuEntry().option(a.option).target("")
                            .identifier(a.identifier).type(MenuAction.CC_OP).param0(2).param1(id)
                            .itemId(-1).forceLeftClick(false),
                            new Rectangle(point.getX() - 1, point.getY() - 1, 2, 2));
                        return true;
                    }
                });
            lastActionTime = System.currentTimeMillis();
            actionCooldown = DEFAULT_ACTION_COOLDOWN;
            if (handleSlotActionFailure(result, action, key)) return true;
            slotActionStatus = "";
            log.info("Executed {} on slot {} using {}.", action.option, boxId + 1, config.slotAction().actionDescription);
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
				Rs2Widget.clickWidget(copilotWidget);
			} else {
				log.info("Selecting Copilot item via hotkey (ENTER).");
				Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
			}
			
			// Wait for item selection widget to disappear (fallback to enter if still visible after mouse click)
			if (!waitForUi(() -> !isUiWidgetVisible(copilotWidget.getId()), 2000)) {
				Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
				if (!waitForUi(() -> !isUiWidgetVisible(copilotWidget.getId()), 1500)) {
					// Fallback to mouse click if ENTER failed
					if (isUiWidgetVisible(copilotWidget.getId())) {
						Rs2Widget.clickWidget(copilotWidget);
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

			// Parse the suggested value from button text if available (e.g. "Press [E] to set to Copilot price: 979 gp")
			long valFromButton = -1;
			if (copilotButton != null && copilotButton.getText() != null) {
				String btnText = copilotButton.getText().replaceAll("[^0-9]", "");
				if (!btnText.isEmpty()) {
					try {
						valFromButton = Long.parseLong(btnText);
					} catch (Exception ignored) {}
				}
			}

			if (isMouseMode()) {
				if (copilotButtonVisible) {
					log.info("Clicking Copilot prompt button '{}' via mouse.", copilotButton.getId());
					Rs2Widget.clickWidget(copilotButton);
				} else {
					log.info("Copilot prompt button not visible, falling back to hotkey [E].");
					triggerCopilotQuickSet();
				}
				waitForUi(this::hasChatboxInput, 1500);
			} else {
				// Hotkey mode: Strictly use hotkey [E] without mouse clicks
				log.info("Selecting Copilot suggestion via hotkey [E].");
				triggerCopilotQuickSet();

				// If hotkey didn't populate within 600ms, set directly via Copilot offerHandler without mouse
				if (!waitForUi(this::hasChatboxInput, 600)) {
					if (valFromButton > 0) {
						log.info("Setting chatbox value directly from Copilot suggestion without mouse.");
						setCopilotChatboxValueDirectly(valFromButton);
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
					setCopilotChatboxValueDirectly(val);
					waitForUi(this::hasChatboxInput, 800);
				}

				// Fallback: If still not populated, type the value into chatbox
				if (!hasChatboxInput() && val > 0) {
					log.info("Entering the suggested {} into the active prompt.", isPricePrompt ? "price" : "quantity");
					Rs2Keyboard.typeString(String.valueOf(val));
					waitForUi(this::hasChatboxInput, 1000);
				}
			}

			// Check if chatbox input was successfully populated
			if (!hasChatboxInput()) {
				log.warn("Failed to populate {} input! Cancelling prompt with ESC to prevent chat spam and backing out to GE overview.",
					isPricePrompt ? "price" : "quantity");
				Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
				sleep(200, 400);
				backToOverview();
				return true;
			}

			// Submit the value
			if (!finishPermitsTrade()) return true;
			sleep(KEY_PRESS_DELAY_MIN, KEY_PRESS_DELAY_MAX);
			Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
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
                Microbot.getMouse().click(bounds);
                return true;
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

				if (clickBounds != null && Rs2UiHelper.isRectangleWithinCanvas(clickBounds)) {
					Microbot.getMouse().click(clickBounds);
				} else {
					Rs2Widget.clickWidget(highlightedWidget);
				}
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
