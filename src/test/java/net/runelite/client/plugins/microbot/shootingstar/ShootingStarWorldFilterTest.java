package net.runelite.client.plugins.microbot.shootingstar;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarLocation;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShootingStarWorldFilterTest
{
	private static final int MEMBERS_WORLD = 302;
	private static final int F2P_WORLD = 301;
	private static final int MEMBERSHIP_DAYS = 30;

	private MockedStatic<Microbot> microbot;
	private Client client;
	private ShootingStarPlugin plugin;
	private boolean onClientThread;

	@BeforeEach
	void setUp() throws Exception
	{
		client = mock(Client.class);
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);

		ShootingStarApiClient apiClient = mock(ShootingStarApiClient.class);
		when(apiClient.getStarData(any())).thenReturn(Collections.emptyList());

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
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
		microbot.when(Microbot::getConfigManager).thenReturn(mock(ConfigManager.class));

		plugin = new ShootingStarPlugin();
		inject("config", mock(ShootingStarConfig.class));
		inject("shootingStarApiClient", apiClient);
		inject("shootingStarScript", mock(ShootingStarScript.class));
		inject("panel", mock(ShootingStarPanel.class));
		inject("hideMembersWorlds", true);
		inject("hideF2PWorlds", false);
	}

	@AfterEach
	void tearDown()
	{
		microbot.close();
	}

	@Test
	void firstLoginOnAMembersWorldShowsMembersStarsAndHidesF2pStars()
	{
		Star membersStar = star(true);
		Star f2pStar = star(false);
		plugin.getStarList().add(membersStar);
		plugin.getStarList().add(f2pStar);

		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
		verify(membersStar).setHidden(false);
		verify(f2pStar).setHidden(true);
	}

	@Test
	void hoppingToAnF2pWorldAfterLoginKeepsTheMembersFilter()
	{
		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		fire(GameState.HOPPING);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
	}

	@Test
	void returningToTheLoginScreenWithUnknownMembershipFallsBackToTheWorldType()
	{
		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		fire(GameState.LOGIN_SCREEN);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		assertTrue(plugin.isHideMembersWorlds());
		assertFalse(plugin.isHideF2PWorlds());
	}

	@Test
	void membersAccountRelogOnAnF2pWorldKeepsTheMembersFilter()
	{
		when(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT)).thenReturn(MEMBERSHIP_DAYS);
		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		fire(GameState.HOPPING);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));
		fire(GameState.LOGIN_SCREEN);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
	}

	@Test
	void membershipArrivingAfterLoginOnAnF2pWorldSwitchesToTheMembersFilter()
	{
		Star membersStar = star(true);
		Star f2pStar = star(false);
		plugin.getStarList().add(membersStar);
		plugin.getStarList().add(f2pStar);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		accountCredit(MEMBERSHIP_DAYS);

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
		verify(membersStar).setHidden(false);
		verify(f2pStar).setHidden(true);
	}

	@Test
	void accountCreditResetDuringAHopDoesNotDropTheMembersFilter()
	{
		when(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT)).thenReturn(MEMBERSHIP_DAYS);
		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		fire(GameState.HOPPING);
		when(client.getWorld()).thenReturn(F2P_WORLD);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		accountCredit(0);

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
	}

	@Test
	void accountCreditBeforeTheFirstLoginWaitsForTheLogin()
	{
		accountCredit(MEMBERSHIP_DAYS);

		assertTrue(plugin.isHideMembersWorlds());
		assertFalse(plugin.isHideF2PWorlds());
	}

	@Test
	void startUpWhileLoggedInReadsMembershipOnTheClientThread() throws Exception
	{
		inject("hideMembersWorlds", false);
		microbot.when(Microbot::isLoggedIn).thenReturn(true);
		when(client.getVarpValue(anyInt())).thenAnswer(invocation -> {
			assertTrue(onClientThread, "varp read off the client thread");
			return MEMBERSHIP_DAYS;
		});
		when(client.getWorldType()).thenAnswer(invocation -> {
			assertTrue(onClientThread, "world type read off the client thread");
			return EnumSet.noneOf(WorldType.class);
		});

		plugin.startUp();

		assertFalse(plugin.isHideMembersWorlds());
		assertTrue(plugin.isHideF2PWorlds());
	}

	@Test
	void startUpWhileLoggedOutWaitsForTheLogin() throws Exception
	{
		plugin.startUp();

		assertTrue(plugin.isHideMembersWorlds());
		assertFalse(plugin.isHideF2PWorlds());

		when(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT)).thenReturn(MEMBERSHIP_DAYS);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		assertFalse(plugin.isHideMembersWorlds());
	}

	@Test
	void removingAStarWhileTheFilterIsAppliedDoesNotBreakTheIteration()
	{
		Star first = star(true);
		Star second = star(false);
		plugin.getStarList().add(first);
		plugin.getStarList().add(second);
		doAnswer(invocation -> {
			plugin.removeStar(second);
			return null;
		}).when(first).setHidden(anyBoolean());

		assertDoesNotThrow(plugin::updateHiddenStars);
		assertFalse(plugin.getStarList().contains(second));
	}

	private void accountCredit(int days)
	{
		when(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT)).thenReturn(days);
		VarbitChanged event = new VarbitChanged();
		event.setVarpId(VarPlayerID.ACCOUNT_CREDIT);
		event.setValue(days);
		plugin.onVarbitChanged(event);
	}

	private void logInTo(int world, EnumSet<WorldType> worldTypes)
	{
		when(client.getWorld()).thenReturn(world);
		when(client.getWorldType()).thenReturn(worldTypes);
		fire(GameState.LOGGED_IN);
	}

	private void fire(GameState gameState)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(gameState);
		plugin.onGameStateChanged(event);
	}

	private void inject(String fieldName, Object value) throws Exception
	{
		Field field = ShootingStarPlugin.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private static Star star(boolean membersWorld)
	{
		Star star = mock(Star.class);
		when(star.isMemberWorld()).thenReturn(membersWorld);
		when(star.getShootingStarLocation()).thenReturn(ShootingStarLocation.DRAYNOR_VILLAGE);
		return star;
	}
}
