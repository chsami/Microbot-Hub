package net.runelite.client.plugins.microbot.fourtifysagility.util.inventory;

/** Counts observed inventory gains during eligible parts of an enabled session. */
public class InventoryGainTracker
{
    public enum Baseline
    {
        HIGHEST_OBSERVED_COUNT, PREVIOUS_SNAPSHOT
    }

    private final Baseline baseline;
    private volatile int collected;
    private int highestQuantity;
    private boolean baselined;

    public InventoryGainTracker()
    {
        this(Baseline.HIGHEST_OBSERVED_COUNT);
    }

    public InventoryGainTracker(Baseline baseline)
    {
        this.baseline = baseline;
    }

    public void reset()
    {
        collected = 0;
        suspend();
    }

    /** Discards an unavailable inventory snapshot without resetting session progress. */
    public void suspend()
    {
        baselined = false;
    }

    /**
    * Observes a valid inventory quantity. Ineligible observations establish a new
    * baseline, so bank transfers and off-course inventory changes are excluded.
    * In HIGHEST_OBSERVED_COUNT mode, removals do not lower the baseline: replacing those
    * items is not a new gain. This can undercount pickups after an on-course removal.
    */
    public void observe(int quantity, boolean eligible)
    {
        if (quantity < 0)
        {
            suspend();
            return;
        }

        if (!baselined || !eligible)
        {
            highestQuantity = quantity;
            baselined = true;
            return;
        }

        if (quantity > highestQuantity)
        {
            collected = (int) Math.min(Integer.MAX_VALUE,
                                        (long) collected + quantity - highestQuantity);
            highestQuantity = quantity;
        }
        if (baseline == Baseline.PREVIOUS_SNAPSHOT)
        {
            highestQuantity = quantity;
        }
    }

    public int getCollected()
    {
        return collected;
    }
}
