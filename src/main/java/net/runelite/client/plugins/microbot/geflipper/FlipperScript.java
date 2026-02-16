package net.runelite.client.plugins.microbot.geflipper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
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
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
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

@Slf4j
public class FlipperScript extends Script {
	private final WorldArea grandExchangeArea = new WorldArea(3136, 3465, 61, 54, 0);
    State state = State.GOING_TO_GE;

    private Plugin flippingCopilot;
	private Object suggestionManager;
    private Object highlightController;
    private long lastActionTime = 0;
    private long actionCooldown = 1800; // randomized cooldown between actions
	private long interactionTimeout = 33000;

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
                        if (!grandExchangeArea.contains(Microbot.getClient().getLocalPlayer().getWorldLocation())) {
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
                            Rs2Inventory.waitForInventoryChanges(5000);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            state = State.MONITORING_COPILOT;
                        }
                        break;

                    case MONITORING_COPILOT:
                        if (!Rs2GrandExchange.isOpen()) {
                            Rs2GrandExchange.openExchange();
                            return;
                        }

                        // Check interaction timeout FIRST - reset state if stuck
                        long currentTime = System.currentTimeMillis();
                        if (Rs2GrandExchange.isOfferScreenOpen() && (currentTime - lastActionTime > interactionTimeout)) {
                            Rs2GrandExchange.backToOverview();

                            lastActionTime = System.currentTimeMillis();
                            actionCooldown = Rs2Random.randomGaussian(1800, 300);
                            interactionTimeout = Rs2Random.randomGaussian(33000, 6000);

                            log.info("interactionTimeout reached, returning to GE overview.");
                            return;
                        }

						// Check for Copilot price/quantity messages in chat
						if (checkAndPressCopilotKeybind()) return;

                        // Check if we need to abort any offers
                        if (checkAndAbortIfNeeded()) return;

                        // Check for highlighted widgets
                        if (checkAndClickHighlightedWidgets()) return;

                        break;
                }
            } catch (Exception ex) {
                log.error("Error in FlipperScript: {} - ", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

	@Override
	public void shutdown()
	{
		flippingCopilot = null;
		suggestionManager = null;
		highlightController = null;
		lastActionTime = 0;
		actionCooldown = 1800;
		super.shutdown();
	}

	private boolean initialize()
	{
		if (flippingCopilot != null && suggestionManager != null && highlightController != null) return true;

		Plugin _flippingCopilot = getFlippingCopilot();
		Object _suggestionManager = getSuggestionManager(_flippingCopilot);
		Object _highlightController = getHighlightController(_flippingCopilot);

		return _flippingCopilot != null && _suggestionManager != null && _highlightController != null;
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

	private String getSuggestionType(Object suggestion)
	{
		if (suggestion == null) return null;
		try
		{
			Field typeField = suggestion.getClass().getDeclaredField("type");
			typeField.setAccessible(true);
			return (String) typeField.get(suggestion);
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

	private List<Widget> getHighlightWidgets(Object highlightController)
	{
		final List<Widget> highlightWidgets = new ArrayList<>();
		if (highlightController == null)
		{
			return highlightWidgets;
		}

		List<Object> highlightOverlays = getHighlightOverlays(highlightController);

		for (Object highlightOverlay : highlightOverlays)
		{
			try
			{
				Field widgetField = highlightOverlay.getClass().getDeclaredField("widget");
				widgetField.setAccessible(true);
				highlightWidgets.add((Widget) widgetField.get(highlightOverlay));
			}
			catch (Exception e)
			{
				log.error("Could not get widget from overlay: {} - ", e.getMessage(), e);
				return highlightWidgets;
			}
		}

		return highlightWidgets;
	}

	private Widget getWidgetFromOverlay(Object highlightController, String suggestionType)
	{
		List<Object> highlightOverlays = getHighlightOverlays(highlightController);
		if (highlightOverlays == null || highlightOverlays.isEmpty())
		{
			return null;
		}

		if (Objects.equals(suggestionType, "abort"))
		{
			return getHighlightWidgets(highlightController).stream()
				.filter(Objects::nonNull)
				// Filter to "home" grand exchange slot widgets
				.filter(widget -> Arrays.stream(grandExchangeSlotIds).anyMatch(id -> id == widget.getId()))
				.findFirst()
				.orElse(null);
		}
		else
		{
			// For other suggestion types, we can return the first highlighted widget
			return getHighlightWidgets(highlightController).stream()
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
		}
	}

	private boolean checkAndAbortIfNeeded()
	{
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastActionTime < actionCooldown) return true;

		if (flippingCopilot == null || highlightController == null || suggestionManager == null) return false;
		try
		{
			Object currentSuggestion = getSuggestion(suggestionManager);
			if (currentSuggestion == null) return false;

			String suggestionType = getSuggestionType(currentSuggestion);

			if (!Objects.equals(suggestionType, "abort")) return false;

			log.info("Found suggestion type '{}'.", suggestionType);

			Widget abortWidget = getWidgetFromOverlay(highlightController, suggestionType);
			if (abortWidget != null)
			{
				NewMenuEntry menuEntry = new NewMenuEntry("Abort offer", "", 2, MenuAction.CC_OP, 2, abortWidget.getId(), false);
				Rectangle bounds = abortWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(abortWidget.getBounds())
					? abortWidget.getBounds()
					: Rs2UiHelper.getDefaultRectangle();
				Microbot.doInvoke(menuEntry, bounds);
				lastActionTime = System.currentTimeMillis();
				actionCooldown = Rs2Random.randomGaussian(1800, 300);
				return true;
			}
		}
		catch (Exception e)
		{
			log.error("Could not process suggestion: {} - ", e.getMessage(), e);
		}
		return false;
	}

    private boolean checkAndPressCopilotKeybind() {
		boolean isChatboxInputOpen = Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_LAYER);
		if (!isChatboxInputOpen) return false;
		log.info("Found chatbox input open, pressing 'e' then Enter");
		// Press 'e' to trigger FlippingCopilot's keybind
		Rs2Keyboard.keyPress(KeyEvent.VK_E);
		sleep(250, 400);
		Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);

		lastActionTime = System.currentTimeMillis();
		return true;
    }

    private boolean checkAndClickHighlightedWidgets()
	{
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastActionTime < actionCooldown) return false;

		if (flippingCopilot == null || highlightController == null) return false;

		try {
			Widget highlightedWidget = getWidgetFromOverlay(highlightController, "");
			boolean isHighlightedVisible = highlightedWidget != null && Rs2Widget.isWidgetVisible(highlightedWidget.getId());

			if (isHighlightedVisible) {
				log.info("Clicking highlighted widget: {}", highlightedWidget.getId());
				Rs2Widget.clickWidget(highlightedWidget);
				lastActionTime = currentTime;
                actionCooldown = Rs2Random.randomGaussian(1800, 300);
				return true;
			}
		}
		catch (Exception e)
		{
			log.error("Could not process highlight widgets: {} - ", e.getMessage(), e);
		}

		return false;
	}
}