package net.runelite.client.plugins.microbot.irkedmlm.enums;

public enum DepositMethod {
	ITEMS("Deposit Items"),
	ALL("Deposit All");

	private final String label;
	DepositMethod(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
