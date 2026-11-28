package net.runelite.client.plugins.microbot.cgprep.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class FightRunTest
{
	private static final int DEFAULT_THRESHOLD = 1000;

	@Test
	void runOffWithEnergyAboveThresholdIsTurnedBackOn()
	{
		assertTrue(BossFight.shouldRestoreRun(false, 5000, DEFAULT_THRESHOLD, 10_000, 0));
		assertTrue(BossFight.shouldRestoreRun(false, DEFAULT_THRESHOLD + 1, DEFAULT_THRESHOLD, 10_000, 0));
	}

	@Test
	void runAlreadyOnIsLeftAlone()
	{
		assertFalse(BossFight.shouldRestoreRun(true, 10_000, DEFAULT_THRESHOLD, 10_000, 0));
	}

	@Test
	void energyAtOrBelowClientThresholdIsLeftAlone()
	{
		assertFalse(BossFight.shouldRestoreRun(false, DEFAULT_THRESHOLD, DEFAULT_THRESHOLD, 10_000, 0));
		assertFalse(BossFight.shouldRestoreRun(false, 300, DEFAULT_THRESHOLD, 10_000, 0));
	}

	@Test
	void negativeThresholdStillNeedsSomeEnergy()
	{
		assertFalse(BossFight.shouldRestoreRun(false, 0, -1, 10_000, 0));
		assertTrue(BossFight.shouldRestoreRun(false, 1, -1, 10_000, 0));
	}

	@Test
	void retriesWaitBetweenClicks()
	{
		long last = 10_000;
		assertFalse(BossFight.shouldRestoreRun(false, 5000, DEFAULT_THRESHOLD, last + BossFight.RUN_RETRY_MS, last));
		assertTrue(BossFight.shouldRestoreRun(false, 5000, DEFAULT_THRESHOLD, last + BossFight.RUN_RETRY_MS + 1, last));
	}

	@Test
	void walkFallbackNeverTurnsRunOff() throws IOException
	{
		Path src = Paths.get("src/main/java/net/runelite/client/plugins/microbot/cgprep/boss/BossFight.java");
		String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
		assertTrue(text.contains("walkFastCanvas("));
		assertFalse(text.matches("(?s).*walkFastCanvas\\([^;]*,\\s*false\\).*"));
	}
}
