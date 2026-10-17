package net.runelite.client.plugins.microbot.shootingstar;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarState;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShootingStarStopRaceTest
{
	private final AtomicReference<Throwable> edtFailure = new AtomicReference<>();
	private Thread.UncaughtExceptionHandler previousHandler;
	private ShootingStarPlugin plugin;

	@BeforeEach
	void setUp()
	{
		previousHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> edtFailure.set(throwable));
		plugin = new ShootingStarPlugin();
	}

	@AfterEach
	void tearDown()
	{
		Thread.setDefaultUncaughtExceptionHandler(previousHandler);
	}

	@Test
	void updatePanelListIsNoOpWithoutPanel() throws Exception
	{
		plugin.updatePanelList(true);
		plugin.updatePanelList(false);
		flushEdt();

		assertNull(edtFailure.get());
	}

	@Test
	void queuedPanelUpdateSkipsRemovedPanel() throws Exception
	{
		ShootingStarPanel panel = mock(ShootingStarPanel.class);
		setPanel(panel);

		SwingUtilities.invokeAndWait(() -> {
			plugin.updatePanelList(true);
			plugin.updatePanelList(false);
			setPanelUnchecked(null);
		});
		flushEdt();

		assertNull(edtFailure.get());
		verify(panel, never()).updateList(anyList());
		verify(panel, never()).refreshList(anyList());
	}

	@Test
	void queuedPanelUpdateReachesLivePanel() throws Exception
	{
		ShootingStarPanel panel = mock(ShootingStarPanel.class);
		setPanel(panel);

		plugin.updatePanelList(true);
		plugin.updatePanelList(false);
		flushEdt();

		assertNull(edtFailure.get());
		verify(panel).updateList(anyList());
		verify(panel).refreshList(anyList());
	}

	@Test
	void missingAntibanCategoryIsNotBusy()
	{
		assertFalse(ShootingStarScript.isAntibanBusy(null));
	}

	@Test
	void stoppingScriptKeepsLoopStateForInFlightIteration() throws Exception
	{
		ShootingStarScript script = new ShootingStarScript(plugin, mock(ShootingStarConfig.class));
		Star star = mock(Star.class);
		Field currentStar = ShootingStarScript.class.getDeclaredField("currentStar");
		currentStar.setAccessible(true);
		currentStar.set(script, star);
		ShootingStarScript.state = ShootingStarState.MINING;

		try
		{
			script.shutdown();

			assertSame(ShootingStarState.MINING, ShootingStarScript.state);
			assertSame(star, currentStar.get(script));
		}
		finally
		{
			ShootingStarScript.state = null;
		}
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}

	private void setPanelUnchecked(ShootingStarPanel value)
	{
		try
		{
			setPanel(value);
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private void setPanel(ShootingStarPanel value) throws ReflectiveOperationException
	{
		Field field = ShootingStarPlugin.class.getDeclaredField("panel");
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
