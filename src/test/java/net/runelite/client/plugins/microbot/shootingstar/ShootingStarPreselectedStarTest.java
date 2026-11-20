package net.runelite.client.plugins.microbot.shootingstar;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.stream.Stream;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarState;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ShootingStarPreselectedStarTest
{
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Inventory> inventory;
	private MockedStatic<Rs2Equipment> equipment;
	private MockedStatic<Rs2Gembag> gembag;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2Walker> walker;
	private ShootingStarPlugin plugin;
	private ShootingStarScript script;
	private Star star;

	@BeforeEach
	void setUp()
	{
		microbot = mockStatic(Microbot.class);
		inventory = mockStatic(Rs2Inventory.class);
		equipment = mockStatic(Rs2Equipment.class);
		gembag = mockStatic(Rs2Gembag.class);
		player = mockStatic(Rs2Player.class);
		walker = mockStatic(Rs2Walker.class);
		plugin = mock(ShootingStarPlugin.class);
		star = mock(Star.class);
		when(plugin.getSelectedStar()).thenReturn(star);
		script = new ShootingStarScript(plugin, mock(ShootingStarConfig.class));
		ShootingStarScript.state = null;
	}

	@AfterEach
	void tearDown()
	{
		walker.close();
		player.close();
		gembag.close();
		equipment.close();
		inventory.close();
		microbot.close();
		ShootingStarScript.state = null;
	}

	@Test
	void startingWithASelectedStarAndNoPickaxeAdoptsTheStarBeforeBanking() throws Exception
	{
		assertTrue(script.hasStateChanged());
		ShootingStarScript.state = script.updateStarState();

		assertEquals(ShootingStarState.BANKING, ShootingStarScript.state);
		assertSame(star, currentStar());
		assertFalse(script.hasStateChanged());
	}

	@Test
	void startingWithASelectedStarAndAPickaxeAdoptsTheStarBeforeWalking() throws Exception
	{
		Rs2ItemModel pickaxe = pickaxeItem();
		inventory.when(() -> Rs2Inventory.hasItem("pickaxe")).thenReturn(true);
		inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(pickaxe));
		equipment.when(Rs2Equipment::items).thenReturn(Collections.emptyList());
		player.when(() -> Rs2Player.getSkillRequirement(any(Skill.class), anyInt())).thenReturn(true);

		ShootingStarScript.state = script.updateStarState();

		assertEquals(ShootingStarState.WALKING, ShootingStarScript.state);
		assertSame(star, currentStar());
		assertFalse(script.hasStateChanged());
	}

	@Test
	void missingCurrentStarWhileWalkingIsAStateChangeThatAdoptsTheSelectedStar() throws Exception
	{
		ShootingStarScript.state = ShootingStarState.WALKING;

		assertTrue(script.hasStateChanged());
		assertEquals(ShootingStarState.WALKING, script.updateStarState());
		assertSame(star, currentStar());
	}

	@Test
	void missingCurrentStarWhileBankingIsAStateChangeThatAdoptsTheSelectedStar() throws Exception
	{
		ShootingStarScript.state = ShootingStarState.BANKING;

		assertTrue(script.hasStateChanged());
		assertEquals(ShootingStarState.WALKING, script.updateStarState());
		assertSame(star, currentStar());
	}

	private Object currentStar() throws ReflectiveOperationException
	{
		Field field = ShootingStarScript.class.getDeclaredField("currentStar");
		field.setAccessible(true);
		return field.get(script);
	}

	private static Rs2ItemModel pickaxeItem()
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getId()).thenReturn(ItemID.BRONZE_PICKAXE);
		return item;
	}
}
