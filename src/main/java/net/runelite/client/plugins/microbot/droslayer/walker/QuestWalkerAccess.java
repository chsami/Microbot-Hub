package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;

/**
 * Compile-checked entry points for Dro's scripted quests ({@code droquester}), which reached these
 * package-private walker members through reflection before. Forwarding only: no walker behaviour lives here.
 */
public final class QuestWalkerAccess
{
	private QuestWalkerAccess()
	{
	}

	/** Identity of the walk the engine currently owns; changes whenever another request takes over. */
	public static Object requestIdentity(EfficientWalker walker)
	{
		return walker.getRequestIdentity();
	}

	public static boolean followSuppliedRoute(EfficientWalker walker, List<WorldPoint> route)
	{
		return walker.followSuppliedRoute(route);
	}

	public static List<WorldPoint> tilesAround(WorldPoint origin, int radius)
	{
		return SceneInteractionTiles.tilesAround(origin, radius);
	}

	public static List<WorldPoint> approachTiles(Client client, WorldPoint point)
	{
		return SceneInteractionTiles.findApproachTiles(client, point);
	}

	public static List<WorldPoint> approachTiles(Client client, TileObject object)
	{
		return SceneInteractionTiles.findApproachTiles(client, object);
	}

	public static Navigator navigator(Object selectedQuest, EfficientWalker walker)
	{
		return new Navigator(new QuestStepNavigator(selectedQuest, walker));
	}

	/** One delegated Quest Helper step driven by the walker's quest-step navigator. */
	public static final class Navigator
	{
		private final QuestStepNavigator delegate;

		private Navigator(QuestStepNavigator delegate)
		{
			this.delegate = delegate;
		}

		public boolean tick(Client client, PluginManager manager, EfficientWalker walker)
		{
			return delegate.tickQuestStep(client, manager, walker);
		}

		public void clearSelectedItem(Client client)
		{
			delegate.clearSelectedItem(client);
		}

		public void onWidgetGroupLoaded(int tick)
		{
			delegate.onWidgetGroupLoaded(tick);
		}

		public boolean isCurrentStepFailure(String message, EfficientWalker walker)
		{
			return delegate.isCurrentStepFailure(message, walker);
		}
	}
}
