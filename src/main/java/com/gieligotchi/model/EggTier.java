package com.gieligotchi.model;

public enum EggTier
{
	COMMON("Common", 25_000L, 100L, new double[]{65, 25, 8, 1.8, 0.2}),
	RARE("Rare", 100_000L, 250L, new double[]{35, 35, 20, 8, 2}),
	MEGA_RARE("Mega-rare", 300_000L, 500L, new double[]{10, 25, 35, 22, 8});

	private final String displayName;
	private final long hatchXp;
	private final long price;
	private final double[] speciesOdds;

	EggTier(String displayName, long hatchXp, long price, double[] speciesOdds)
	{
		this.displayName = displayName;
		this.hatchXp = hatchXp;
		this.price = price;
		this.speciesOdds = speciesOdds;
	}

	public String getDisplayName() { return displayName; }
	public long getHatchXp() { return hatchXp; }
	public long getPrice() { return price; }
	public double[] getSpeciesOdds() { return speciesOdds.clone(); }
}
