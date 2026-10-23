package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BankSnapshotReaderTest
{
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Bank> bank;
	private final AtomicReference<Throwable> clientThreadFailure = new AtomicReference<>();
	private BankSnapshotReader reader;

	@BeforeEach
	void setUp()
	{
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
			try
			{
				return Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call());
			}
			catch (Throwable t)
			{
				clientThreadFailure.set(t);
				return Optional.empty();
			}
		});

		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
		bank = mockStatic(Rs2Bank.class);

		reader = new BankSnapshotReader(mock(Client.class), mock(ItemManager.class));
	}

	@AfterEach
	void tearDown()
	{
		bank.close();
		microbot.close();
	}

	@Test
	void closedBankFailsOffTheClientThread()
	{
		bank.when(Rs2Bank::isOpen).thenReturn(false);

		IllegalStateException thrown = assertThrows(IllegalStateException.class, reader::read);

		assertEquals("Open your bank before running Bank Organizer.", thrown.getMessage());
		assertNull(clientThreadFailure.get());
	}

	@Test
	void openBankReturnsSnapshot()
	{
		bank.when(Rs2Bank::isOpen).thenReturn(true);
		bank.when(Rs2Bank::bankItems).thenReturn(Collections.emptyList());

		BankSnapshot snapshot = reader.read();

		assertNotNull(snapshot);
		assertEquals(0, snapshot.stackCount());
		assertNull(clientThreadFailure.get());
	}
}
