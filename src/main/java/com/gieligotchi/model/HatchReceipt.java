package com.gieligotchi.model;

public class HatchReceipt
{
	private String eggInstanceId;
	private EggTier eggTier;
	private String speciesId;
	private SpeciesRarity speciesRarity;
	private double speciesTierChance;
	private int speciesPoolSize;
	private Palette palette;
	private double paletteChance;
	private double combinedChance;
	private long rolledAt;
	private Long revealedAt;

	public HatchReceipt(String eggInstanceId, EggTier eggTier, String speciesId,
		SpeciesRarity speciesRarity, double speciesTierChance, int speciesPoolSize,
		Palette palette, double paletteChance, double combinedChance)
	{
		this.eggInstanceId = eggInstanceId;
		this.eggTier = eggTier;
		this.speciesId = speciesId;
		this.speciesRarity = speciesRarity;
		this.speciesTierChance = speciesTierChance;
		this.speciesPoolSize = speciesPoolSize;
		this.palette = palette;
		this.paletteChance = paletteChance;
		this.combinedChance = combinedChance;
		this.rolledAt = System.currentTimeMillis();
	}

	public String getEggInstanceId() { return eggInstanceId; }
	public EggTier getEggTier() { return eggTier; }
	public String getSpeciesId() { return speciesId; }
	public SpeciesRarity getSpeciesRarity() { return speciesRarity; }
	public double getSpeciesTierChance() { return speciesTierChance; }
	public int getSpeciesPoolSize() { return speciesPoolSize; }
	public Palette getPalette() { return palette; }
	public double getPaletteChance() { return paletteChance; }
	public double getCombinedChance() { return combinedChance; }
	public long getRolledAt() { return rolledAt; }
	public Long getRevealedAt() { return revealedAt; }
	public void markRevealed() { revealedAt = System.currentTimeMillis(); }
}
