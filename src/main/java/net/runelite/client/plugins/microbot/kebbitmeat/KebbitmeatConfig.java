package net.runelite.client.plugins.microbot.kebbitmeat;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kebbitmeat")
public interface KebbitmeatConfig extends Config {

	enum HuntingLocation {
		PISCATORIS("Piscatoris Hunter Area", new WorldPoint(2335, 3584, 0)),
		AUBURNVALE("Auburnvale", new WorldPoint(1387, 3392, 0));

		private final String displayName;
		private final WorldPoint worldPoint;

		HuntingLocation(String displayName, WorldPoint worldPoint) {
			this.displayName = displayName;
			this.worldPoint = worldPoint;
		}

		public WorldPoint getWorldPoint() {
			return worldPoint;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	@ConfigItem(
		keyName = "huntingLocation",
		name = "Hunting Location",
		description = "Where to hunt wild kebbits (bank run always goes to Auburnvale)"
	)
	default HuntingLocation huntingLocation() {
		return HuntingLocation.AUBURNVALE;
	}
}
