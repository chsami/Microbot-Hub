package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import net.runelite.api.MenuAction;
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
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
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
import java.util.concurrent.TimeUnit;

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
	private static final int KEY_PRESS_DELAY_MIN = 250;
	private static final int KEY_PRESS_DELAY_MAX = 400;

	private final WorldArea grandExchangeArea = new WorldArea(3136, 3465, 61, 54, 0);
    State state = State.GOING_TO_GE;

    private Plugin flippingCopilot;
	private Object suggestionManager;
    private Object highlightController;
    private long lastActionTime = 0;
    private long actionCooldown = DEFAULT_ACTION_COOLDOWN;
	private long interactionTimeout = DEFAULT_INTERACTION_TIMEOUT;
	private long offerScreenOpenTime = 0;
	private int offerScreenActionCount = 0;

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

	public boolean run(FlipperConfig config) {
		this.config = config;
		return run();
	}

	private boolean isMouseMode() {
		return config != null && config.selectionMethod() == FlipperConfig.SelectionMethod.MOUSE;
	}

    public boolean run() {
        Rs2AntibanSettings.naturalMouse = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;

                if (!initialize()) {
					log.warn("FlipperScript initialization failed. Ensure Flipping Copilot is installed and enabled.");
					return;
				}

                switch (state) {
                    case GOING_TO_GE:
                        if (Rs2GrandExchange.isOpen() && Rs2Inventory.onlyContains(ItemID.COINS)) {
                             state = State.MONITORING_COPILOT;
                             return;
                        }
						WorldPoint playerLocation = Rs2Player.getWorldLocation();
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
                            sleepUntil(Rs2Inventory::isEmpty);
                            Rs2Bank.withdrawAll(ItemID.COINS);
                            Rs2Inventory.waitForInventoryChanges(INVENTORY_WAIT_TIMEOUT);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            state = State.MONITORING_COPILOT;
                        }
                        break;

                    case MONITORING_COPILOT:
						long currentTime = System.currentTimeMillis();

						// 0. Offer screen watchdog & loop detection
						if (isOfferScreenOpen()) {
							if (offerScreenOpenTime == 0) {
								offerScreenOpenTime = currentTime;
								offerScreenActionCount = 0;
							}

							// Check if "Too much money!" warning is shown on offer screen
							if (Rs2Widget.hasWidget("Too much money")) {
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
											Widget abortBtn = getOfferScreenAbortButton();
											if (abortBtn != null && Rs2Widget.isWidgetVisible(abortBtn.getId())) {
												log.info("Aborting offer via offer screen button '{}'", abortBtn.getId());
												Rs2Widget.clickWidget(abortBtn);
												sleep(300, 500);

												// Check for confirmation dialog ('Are you sure...')
												if (sleepUntil(() -> Rs2Widget.hasWidget("Are you sure") || Rs2Widget.hasWidget("Your offer is much"), 1200)) {
													log.info("Abort confirmation dialog detected. Confirming 'Yes'...");
													Rs2Widget.clickWidget("Yes");
													sleep(200, 400);
												}

												// Wait for abort to register, then back to overview
												sleepUntil(() -> !isOfferScreenOpen() || getOfferScreenAbortButton() == null, 2500);
												backToOverview();
											} else {
												// Check if already aborted / cancelled on offer screen
												Widget statusWidget = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_STATUS);
												String statusText = statusWidget != null ? statusWidget.getText() : "";
												if (statusText != null && (statusText.toLowerCase().contains("cancelled") || statusText.toLowerCase().contains("aborted"))) {
													log.info("Offer already cancelled on offer screen. Returning to overview.");
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
								} catch (Exception ignored) {}
							}

							// If stuck on offer screen for > 30 seconds or after 10 repeated actions without closing
							if (currentTime - offerScreenOpenTime > 30000 || offerScreenActionCount >= 10) {
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
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen(), 2500);
                            return;
                        }

						// 2. Check for Copilot price/quantity messages in chat
						if (checkAndPressCopilotKeybind()) return;

                        // 3. Check if we need to abort any offers
                        if (checkAndAbortOrModifyIfNeeded()) return;

                        // 4. Check for highlighted widgets
                        if (checkAndClickHighlightedWidgets()) return;

                        // 5. Check for highlighted NPCs
                        if (checkAndInteractHighlightedNpc()) return;

                        // 6. If neither GE nor Bank is open, open GE
                        if (!Rs2GrandExchange.isOpen() && !Rs2Bank.isOpen()) {
                            Rs2GrandExchange.openExchange();
                            return;
                        }

                        break;
                }
            } catch (Exception ex) {
                log.error("Error in FlipperScript: {} - ", ex.getMessage(), ex);
            }
        }, 0, SCHEDULE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return true;
    }

	@Override
	public void shutdown()
	{
		flippingCopilot = null;
		suggestionManager = null;
		highlightController = null;
		lastActionTime = 0;
		actionCooldown = DEFAULT_ACTION_COOLDOWN;
		super.shutdown();
	}

	private boolean initialize()
	{
		if (flippingCopilot != null && suggestionManager != null && highlightController != null) {
			ensureSlotActionSwapEnabled();
			return true;
		}

		Plugin _flippingCopilot = getFlippingCopilot();
		Object _suggestionManager = getSuggestionManager(_flippingCopilot);
		Object _highlightController = getHighlightController(_flippingCopilot);

		if (_flippingCopilot != null && _suggestionManager != null && _highlightController != null) {
			ensureSlotActionSwapEnabled();
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
				.filter(plugin -> plugin.getClass().getSimpleName().equalsIgnoreCase("FlippingCopilotPlugin"))
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
				log.error("Could not access HighlightController: {} - ", e.getMessage(), e);
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
				log.error("Could not access SuggestionManager: {} - ", e.getMessage(), e);
			}
		}
		return suggestionManager;
	}

	private boolean isOfferScreenOpen() {
		return Rs2GrandExchange.isOfferScreenOpen() 
			|| Rs2Widget.isWidgetVisible(30474266) 
			|| Rs2Widget.isWidgetVisible(30474267);
	}

	private void backToOverview() {
		log.info("Returning to GE overview.");
		Rs2GrandExchange.backToOverview();
		if (isOfferScreenOpen()) {
			Rs2Widget.clickWidget(30474244);
		}
		sleepUntil(() -> !isOfferScreenOpen(), 2500);
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
		return true;
	}

	public void ensureSlotActionSwapEnabled() {
		try {
			if (config != null && !config.autoEnableSlotSwap()) {
				return;
			}
			if (Microbot.getConfigManager() != null) {
				String val = Microbot.getConfigManager().getConfiguration("flippingcopilot", "slotActionSwap");
				if (!"true".equalsIgnoreCase(val)) {
					log.info("Flipping Copilot 'slotActionSwap' is disabled; automatically enabling it in ConfigManager.");
					Microbot.getConfigManager().setConfiguration("flippingcopilot", "slotActionSwap", true);
				}
			}
		} catch (Exception e) {
			log.warn("Failed to set flippingcopilot slotActionSwap setting: {}", e.getMessage());
		}
	}

	private Widget getOfferScreenAbortButton() {
		// 1. Direct widget ID for abort button on GE offer details screen (Interface 465, child 22 / DETAILS_GRAPHIC6)
		Widget abortBtn = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_GRAPHIC6);
		if (abortBtn != null && Rs2Widget.isWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}
		abortBtn = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 22);
		if (abortBtn != null && Rs2Widget.isWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}

		// 2. Search for any widget within GE_OFFERS with an "Abort" action
		try {
			java.util.Map<Widget, String> actionWidgets = Rs2Widget.findWidgetsWithAction("Abort", InterfaceID.GE_OFFERS, false);
			if (actionWidgets != null && !actionWidgets.isEmpty()) {
				for (Widget w : actionWidgets.keySet()) {
					if (w != null && Rs2Widget.isWidgetVisible(w.getId())) {
						return w;
					}
				}
			}
		} catch (Exception ignored) {}

		// 3. Search children of DETAILS container (InterfaceID.GeOffers.DETAILS)
		try {
			Widget detailsContainer = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS);
			if (detailsContainer != null) {
				Widget[] children = detailsContainer.getChildren();
				if (children != null) {
					for (Widget child : children) {
						if (child != null && Rs2Widget.isWidgetVisible(child.getId()) && child.getActions() != null) {
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
						if (child != null && Rs2Widget.isWidgetVisible(child.getId()) && child.getActions() != null) {
							for (String action : child.getActions()) {
								if (action != null && action.toLowerCase().contains("abort")) {
									return child;
								}
							}
						}
					}
				}
			}
		} catch (Exception ignored) {}

		// 4. Search by widget text as final fallback
		abortBtn = Rs2Widget.findWidget("Abort offer");
		if (abortBtn != null && Rs2Widget.isWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}
		abortBtn = Rs2Widget.findWidget("Abort");
		if (abortBtn != null && Rs2Widget.isWidgetVisible(abortBtn.getId())) {
			return abortBtn;
		}

		return null;
	}

	private boolean hasChatboxInput() {
		Widget inputWidget = Rs2Widget.getWidget(10616876);
		if (inputWidget == null) inputWidget = Rs2Widget.getWidget(162, 44);
		if (inputWidget != null) {
			String text = inputWidget.getText();
			if (text != null && !text.trim().isEmpty() && !text.trim().equals("*")) {
				return true;
			}
		}
		try {
			String varcStr = Microbot.getClient().getVarcStrValue(359);
			if (varcStr != null && !varcStr.trim().isEmpty() && !varcStr.trim().equals("*")) {
				return true;
			}
		} catch (Exception ignored) {}
		return false;
	}

	private KeyManager getKeyManager() {
		if (keyManager != null) return keyManager;
		try {
			if (Microbot.getInjector() != null) {
				keyManager = Microbot.getInjector().getInstance(KeyManager.class);
			}
		} catch (Exception e) {
			log.warn("Could not get KeyManager: {}", e.getMessage());
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
								Microbot.getClientThread().invokeLater(() -> {
									try {
										m.invoke(keyListener, true, false, false);
									} catch (Exception ex) {
										log.debug("handleKeybind invoke failed: {}", ex.getMessage());
									}
								});
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				log.debug("Could not trigger handleKeybind via reflection: {}", e.getMessage());
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
								Microbot.getClientThread().invokeLater(() -> {
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
				log.debug("Could not set chatbox value via offerHandler: {}", e.getMessage());
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
			log.error("Could not access Suggestion: {} ", e.getMessage(), e);
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
			log.error("Could not access suggestion type: {} - ", e.getMessage(), e);
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
			log.error("Could not access highlight overlays: {} - ", e.getMessage(), e);
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
				log.error("Could not get target from overlay: {} - ", e.getMessage(), e);
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
				.filter(t -> t.getWidget() != null && Rs2Widget.isWidgetVisible(t.getWidget().getId()))
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

	private boolean checkAndAbortOrModifyIfNeeded()
	{
		if (!Rs2GrandExchange.isOpen() || isOfferScreenOpen()) return false;
		if (System.currentTimeMillis() - lastActionTime < actionCooldown) return false;

		if (flippingCopilot == null || highlightController == null || suggestionManager == null) return false;
		try
		{
			Object currentSuggestion = getSuggestion(suggestionManager);
			if (currentSuggestion == null) return false;

			// Use Suggestion helper methods (type is now SuggestionType enum, not String)
			Method isAbortMethod = currentSuggestion.getClass().getMethod("isAbortSuggestion");
			Method isModifyMethod = currentSuggestion.getClass().getMethod("isModifySuggestion");
			boolean isAbort = (Boolean) isAbortMethod.invoke(currentSuggestion);
			boolean isModify = (Boolean) isModifyMethod.invoke(currentSuggestion);

			if (!isAbort && !isModify) return false;

			Widget abortWidget = getWidgetFromOverlay(highlightController, isAbort ? "abort" : "modify");
			if (abortWidget == null)
			{
				try {
					Method getBoxIdMethod = currentSuggestion.getClass().getMethod("getBoxId");
					int boxId = (Integer) getBoxIdMethod.invoke(currentSuggestion);
					if (boxId >= 0 && boxId < grandExchangeSlotIds.length) {
						abortWidget = Rs2Widget.getWidget(grandExchangeSlotIds[boxId]);
					}
				} catch (Exception ignored) {}
			}
			if (abortWidget != null && Rs2Widget.isWidgetVisible(abortWidget.getId()))
			{	
				if (isAbort)
				{
					ensureSlotActionSwapEnabled();
					boolean slotActionSwap = isSlotActionSwapEnabled();
					log.info("Executing suggestion ABORT on slot widget {} (slotActionSwap={})", abortWidget.getId(), slotActionSwap);
					if (slotActionSwap)
					{
						NewMenuEntry abortEntry = new NewMenuEntry()
							.option("Abort offer")
							.target("")
							.identifier(2)
							.type(MenuAction.CC_OP)
							.param0(2)
							.param1(abortWidget.getId())
							.itemId(-1)
							.forceLeftClick(false);
						Rectangle bounds = abortWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(abortWidget.getBounds())
							? abortWidget.getBounds()
							: Rs2UiHelper.getDefaultRectangle();
						Microbot.doInvoke(abortEntry, bounds);
					}
					else
					{
						// When slotActionSwap is OFF, left-clicking the slot widget in OSRS opens "View offer".
						// We open the offer screen and let getOfferScreenAbortButton perform the abort reliably.
						log.info("slotActionSwap is disabled: opening slot widget {} to abort from offer screen.", abortWidget.getId());
						Rs2Widget.clickWidget(abortWidget);
						sleepUntil(this::isOfferScreenOpen, 2500);
					}
					lastActionTime = System.currentTimeMillis();
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
				else // isModify
				{
					log.info("Executing suggestion MODIFY: opening slot widget {}", abortWidget.getId());
					Rs2Widget.clickWidget(abortWidget);
					lastActionTime = System.currentTimeMillis();
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
			}
			else if (isAbort)
			{
				try {
					Method getNameMethod = currentSuggestion.getClass().getMethod("getName");
					String itemName = (String) getNameMethod.invoke(currentSuggestion);
					if (itemName != null && !itemName.isEmpty()) {
						log.info("Executing suggestion ABORT via Rs2GrandExchange.abortOffer for item '{}'", itemName);
						if (Rs2GrandExchange.abortOffer(itemName, false)) {
							lastActionTime = System.currentTimeMillis();
							actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
							return true;
						}
					}
				} catch (Exception ignored) {}
			}
		}
		catch (Exception e)
		{
			log.error("Could not process suggestion: {} - ", e.getMessage(), e);
		}
		return false;
	}

    private boolean checkAndPressCopilotKeybind() {
		// 1. Search for a widget with text "Copilot item" (if it's time to select the item suggestion in the buy item window)
        Widget copilotWidget = Rs2Widget.findWidget("Copilot item:", null, false);
        if (copilotWidget != null && Rs2Widget.isWidgetVisible(copilotWidget.getId())) {
			log.info("Found chat widget Copilot item '{}'.", copilotWidget.getId());
			if (isMouseMode()) {
				log.info("Selecting Copilot item via mouse click.");
				Rs2Widget.clickWidget(copilotWidget);
			} else {
				log.info("Selecting Copilot item via hotkey (ENTER).");
				Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
			}
			
			// Wait for item selection widget to disappear (fallback to enter if still visible after mouse click)
			if (!sleepUntil(() -> !Rs2Widget.isWidgetVisible(copilotWidget.getId()), 2000)) {
				Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
				if (!sleepUntil(() -> !Rs2Widget.isWidgetVisible(copilotWidget.getId()), 1500)) {
					// Fallback to mouse click if ENTER failed
					if (Rs2Widget.isWidgetVisible(copilotWidget.getId())) {
						Rs2Widget.clickWidget(copilotWidget);
						sleepUntil(() -> !Rs2Widget.isWidgetVisible(copilotWidget.getId()), 1500);
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

		Widget setPriceWidget = Rs2Widget.findWidget("Set a price for each item:", null, false);
		Widget setQuantityWidget = Rs2Widget.findWidget("How many do you wish to ", null, false);

		boolean isPricePrompt = setPriceWidget != null && Rs2Widget.isWidgetVisible(setPriceWidget.getId());
		boolean isQuantityPrompt = setQuantityWidget != null && Rs2Widget.isWidgetVisible(setQuantityWidget.getId());

        if (isPricePrompt || isQuantityPrompt) {
			Widget promptWidget = isPricePrompt ? setPriceWidget : setQuantityWidget;
			log.info("Found chat widget ({}) '{}'.", isPricePrompt ? "price" : "quantity", promptWidget.getId());

			// 1. First attempt: Click Copilot's prompt button if visible, or press hotkey
			Widget copilotButton = Rs2Widget.findWidget("to set to Copilot", null, false);
			boolean copilotButtonVisible = copilotButton != null && Rs2Widget.isWidgetVisible(copilotButton.getId());

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
				sleepUntil(this::hasChatboxInput, 1500);
			} else {
				// Hotkey mode: Strictly use hotkey [E] without mouse clicks
				log.info("Selecting Copilot suggestion via hotkey [E].");
				triggerCopilotQuickSet();

				// If hotkey didn't populate within 600ms, set directly via Copilot offerHandler without mouse
				if (!sleepUntil(this::hasChatboxInput, 600)) {
					if (valFromButton > 0) {
						log.info("Setting chatbox value ({}) directly from Copilot suggestion without mouse.", valFromButton);
						setCopilotChatboxValueDirectly(valFromButton);
						sleepUntil(this::hasChatboxInput, 600);
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
							int currentOfferItemId = Microbot.getClient().getVarpValue(1151);
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
								log.warn("Suggestion itemId ({}) does not match offer screen itemId ({})! Skipping suggestion value.",
									suggestionItemId, currentOfferItemId);
							}
						} catch (Exception e) {
							log.error("Failed to read suggestion value: {}", e.getMessage());
						}
					}
				}

				if (val > 0) {
					log.info("Setting {} value directly on client thread: {}", isPricePrompt ? "price" : "quantity", val);
					setCopilotChatboxValueDirectly(val);
					sleepUntil(this::hasChatboxInput, 800);
				}

				// Fallback: If still not populated, type the value into chatbox
				if (!hasChatboxInput() && val > 0) {
					log.info("Typing {} value into chatbox: {}", isPricePrompt ? "price" : "quantity", val);
					Rs2Keyboard.typeString(String.valueOf(val));
					sleepUntil(this::hasChatboxInput, 1000);
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
			sleep(KEY_PRESS_DELAY_MIN, KEY_PRESS_DELAY_MAX);
			Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
			sleepUntil(() -> !Rs2Widget.isWidgetVisible(promptWidget.getId()), 2500);
			offerScreenActionCount++;
			lastActionTime = System.currentTimeMillis();
			actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
			return true;
        }

		return false;
    }

    private boolean checkAndClickHighlightedWidgets()
	{
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastActionTime < actionCooldown) return false;

		if (flippingCopilot == null || highlightController == null) return false;

		try {
			if (Rs2Widget.hasWidget("Your offer is much") || Rs2Widget.hasWidget("Are you sure")) {
				log.info("Price warning dialog detected ('Your offer is much' / 'Are you sure'). Clicking 'Yes' to confirm...");
				Rs2Widget.clickWidget("Yes");
				lastActionTime = currentTime;
				actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
				return true;
			}

			HighlightTarget target = getTargetFromOverlay(highlightController, "");
			if (target != null && target.getWidget() != null && Rs2Widget.isWidgetVisible(target.getWidget().getId())) {
				Widget highlightedWidget = target.getWidget();
				Rectangle clickBounds = target.getClickBounds();
				log.info("Processing highlighted target: widgetId={}, clickBounds={}, relativeBounds={}",
					highlightedWidget.getId(), clickBounds, target.getRelativeBounds());

				// If GE close button is highlighted (container 30474242 or close button dynamic child)
				if (highlightedWidget.getId() == 30474242 && Rs2GrandExchange.isOpen()) {
					Rs2GrandExchange.closeExchange();
					sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2500);
					lastActionTime = currentTime;
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
				// If Bank close button is highlighted (container 786434 or close button dynamic child)
				if (highlightedWidget.getId() == 786434 && Rs2Bank.isOpen()) {
					Rs2Bank.closeBank();
					sleepUntil(() -> !Rs2Bank.isOpen(), 2500);
					lastActionTime = currentTime;
					actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
					return true;
				}
				// If GE is on offer screen and has "Too much money!" warning, back out immediately
				if (isOfferScreenOpen() && Rs2Widget.hasWidget("Too much money")) {
					log.warn("Offer has 'Too much money!' error. Backing out to GE overview.");
					backToOverview();
					return true;
				}

				boolean isConfirm = target.isConfirmTarget();

				boolean isSlotWidget = Arrays.stream(grandExchangeSlotIds).anyMatch(id -> id == highlightedWidget.getId());
				boolean isAbortOnSlot = false;
				if (isSlotWidget && suggestionManager != null) {
					try {
						Object currentSuggestion = getSuggestion(suggestionManager);
						if (currentSuggestion != null) {
							Method isAbortMethod = currentSuggestion.getClass().getMethod("isAbortSuggestion");
							isAbortOnSlot = (Boolean) isAbortMethod.invoke(currentSuggestion);
						}
					} catch (Exception ignored) {}
				}
				if (isAbortOnSlot) {
					ensureSlotActionSwapEnabled();
					if (!isSlotActionSwapEnabled()) {
						log.info("Highlighted GE slot {} for abort with slotActionSwap=false; clicking slot to open offer screen.",
							highlightedWidget.getId());
					}
				}

				if (clickBounds != null && Rs2UiHelper.isRectangleWithinCanvas(clickBounds)) {
					Microbot.getMouse().click(clickBounds);
				} else {
					Rs2Widget.clickWidget(highlightedWidget);
				}
				Rs2Random.wait(100, 200);
				lastActionTime = currentTime;
				actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);

				if (isAbortOnSlot && !isSlotActionSwapEnabled()) {
					sleepUntil(this::isOfferScreenOpen, 2500);
				}

				if (isOfferScreenOpen()) {
					offerScreenActionCount++;
				}

				// If confirming an offer, dismiss any price warning dialog and wait for offer screen to close
				if (isConfirm) {
					log.info("Clicked Confirm button. Checking for warning dialog or waiting for offer screen to close...");
					if (sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much") || Rs2Widget.hasWidget("Are you sure"), 1200)) {
						log.info("Warning dialog appeared ('Your offer is much' / 'Are you sure'). Confirming 'Yes'...");
						Rs2Widget.clickWidget("Yes");
					}
					if (!sleepUntil(() -> !isOfferScreenOpen(), 4000)) {
						log.warn("Offer screen did not close after confirm. Backing out to overview.");
						backToOverview();
						return false;
					}
					log.info("Offer placed successfully; offer screen closed.");
				}

				return true;
			}
		}
		catch (Exception e)
		{
			log.error("Could not process highlight widgets: {} - ", e.getMessage(), e);
		}

		return false;
	}

	private boolean checkAndInteractHighlightedNpc()
	{
		if (Rs2GrandExchange.isOpen() || Rs2Bank.isOpen()) return false;
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
						sleepUntil(Rs2GrandExchange::isOpen, 3000);
						lastActionTime = currentTime;
						actionCooldown = Rs2Random.randomGaussian(DEFAULT_ACTION_COOLDOWN, ACTION_COOLDOWN_VARIANCE);
						return true;
					}
				}
			}
		} catch (Exception e) {
			log.error("Could not interact with highlighted NPC: {}", e.getMessage());
		}
		return false;
	}
}