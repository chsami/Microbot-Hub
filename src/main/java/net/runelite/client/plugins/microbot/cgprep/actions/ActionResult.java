package net.runelite.client.plugins.microbot.cgprep.actions;

/**
 * What an action actually observed. Never "true because we clicked": CLICKED means the click went
 * out and the expected reaction (animation, movement, item change) was seen within the wait.
 */
public enum ActionResult
{
	/** Goal for this action is satisfied (e.g. item now in inventory). */
	DONE,
	/** Already busy doing it (animating / walking there); nothing sent this tick. */
	IN_PROGRESS,
	/** Sent one action and saw the expected reaction start. */
	CLICKED,
	/** Sent an action but the expected reaction never happened (caller may retry). */
	NO_REACTION,
	/** Target doesn't exist / isn't known (caller should re-plan). */
	NOT_AVAILABLE,
	/** Walking towards the target. */
	WALKING,
	/** Can't path to the target. */
	NO_PATH
}
