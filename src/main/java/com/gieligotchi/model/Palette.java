package com.gieligotchi.model;

public enum Palette
{
	BASE("Base", 56.4, 1.00, 1.00), COPPER("Copper", 13.0, 1.05, 1.05),
	MOSS("Moss", 9.0, 1.10, 1.10), AZURE("Azure", 7.0, 1.15, 1.20),
	VIOLET("Violet", 5.0, 1.20, 1.30), ROSE("Rose", 4.0, 1.30, 1.45),
	CRIMSON("Crimson", 2.5, 1.40, 1.70), FROST("Frost", 1.8, 1.55, 2.10),
	GILDED("Gilded", 1.1, 1.75, 3.00), VOID("Void", 0.15, 2.30, 6.00),
	OBSIDIAN("Obsidian", 0.05, 2.75, 10.00);

	private final String displayName;
	private final double chancePercent;
	private final double xpMultiplier;
	private final double saleMultiplier;

	Palette(String displayName, double chancePercent, double xpMultiplier, double saleMultiplier)
	{
		this.displayName = displayName;
		this.chancePercent = chancePercent;
		this.xpMultiplier = xpMultiplier;
		this.saleMultiplier = saleMultiplier;
	}

	public String getDisplayName() { return displayName; }
	public double getChancePercent() { return chancePercent; }
	public double getXpMultiplier() { return xpMultiplier; }
	public double getSaleMultiplier() { return saleMultiplier; }
	public String getAssetId() { return name().toLowerCase(); }
}
