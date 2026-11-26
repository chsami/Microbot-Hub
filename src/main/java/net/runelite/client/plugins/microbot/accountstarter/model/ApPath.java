package net.runelite.client.plugins.microbot.accountstarter.model;

public enum ApPath {
    COMBAT(10228, 10229, 10230),
    GATHERER(10231, 10232, 10233);

    private final int doneVarbit;
    private final int f2pVarbit;
    private final int p2pVarbit;

    ApPath(int doneVarbit, int f2pVarbit, int p2pVarbit) {
        this.doneVarbit = doneVarbit;
        this.f2pVarbit = f2pVarbit;
        this.p2pVarbit = p2pVarbit;
    }

    public int doneVarbit() { return doneVarbit; }
    public int f2pVarbit() { return f2pVarbit; }
    public int p2pVarbit() { return p2pVarbit; }
}
