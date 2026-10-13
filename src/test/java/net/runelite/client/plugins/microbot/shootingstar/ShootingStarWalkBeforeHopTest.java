package net.runelite.client.plugins.microbot.shootingstar;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarLocation;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarState;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ShootingStarWalkBeforeHopTest
{
	private static final int MEMBERS_WORLD = 302;
	private static final int STAR_WORLD = 301;
	private static final int OTHER_STAR_WORLD = 308;
	private static final ShootingStarLocation LOCATION = ShootingStarLocation.DRAYNOR_VILLAGE;
	private static final WorldPoint FAR_AWAY = new WorldPoint(2605, 3092, 0);
	private static final WorldPoint AT_STAR = LOCATION.getWorldPoint().dx(2);

	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2Walker> walker;
	private ShootingStarScript script;
	private Client client;
	private boolean onClientThread;

	@BeforeEach
	void setUp()
	{
		microbot = mockStatic(Microbot.class);
		player = mockStatic(Rs2Player.class);
		walker = mockStatic(Rs2Walker.class);
		client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
			onClientThread = true;
			try
			{
				return Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call());
			}
			finally
			{
				onClientThread = false;
			}
		});
		microbot.when(Microbot::getClient).thenReturn(client);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.MOVING);
		player.when(Rs2Player::getWorld).thenReturn(MEMBERS_WORLD);
		ShootingStarPlugin plugin = mock(ShootingStarPlugin.class);
		script = new ShootingStarScript(plugin, mock(ShootingStarConfig.class));
		ShootingStarScript.state = ShootingStarState.WALKING;
		Microbot.cantHopWorld = false;
	}

	@AfterEach
	void tearDown()
	{
		walker.close();
		player.close();
		microbot.close();
		ShootingStarScript.state = null;
		Microbot.cantHopWorld = false;
	}

	@Test
	void f2pStarFromMembersWorldWalksToTheStarInsteadOfHopping()
	{
		standAt(FAR_AWAY, true);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
		walker.verify(() -> Rs2Walker.walkWithState(LOCATION.getWorldPoint(), 6));
	}

	@Test
	void f2pStarHopsOnceThePlayerReachedTheStar()
	{
		standAt(AT_STAR, true);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
		assertEquals(ShootingStarState.WALKING, ShootingStarScript.state);
	}

	@Test
	void membersStarStillHopsBeforeWalking()
	{
		standAt(FAR_AWAY, true);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);

		script.handleWalking(star(true));

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	void failedHopToF2pWorldMakesTheNextLoopWalkInsteadOfRetryingTheHop()
	{
		standAt(FAR_AWAY, false);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(false);
		Star star = star(false);

		script.handleWalking(star);
		script.handleWalking(star);

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD), times(1));
		walker.verify(() -> Rs2Walker.walkWithState(LOCATION.getWorldPoint(), 6), times(1));
	}

	@Test
	void staleMembersAreaRefusalIsClearedOnceAtTheF2pStar()
	{
		standAt(AT_STAR, true);
		Microbot.cantHopWorld = true;
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenAnswer(invocation -> !Microbot.cantHopWorld);

		script.handleWalking(star(false));

		assertFalse(Microbot.cantHopWorld);
		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
	}

	@Test
	void onTheStarWorldNextToTheStarStartsMining()
	{
		standAt(AT_STAR, false);
		player.when(Rs2Player::getWorld).thenReturn(STAR_WORLD);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
		assertEquals(ShootingStarState.MINING, ShootingStarScript.state);
	}

	@Test
	void standingExactlyOnTheWalkerArrivalDistanceHopsInsteadOfWalkingAgain()
	{
		standAt(LOCATION.getWorldPoint().dx(ShootingStarScript.STAR_ARRIVAL_DISTANCE), true);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.ARRIVED);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	void standingExactlyOnTheWalkerArrivalDistanceOnTheStarWorldStartsMining()
	{
		standAt(LOCATION.getWorldPoint().dx(ShootingStarScript.STAR_ARRIVAL_DISTANCE), false);
		player.when(Rs2Player::getWorld).thenReturn(STAR_WORLD);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.ARRIVED);

		script.handleWalking(star(false));

		assertEquals(ShootingStarState.MINING, ShootingStarScript.state);
	}

	@Test
	void walkerReportingArrivedOutsideTheRadiusHopsInsteadOfLoopingOnTheWalker()
	{
		standAt(LOCATION.getWorldPoint().dx(ShootingStarScript.STAR_ARRIVAL_DISTANCE + 1), true);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.ARRIVED);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);

		script.handleWalking(star(false));

		walker.verify(() -> Rs2Walker.walkWithState(LOCATION.getWorldPoint(), ShootingStarScript.STAR_ARRIVAL_DISTANCE), times(1));
		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
	}

	@Test
	void membersWorldIsReadFromTheClientWorldTypeNotTheHttpWorldList()
	{
		standAt(FAR_AWAY, true);
		player.when(Rs2Player::isInMemberWorld).thenReturn(false);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
		walker.verify(() -> Rs2Walker.walkWithState(LOCATION.getWorldPoint(), ShootingStarScript.STAR_ARRIVAL_DISTANCE));
	}

	@Test
	void f2pWorldTypeHopsEvenWhenTheHttpWorldListSaysMembers()
	{
		standAt(FAR_AWAY, false);
		player.when(Rs2Player::isInMemberWorld).thenReturn(true);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);

		script.handleWalking(star(false));

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	void walkerArrivedOnTheStarWorldFarFromTheStarKeepsWalkingInsteadOfMining()
	{
		standAt(FAR_AWAY, false);
		player.when(Rs2Player::getWorld).thenReturn(STAR_WORLD);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.ARRIVED);

		script.handleWalking(star(false));

		assertEquals(ShootingStarState.WALKING, ShootingStarScript.state);
		microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
	}

	@Test
	void walkerArrivedOnTheStarWorldNextToTheStarStartsMining()
	{
		standAt(FAR_AWAY, false);
		player.when(Rs2Player::getWorld).thenReturn(STAR_WORLD);
		walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenAnswer(invocation -> {
			player.when(Rs2Player::getWorldLocation).thenReturn(AT_STAR);
			return WalkerState.ARRIVED;
		});

		script.handleWalking(star(false));

		assertEquals(ShootingStarState.MINING, ShootingStarScript.state);
	}

	@Test
	void walkBeforeHopResetsAfterASuccessfulHop()
	{
		standAt(FAR_AWAY, false);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(false);
		Star star = star(false);
		script.handleWalking(star);

		standAt(AT_STAR, false);
		microbot.when(() -> Microbot.hopToWorld(STAR_WORLD)).thenReturn(true);
		script.handleWalking(star);

		standAt(FAR_AWAY, false);
		script.handleWalking(star);

		microbot.verify(() -> Microbot.hopToWorld(STAR_WORLD), times(3));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	void walkBeforeHopFromAFailedHopDoesNotCarryOverToTheNextStar()
	{
		standAt(FAR_AWAY, false);
		microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(false);
		script.handleWalking(star(false));

		Star nextStar = star(false, OTHER_STAR_WORLD);
		script.handleWalking(nextStar);

		microbot.verify(() -> Microbot.hopToWorld(OTHER_STAR_WORLD));
		walker.verify(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt()), never());
	}

	private void standAt(WorldPoint location, boolean onMembersWorld)
	{
		player.when(Rs2Player::getWorldLocation).thenReturn(location);
		EnumSet<WorldType> worldTypes = onMembersWorld ? EnumSet.of(WorldType.MEMBERS) : EnumSet.noneOf(WorldType.class);
		doAnswer(invocation -> {
			if (!onClientThread)
			{
				throw new AssertionError("world type read off the client thread");
			}
			return worldTypes;
		}).when(client).getWorldType();
	}

	private static Star star(boolean membersWorld)
	{
		return star(membersWorld, STAR_WORLD);
	}

	private static Star star(boolean membersWorld, int world)
	{
		Star star = mock(Star.class);
		when(star.getWorld()).thenReturn(world);
		when(star.isMemberWorld()).thenReturn(membersWorld);
		when(star.getShootingStarLocation()).thenReturn(LOCATION);
		return star;
	}
}
