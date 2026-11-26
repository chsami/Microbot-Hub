package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.droefficientwalker.support.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShantayPassAccessTest
{
	private static final WorldPoint NORTH = new WorldPoint(3304, 3117, 0);
	private static final WorldPoint SOUTH = new WorldPoint(3304, 3115, 0);

	@Test
	void southboundPassRequiresCarriedPassFiveCoinsOrEliteDiary()
	{
		Transport southMain = new Transport(NORTH, SOUTH, "Shantay", TransportType.TRANSPORT, false, "Go-through", "Shantay pass", 4031);
		Transport northMain = new Transport(SOUTH, NORTH, "Shantay", TransportType.TRANSPORT, false, "Go-through", "Shantay pass", 4031);
		Transport southUnkah = new Transport(new WorldPoint(3193, 2843, 0), new WorldPoint(3196, 2843, 0),
			"Unkah", TransportType.TRANSPORT, false, "Go-through", "Shantay pass", 41326);

		// Northbound is always free
		assertTrue(ShantayPassAccess.requirementsSatisfied(northMain, false, 0, 0));
		// Southbound blocked with nothing
		assertFalse(ShantayPassAccess.requirementsSatisfied(southMain, false, 0, 0));
		assertFalse(ShantayPassAccess.requirementsSatisfied(southMain, false, 0, 4));
		// Southbound allowed with carried pass, 5 coins at main pass, or Elite Diary
		assertTrue(ShantayPassAccess.requirementsSatisfied(southMain, false, 1, 0));
		assertTrue(ShantayPassAccess.requirementsSatisfied(southMain, false, 0, 5));
		assertTrue(ShantayPassAccess.requirementsSatisfied(southMain, true, 0, 0));
		// Ruins of Unkah pass (41326) has no Shantay vendor beside it, so coins alone do not satisfy it
		assertFalse(ShantayPassAccess.requirementsSatisfied(southUnkah, false, 0, 5));
		assertTrue(ShantayPassAccess.requirementsSatisfied(southUnkah, false, 1, 0));
		assertTrue(ShantayPassAccess.requirementsSatisfied(southUnkah, true, 0, 0));
	}

	@Test
	void detectsAndAcceptsShantayDesertWarningWidget565Child17()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		ClientThread thread = mock(ClientThread.class);
		Widget yes = mock(Widget.class);
		when(yes.getId()).thenReturn((565 << 16) | 17);
		when(yes.getIndex()).thenReturn(-1);
		when(yes.isHidden()).thenReturn(false);
		when(yes.isIf3()).thenReturn(true);
		when(yes.getActions()).thenReturn(new String[]{"Yes"});
		when(yes.getText()).thenReturn("Yes");
		when(yes.getBounds()).thenReturn(new Rectangle(200, 260, 80, 22));
		when(client.getWidget(565, 17)).thenReturn(yes);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(NORTH);
		when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> {
			Callable<?> c = call.getArgument(0);
			return Optional.ofNullable(c.call());
		});

		Transport southMain = new Transport(NORTH, SOUTH, "Shantay", TransportType.TRANSPORT, false, "Go-through", "Shantay pass", 4031);
		AtomicReference<NewMenuEntry> invoked = new AtomicReference<>();

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			microbot.when(Microbot::getClientThread).thenReturn(thread);
			microbot.when(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class))).thenAnswer(call -> {
				invoked.set(call.getArgument(0));
				return null;
			});

			ShantayPassAccess.Warning warning = ShantayPassAccess.warning(client);
			assertEquals(565, warning.group);
			assertNotNull(warning.selection);
			assertTrue(ShantayPassAccess.acceptWarning(client, southMain, warning));
			assertNotNull(invoked.get());
			assertEquals(MenuAction.CC_OP, invoked.get().getType());
			assertEquals((565 << 16) | 17, invoked.get().getParam1());
			assertEquals(1, invoked.get().getIdentifier());
			assertEquals("Yes", invoked.get().getOption());
		}
	}

	@Test
	void recoverableBlockedPreviewCoversPlanUnavailableClearedDestination()
	{
		assertTrue(EfficientWalker.isRecoverableBlockedPreview(
			EfficientWalker.Status.BLOCKED, null, EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED));
		assertTrue(EfficientWalker.isRecoverableBlockedPreview(
			EfficientWalker.Status.BLOCKED, null, EfficientWalker.PlanningFailure.SEARCH_LIMIT));
		assertFalse(EfficientWalker.isRecoverableBlockedPreview(
			EfficientWalker.Status.BLOCKED, NORTH, EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED));
		assertFalse(EfficientWalker.isRecoverableBlockedPreview(
			EfficientWalker.Status.BLOCKED, null, EfficientWalker.PlanningFailure.NONE));
		assertFalse(EfficientWalker.isRecoverableBlockedPreview(
			EfficientWalker.Status.PREVIEW, null, EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED));
	}

	@Test
	void bankingPlannerSubtractsCarriedCoinsForCurrencyAndPurchasableShantayRows() throws Exception
	{
		Rs2TransportEdge coinEdge = new Rs2TransportEdge(
			NORTH, SOUTH, Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT, Rs2TerminalTravelMode.UNSUPPORTED,
			"", "Go-through", "Shantay pass", 4031, 2, false, false, false, 0, "Coins", 5, List.of());
		Rs2TransportEdge passEdge = new Rs2TransportEdge(
			NORTH, SOUTH, Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT, Rs2TerminalTravelMode.UNSUPPORTED,
			"", "Go-through", "Shantay pass", 4031, 2, false, false, false, 0, "", 0,
			List.of(new Rs2TransportItemRequirement(Map.of(ItemID.SHANTAY_PASS, 1))));

		var method = Rs2WalkerBankingPlanner.class.getDeclaredMethod(
			"getMissingTransportEdgeLoadout",
			List.class,
			java.util.function.IntUnaryOperator.class,
			java.util.function.IntUnaryOperator.class,
			java.util.function.IntUnaryOperator.class,
			java.util.function.IntUnaryOperator.class,
			java.util.function.IntPredicate.class);
		method.setAccessible(true);

		// Already carrying 10 coins with 0 in bank -> satisfiable with 0 withdrawal
		Rs2TransportLoadout carriedCoins = (Rs2TransportLoadout) method.invoke(
			null, List.of(coinEdge),
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 10 : 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 10 : 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntPredicate) id -> false);
		assertTrue(carriedCoins.isSatisfiable());
		assertTrue(carriedCoins.getWithdrawals().isEmpty());

		// Carrying 2 coins with 100 in bank -> withdraws exact 3-coin deficit
		Rs2TransportLoadout partialCoins = (Rs2TransportLoadout) method.invoke(
			null, List.of(coinEdge),
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 100 : 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 2 : 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 2 : 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 100 : 0,
			(java.util.function.IntPredicate) id -> false);
		assertTrue(partialCoins.isSatisfiable());
		assertEquals(3, partialCoins.getWithdrawals().get(ItemID.COINS));

		// Shantay pass row with 0 passes in bank and 100 coins in bank -> withdraws 5 coins via PurchasableItemCatalog
		Rs2TransportLoadout purchasableFallback = (Rs2TransportLoadout) method.invoke(
			null, List.of(passEdge),
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 100 : 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> id == ItemID.COINS ? 100 : 0,
			(java.util.function.IntPredicate) id -> false);
		assertTrue(purchasableFallback.isSatisfiable());
		assertEquals(5, purchasableFallback.getWithdrawals().get(ItemID.COINS));

		// Shantay pass row with 0 passes and 0 coins in bank -> unavailable
		Rs2TransportLoadout unaffordable = (Rs2TransportLoadout) method.invoke(
			null, List.of(passEdge),
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntUnaryOperator) id -> 0,
			(java.util.function.IntPredicate) id -> false);
		assertFalse(unaffordable.isSatisfiable());
	}
}
