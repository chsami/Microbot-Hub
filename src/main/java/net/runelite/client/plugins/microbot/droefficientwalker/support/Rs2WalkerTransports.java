package net.runelite.client.plugins.microbot.droefficientwalker.support;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandler;
import net.runelite.client.plugins.microbot.droefficientwalker.support.seasonal.SeasonalTransportHandlers;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2Walker.normalizeCharterWidgetText;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/** Preserved transport actions used only by Efficient Walker's own execution coordinator. */
@Slf4j
final class Rs2WalkerTransports {
private static final int OFFSET = 10;
private static final int SEASONAL_HANDLER_MISS_LOG_CAP = 128;
private static final Set<String> SEASONAL_HANDLER_MISS_LOGGED = ConcurrentHashMap.newKeySet();
private static final AtomicInteger SEASONAL_HANDLER_MISS_LOGGED_COUNT = new AtomicInteger();


    /**
     * Same-plane Chebyshev distance from player to {@code dest} strictly less than {@code maxChebyshevExclusive}.
     * Requires matching {@link WorldPoint#getPlane()} before using {@link WorldPoint#distanceTo2D} — that method only
     * compares X/Y, so same X/Y on different planes still reads as distance {@code 0} without an explicit plane check.
     */
    private static boolean isPlayerWithinChebyshevOf(WorldPoint dest, int maxChebyshevExclusive) {
        if (dest == null) {
            return false;
        }
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.getPlane() == dest.getPlane()
                && pl.distanceTo2D(dest) < maxChebyshevExclusive;
    }


    private static boolean handleTeleportSpell(Transport transport) {
        if (Rs2Pvp.isInWilderness() && !isTeleportAllowedAtWildernessLevel(
                Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()), transport.getMaxWildernessLevel())) return false;
        if (!prepareTeleportSpellProviders(transport)) return false;
        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");

        String spellName = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().toLowerCase();

        String option = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : "cast";

        int identifier = hasMultipleDestination
                ? 2
                : 1;

        Optional<TransportExecutionRegistry.HomeTeleport> homeTeleport =
                TransportExecutionRegistry.homeTeleportFor(transport.getDisplayInfo());
        if (homeTeleport.isPresent()) {
            if (!isTeleportSpellUsableDuringCombat(transport, Rs2Player.isInCombat())) {
                return false;
            }
            return Microbot.getClient() != null && !Microbot.getClient().isClientThread()
                    && Rs2Magic.quickCast(homeTeleport.get().getDisplayName());
        }

        MagicAction magicSpell = Arrays.stream(MagicAction.values()).filter(x -> x.getName().toLowerCase().contains(spellName)).findFirst().orElse(null);
        if (magicSpell != null) {
            return Rs2Magic.cast(magicSpell, option, identifier);
        }
        return false;
    }


    static boolean isTeleportSpellUsableDuringCombat(Transport transport, boolean inCombat) {
        return transport != null && (!inCombat
                || !TransportExecutionRegistry.homeTeleportFor(transport.getDisplayInfo()).isPresent());
    }


    /**
     * Equip any inventory staff/tome selected by a source-aware upstream spell requirement before
     * casting. An item merely present in the inventory never acts as an infinite rune provider.
     */
    private static boolean prepareTeleportSpellProviders(Transport transport) {
        List<TransportItemRequirement> requirements = transport.getItemRequirements();
        if (requirements == null || requirements.isEmpty()) {
            return true;
        }

        Map<Integer, Integer> runeQuantities = new HashMap<>();
        net.runelite.client.plugins.microbot.droefficientwalker.support.TravelRunes.getRunes().forEach((rune, quantity) ->
                runeQuantities.put(rune.getItemId(), quantity));
        java.util.function.IntUnaryOperator currentQuantity = itemId -> {
            Runes rune = Runes.byItemId(itemId);
            if (rune != null) {
                return runeQuantities.getOrDefault(itemId, 0);
            }
            int quantity = Rs2Inventory.itemQuantity(itemId);
            return quantity + net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.wornItems().getOrDefault(itemId, 0);
        };

        TransportItemRequirement.ProviderSelection providers =
                TransportItemRequirement.selectProviders(
                        requirements,
                        currentQuantity,
                        itemId -> net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId),
                        itemId -> net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId))
                        .orElse(null);
        if (providers == null) {
            return false;
        }
        if (!equipTransportProvider(providers.getStaffItemId())
                || !equipTransportProvider(providers.getOffhandItemId())) {
            return false;
        }

        Map<Integer, Integer> verifiedRuneQuantities = new HashMap<>();
        net.runelite.client.plugins.microbot.droefficientwalker.support.TravelRunes.getRunes().forEach((rune, quantity) ->
                verifiedRuneQuantities.put(rune.getItemId(), quantity));
        return TransportItemRequirement.selectProviders(
                requirements,
                itemId -> {
                    Runes rune = Runes.byItemId(itemId);
                    if (rune != null) {
                        return verifiedRuneQuantities.getOrDefault(itemId, 0);
                    }
                    int quantity = Rs2Inventory.itemQuantity(itemId);
                    return quantity + net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.wornItems().getOrDefault(itemId, 0);
                },
                net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment::isWearing,
                net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment::isWearing).isPresent();
    }


    private static boolean equipTransportProvider(int itemId) {
        if (itemId <= 0 || net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.isWearing(itemId)) {
            return true;
        }
        return Rs2Inventory.hasItem(itemId)
                && Rs2Inventory.wield(itemId)
                && sleepUntil(() -> net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment.isWearing(itemId), 3000);
    }


    static boolean isTeleportAllowedAtWildernessLevel(int currentLevel, int maximumLevel) {
        return currentLevel <= maximumLevel;
    }


    /**
     * Tries configured seasonal transport handlers for the same {@link Transport} row.
     * Attempt recording is done inside each handler (for built-ins, {@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransportResult})
     * — use {@link #attemptObservedWithoutAttemptRecord} at the call site.
     */
    private static boolean handleSeasonalTransport(Transport transport) {
        if (transport == null) {
            return false;
        }
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null) return false;

        List<SeasonalTransportHandler> handlers = SeasonalTransportHandlers.defaultHandlerList();
        for (SeasonalTransportHandler h : handlers)
        {
            if (h == null)
            {
                continue;
            }
            if (!h.matches(transport))
            {
                continue;
            }
            if (h.tryUse(transport))
            {
                return true;
            }
        }
        
        if (log.isDebugEnabled() && SEASONAL_HANDLER_MISS_LOGGED_COUNT.get() < SEASONAL_HANDLER_MISS_LOG_CAP)
        {
            WorldPoint destWp = transport.getDestination();
            String hash = Integer.toHexString(displayInfo.hashCode());
            String tail = displayInfo.length() > 160
                    ? displayInfo.substring(0, 160) + "|h" + hash
                    : displayInfo + "|h" + hash;
            final String missKey;
            Integer packedTileOrNull = null;
            if (destWp != null)
            {
                packedTileOrNull = WorldPointUtil.packWorldPoint(destWp);
                missKey = Integer.toHexString(packedTileOrNull) + "|" + tail;
            }
            else
            {
                missKey = "nodest|" + tail;
            }
            if (SEASONAL_HANDLER_MISS_LOGGED.add(missKey))
            {
                // Best-effort cap: only increment while below cap; duplicates and races are fine for debug-only logs.
                for (;;)
                {
                    int prev = SEASONAL_HANDLER_MISS_LOGGED_COUNT.get();
                    if (prev >= SEASONAL_HANDLER_MISS_LOG_CAP)
                    {
                        break;
                    }
                    if (SEASONAL_HANDLER_MISS_LOGGED_COUNT.compareAndSet(prev, prev + 1))
                    {
                        break;
                    }
                }
                String sample = displayInfo.length() > 160 ? displayInfo.substring(0, 160) + "…" : displayInfo;
                if (packedTileOrNull != null)
                {
                    sample = sample + " destPacked=" + Integer.toHexString(packedTileOrNull);
                }
                log.debug("[Walker] seasonal transport unmatched by configured handlers (expect pathfinder-only matching rows); key={} sample={}",
                        missKey, sample);
            }
        }
        return false;
    }


    static int canoeMapMainComponentId(int stationObjectId) {
        if (stationObjectId >= 60845 && stationObjectId <= 60849) {
            return InterfaceID.CanoeMapDougne.MAIN_MAP;
        }
        if ((stationObjectId >= 12163 && stationObjectId <= 12166) || stationObjectId == 39638) {
            return InterfaceID.CanoeMapLum.MAIN_MAP;
        }
        return -1;
    }


    static int canoeMapDestinationsComponentId(int stationObjectId) {
        if (stationObjectId >= 60845 && stationObjectId <= 60849) {
            return InterfaceID.CanoeMapDougne.DESTINATIONS;
        }
        if ((stationObjectId >= 12163 && stationObjectId <= 12166) || stationObjectId == 39638) {
            return InterfaceID.CanoeMapLum.DESTINATIONS;
        }
        return -1;
    }


    private static boolean handleCanoe(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        List<String> validActions = List.of("chop-down", "shape-canoe", "float canoe", "paddle canoe");
        ObjectComposition CANOE_COMPOSITION = SceneObjects.convertToObjectComposition(transport.getObjectId());
        if (CANOE_COMPOSITION == null) return false;

        String currentAction = Arrays.stream(CANOE_COMPOSITION.getActions())
                .filter(Objects::nonNull)
                .filter(act -> validActions.contains(act.toLowerCase())).findFirst().orElse(null);
        if (currentAction == null || currentAction.isEmpty()) {
            log.error("Unable to find canoe action");
            return false;
        }

        switch (currentAction) {
            case "Chop-down":
                SceneObjects.interact(transport.getObjectId(), "Chop-down");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = SceneObjects.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Shape-Canoe":
                @Component final int CANOE_SELECTION_PARENT = 27262976; // 416.3
                @Component final int CANOE_SHAPING_TEXT = 27262986; // 416.10

                if (!Rs2Widget.isWidgetVisible(CANOE_SELECTION_PARENT)
                        && !SceneObjects.interact(transport.getObjectId(), "Shape-Canoe")) return false;
                boolean isCanoeShapeTextVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(CANOE_SHAPING_TEXT), 100, 10000);
                if (!isCanoeShapeTextVisible) {
                    log.error("Canoe shape text is not visible within timeout period");
                    return false;
                }

                final int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                String canoeOption;
                if (woodcuttingLevel >= 57) {
                    canoeOption = "Waka canoe";
                } else if (woodcuttingLevel >= 42) {
                    canoeOption = "Stable dugout canoe";
                } else if (woodcuttingLevel >= 27) {
                    canoeOption = "Dugout canoe";
                } else if (woodcuttingLevel >= 12) {
                    canoeOption = "Log canoe";
                } else {
                    // Not high enough level to make any canoe
                    return false;
                }

                Widget canoeSelectionParentWidget = Rs2Widget.getWidget(CANOE_SELECTION_PARENT);
                if (canoeSelectionParentWidget == null) return false;
                Widget canoeSelectionWidget = Rs2Widget.findWidget("Make " + canoeOption, List.of(canoeSelectionParentWidget));
                Rs2Widget.clickWidget(canoeSelectionWidget);
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = SceneObjects.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Float Canoe":
                SceneObjects.interact(transport.getObjectId(), "Float Canoe");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = SceneObjects.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Paddle Canoe":
                int canoeMapMain = canoeMapMainComponentId(transport.getObjectId());
                int canoeMapDestinations = canoeMapDestinationsComponentId(transport.getObjectId());
                if (canoeMapMain < 0 || canoeMapDestinations < 0) {
                    log.error("Unsupported canoe station object id: {}", transport.getObjectId());
                    return false;
                }
                if (!Rs2Widget.isWidgetVisible(canoeMapMain)) {
                    if (!SceneObjects.interact(transport.getObjectId(), "Paddle Canoe")) {
                        log.error("Failed to interact with canoe station");
                        return false;
                    }
                    // Only a fresh object click needs an approach receipt.
                    sleepUntil(Rs2Player::isMoving, 2000);
                    sleepUntilTrue(() -> !Rs2Player.isMoving(), 100, 30000);
                }

                // OSRS uses separate interfaces for the River Lum and River Dougne chains.
                boolean isDestinationMapVisible = sleepUntilTrue(
                        () -> Rs2Widget.isWidgetVisible(canoeMapMain),
                        100, 10000);
                if (!isDestinationMapVisible) {
                    log.error("Canoe destination map not visible within timeout period for station {}",
                            transport.getObjectId());
                    return false;
                }

                if (!sleepUntil(() -> {
                    Widget list = Rs2Widget.getWidget(canoeMapDestinations);
                    return list != null && Rs2Widget.findWidget("Travel to " + displayInfo, List.of(list), false) != null;
                }, 5000)) return false;
                Widget destinationListWidget = Rs2Widget.getWidget(canoeMapDestinations);
                if (destinationListWidget == null) return false;
                Widget destination = Rs2Widget.findWidget("Travel to " + displayInfo, List.of(destinationListWidget), false);
                if (destination == null) {
                    log.error("Could not find canoe destination widget for: {}", displayInfo);
                    return false;
                }
                Rs2Widget.clickWidget(destination);

                Rs2Dialogue.waitForCutScene(100, 15000);
                return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET * 2), 100, 5000);
        }
        return false;
    }


    private static boolean handleMagicCarpet(Transport transport) {
        final int flyingPoseAnimation = 6936;
        if (!Rs2Dialogue.hasSelectAnOption()) {
            var rugMerchant = Rs2Npc.getNpc(transport.getObjectId());
            if (rugMerchant == null || !Rs2Npc.interact(rugMerchant, transport.getAction())) return false;
            Rs2Dialogue.sleepUntilInDialogue();
        }
        if (!sleepUntil(() -> Rs2Dialogue.hasDialogueOption(transport.getDisplayInfo(), false), 5000)
                || !Rs2Dialogue.clickOption(transport.getDisplayInfo())) return false;
        sleepUntil(() -> Rs2Player.getPoseAnimation() == flyingPoseAnimation, 10000);
        return sleepUntilTrue(() -> Rs2Player.getPoseAnimation() != flyingPoseAnimation, 600,60000);
    }


    private static boolean handleCharterShip(Transport transport) {
        String npcName = transport.getName();

        if (!Rs2Widget.isWidgetVisible(885, 4)) {
            Rs2NpcModel npc = Rs2Npc.getNpc(npcName);
            if (npc == null || !Rs2Npc.canWalkTo(npc, 20) || !Rs2Npc.interact(npc, transport.getAction())) return false;
            Rs2Player.waitForWalking();
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(885, 4), 5000)) return false;
        }
        if (!sleepUntil(() -> findCharterDestinationWidget(transport.getDisplayInfo()) != null, 5000)) return false;
        Widget destinationWidget = findCharterDestinationWidget(transport.getDisplayInfo());
        if (!invokeCharterDestinationWidget(destinationWidget, transport.getDisplayInfo())) {
            return false;
        }
        confirmCharterTravelIfPrompted();
        return true;
    }


    private static Widget findCharterDestinationWidget(String destinationText) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(885, 4);
            if (root == null || root.isHidden()) {
                return null;
            }

            Widget textMatch = findCharterDestinationTextWidget(root, destinationText);
            if (textMatch == null) {
                return null;
            }

            Widget clickable = findClickableCharterWidget(textMatch, root);
            return clickable != null ? clickable : textMatch;
        }).orElse(null);
    }


    private static Widget findCharterDestinationTextWidget(Widget widget, String destinationText) {
        if (widget == null || widget.isHidden()) {
            return null;
        }
        if (charterWidgetMatchesDestination(widget, destinationText)) {
            return widget;
        }

        Widget[] staticChildren = widget.getStaticChildren();
        Widget found = findCharterDestinationTextWidget(staticChildren, destinationText);
        if (found != null) {
            return found;
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        found = findCharterDestinationTextWidget(dynamicChildren, destinationText);
        if (found != null) {
            return found;
        }

        return findCharterDestinationTextWidget(widget.getNestedChildren(), destinationText);
    }


    private static Widget findCharterDestinationTextWidget(Widget[] widgets, String destinationText) {
        if (widgets == null) {
            return null;
        }
        for (Widget widget : widgets) {
            Widget found = findCharterDestinationTextWidget(widget, destinationText);
            if (found != null) {
                return found;
            }
        }
        return null;
    }


    private static boolean charterWidgetMatchesDestination(Widget widget, String destinationText) {
        String needle = normalizeCharterWidgetText(destinationText);
        if (needle.isEmpty()) {
            return false;
        }
        if (normalizeCharterWidgetText(widget.getText()).contains(needle)
                || normalizeCharterWidgetText(widget.getName()).contains(needle)) {
            return true;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        return Arrays.stream(actions)
                .filter(Objects::nonNull)
                .map(Rs2Walker::normalizeCharterWidgetText)
                .anyMatch(action -> action.contains(needle));
    }



    private static Widget findClickableCharterWidget(Widget widget, Widget root) {
        Widget current = widget;
        while (current != null) {
            if (hasWidgetActions(current)) {
                return current;
            }
            if (current == root) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }


    private static boolean hasWidgetActions(Widget widget) {
        String[] actions = widget.getActions();
        return actions != null && Arrays.stream(actions).anyMatch(action -> action != null && !action.isEmpty());
    }


    private static boolean invokeCharterDestinationWidget(Widget widget, String destinationText) {
        if (widget == null) {
            return false;
        }

        String option = getFirstWidgetAction(widget);
        if (option == null || option.isBlank()) {
            option = destinationText;
        }

        NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                .option(option)
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(widget.getIndex())
                .param1(widget.getId())
                .forceLeftClick(false);

        Rectangle bounds = widget.getBounds();
        Microbot.doInvoke(destinationMenuEntry, bounds != null ? bounds : Rs2UiHelper.getDefaultRectangle());
        return true;
    }


    private static String getFirstWidgetAction(Widget widget) {
        String[] actions = widget.getActions();
        if (actions == null) {
            return null;
        }
        return Arrays.stream(actions)
                .filter(action -> action != null && !action.isEmpty())
                .findFirst()
                .orElse(null);
    }


    private static void confirmCharterTravelIfPrompted() {
        if (sleepUntil(Rs2Dialogue::hasSelectAnOption, 2000)) {
            Rs2Dialogue.clickOption("Yes", true);
        }
    }


    private static boolean handleGlider(Transport transport) {
        int TA_QUIR_PRIW = 9043972;
        int SINDARPOS = 9043975;
        int LEMANTO_ANDRA = 9043978;
        int KAR_HEWO = 9043981;
        int GANDIUS = 9043984;
        int OOKOOKOLLY_UNDRI = 9043993;
        int LEMANTOLLY_UNDRI = 9043989;

        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        String npcName = transport.getName();
        String action = transport.getAction();

        final int GLIDER_PARENT_WIDGET = 138;
        final int GLIDER_CHILD_WIDGET = 0;

        // Check if the widget is already visible
        boolean isGliderMenuVisible = !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET);
        if (!isGliderMenuVisible) {
            // Find the glider NPC
            var gnome = Rs2Npc.getNpc(npcName);  // Use the NPC name to find the NPC
            if (gnome == null) {
                return false;
            }

            // Interact with the gnome glider NPC
            if (Rs2Npc.interact(gnome, action)) {
                sleepUntil(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET));
            }
        }


        // Wait for the widget to become visible
        boolean widgetVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET), Rs2Player::isMoving, 100, 10000);

        if (!widgetVisible) {
            log.error("Widget did not become visible within the timeout.");
            return false;
        }

        if (displayInfo.isEmpty()) return false;

        switch (displayInfo) {
            case "Kar-Hewo":
                return Rs2Widget.clickWidget(KAR_HEWO);
            case "Ta Quir Priw":
                return Rs2Widget.clickWidget(TA_QUIR_PRIW);
            case "Sindarpos":
                return Rs2Widget.clickWidget(SINDARPOS);
            case "Lemanto Andra":
                return Rs2Widget.clickWidget(LEMANTO_ANDRA);
            case "Gandius":
                return Rs2Widget.clickWidget(GANDIUS);
            case "Ookookolly Undri":
                return Rs2Widget.clickWidget(OOKOOKOLLY_UNDRI);
            case "Lemantolly Undri":
                return Rs2Widget.clickWidget(LEMANTOLLY_UNDRI);
            default:
                log.error("{} not found on the interface.", displayInfo);
                return false;
        }
    }
}
