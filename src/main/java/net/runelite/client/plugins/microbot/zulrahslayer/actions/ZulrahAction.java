package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.client.plugins.microbot.actions.Action;

/**
 * An {@link Action} bound to {@link ZulrahState}, so Zulrah actions get typed access to the
 * {@link FightContext} without a cast. Concrete Zulrah actions implement this rather than
 * {@code Action} directly.
 */
public interface ZulrahAction extends Action<ZulrahState> {
}
