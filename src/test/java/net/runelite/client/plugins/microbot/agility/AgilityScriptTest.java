package net.runelite.client.plugins.microbot.agility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgilityScriptTest
{
	@Test
	void detectedMarkIsNotDeferredWhilePlayerIsMoving()
	{
		assertFalse(AgilityScript.shouldWaitForMarkPickup(
			AgilityScript.MarkPickupPhase.DETECTED, true, false));
	}

	@Test
	void detectedMarkIsNotDeferredWhilePlayerIsAnimating()
	{
		assertFalse(AgilityScript.shouldWaitForMarkPickup(
			AgilityScript.MarkPickupPhase.DETECTED, false, true));
	}

	@Test
	void pendingInteractionKeepsCoursePausedWhilePlayerIsMoving()
	{
		assertTrue(AgilityScript.shouldWaitForMarkPickup(
			AgilityScript.MarkPickupPhase.INTERACTION_PENDING, true, false));
	}
}
