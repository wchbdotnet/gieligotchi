package com.gieligotchi.model;

public enum SpeciesRarity
{
	COMMON("Common", 1.00, 100),
	UNCOMMON("Uncommon", 1.15, 175),
	RARE("Rare", 1.40, 325),
	EPIC("Epic", 1.75, 600),
	LEGENDARY("Legendary", 2.25, 1_100);

	private final String displayName;
	private final double xpMultiplier;
	private final long saleBase;

	SpeciesRarity(String displayName, double xpMultiplier, long saleBase)
	{
		this.displayName = displayName;
		this.xpMultiplier = xpMultiplier;
		this.saleBase = saleBase;
	}

	public String getDisplayName() { return displayName; }
	public double getXpMultiplier() { return xpMultiplier; }
	public long getSaleBase() { return saleBase; }
}
