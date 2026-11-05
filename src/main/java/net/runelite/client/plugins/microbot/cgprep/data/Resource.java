package net.runelite.client.plugins.microbot.cgprep.data;

/** Raw inputs the planner shops for. */
public enum Resource
{
	SHARDS("Shards", Source.MONSTERS),
	ORE("Ore", Source.NODE),
	BARK("Bark", Source.NODE),
	LINUM("Linum", Source.NODE),
	GRYM("Grym leaf", Source.NODE),
	RAW_FISH("Raw paddlefish", Source.NODE),
	FRAME("Weapon frame", Source.MONSTERS),
	BOWSTRING("Bowstring", Source.DEMIBOSS),
	ORB("Orb", Source.DEMIBOSS),
	SPIKE("Spike", Source.DEMIBOSS);

	public enum Source
	{
		NODE, MONSTERS, DEMIBOSS
	}

	public final String label;
	public final Source source;

	Resource(String label, Source source)
	{
		this.label = label;
		this.source = source;
	}
}
