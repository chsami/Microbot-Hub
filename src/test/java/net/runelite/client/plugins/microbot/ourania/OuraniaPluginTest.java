package net.runelite.client.plugins.microbot.ourania;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OuraniaPluginTest
{
	@Test
	void itemContainerChangedWithoutLocalPlayerDoesNotThrow()
	{
		Client client = mock(Client.class);
		OuraniaPlugin plugin = new OuraniaPlugin();
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			assertDoesNotThrow(() -> plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, mock(ItemContainer.class))));
		}
	}

	@Test
	void itemContainerChangedWithoutPlayerLocationDoesNotThrow()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		OuraniaPlugin plugin = new OuraniaPlugin();
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			assertDoesNotThrow(() -> plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, mock(ItemContainer.class))));
		}
	}
}
