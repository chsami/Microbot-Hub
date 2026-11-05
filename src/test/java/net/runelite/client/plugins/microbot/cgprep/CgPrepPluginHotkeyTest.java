package net.runelite.client.plugins.microbot.cgprep;

import java.lang.reflect.Field;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.util.HotkeyListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CgPrepPluginHotkeyTest
{
	@Test
	void pauseHotkeyQueuesChatMessageWithoutWaitingOnClientThread() throws Exception
	{
		assertQueuesChatMessage("pauseListener", "Paused.");
	}

	@Test
	void continueHotkeyQueuesChatMessageWithoutWaitingOnClientThread() throws Exception
	{
		assertQueuesChatMessage("continueListener", "Next run requested");
	}

	private static void assertQueuesChatMessage(String listenerField, String expectedText) throws Exception
	{
		CgPrepScript script = mock(CgPrepScript.class);
		script.paused = true;
		CgPrepPlugin plugin = new CgPrepPlugin();
		set(plugin, "config", mock(CgPrepConfig.class));
		set(plugin, "script", script);
		ClientThread clientThread = mock(ClientThread.class);
		Client client = mock(Client.class);

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			microbot.when(Microbot::getClientThread).thenReturn(clientThread);
			microbot.when(Microbot::getClient).thenReturn(client);

			listener(plugin, listenerField).hotkeyPressed();

			verify(clientThread, never()).invoke(any(Supplier.class));
			verify(clientThread, never()).invoke(any(Runnable.class));
			verifyNoInteractions(client);
			ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
			verify(clientThread).invokeLater(queued.capture());

			queued.getValue().run();

			verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains(expectedText), isNull());
		}
	}

	private static HotkeyListener listener(CgPrepPlugin plugin, String name) throws Exception
	{
		Field f = CgPrepPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return (HotkeyListener) f.get(plugin);
	}

	private static void set(CgPrepPlugin plugin, String name, Object value) throws Exception
	{
		Field f = CgPrepPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
