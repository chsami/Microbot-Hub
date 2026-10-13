package net.runelite.client.plugins.microbot.shootingstar;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarLocation;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShootingStarWorldFilterTest
{
	private static final int MEMBERS_WORLD = 302;
	private static final int F2P_WORLD = 301;

	private MockedStatic<Microbot> microbot;
	private Client client;
	private ShootingStarPlugin plugin;

	@BeforeEach
	void setUp() throws Exception
	{
		client = mock(Client.class);
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);

		ShootingStarApiClient apiClient = mock(ShootingStarApiClient.class);
		when(apiClient.getStarData(any())).thenReturn(Collections.emptyList());

		plugin = new ShootingStarPlugin();
		inject("config", mock(ShootingStarConfig.class));
		inject("shootingStarApiClient", apiClient);
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
	void returningToTheLoginScreenRecomputesTheFilterOnTheNextLogin()
	{
		logInTo(MEMBERS_WORLD, EnumSet.of(WorldType.MEMBERS));

		fire(GameState.LOGIN_SCREEN);
		logInTo(F2P_WORLD, EnumSet.noneOf(WorldType.class));

		assertTrue(plugin.isHideMembersWorlds());
		assertFalse(plugin.isHideF2PWorlds());
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
