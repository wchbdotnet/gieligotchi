package com.gieligotchi.model;

public enum Toy
{
	PLAY_BALL("play_ball", "Gnomeball", 25, CompanionPersonality.PLAYFUL),
	RUNE_BLOCKS("rune_blocks", "Pile of runes", 40, CompanionPersonality.INDUSTRIOUS),
	FEATHER_WAND("feather_wand", "Hand fan", 60, CompanionPersonality.ADVENTUROUS),
	DRAGON_PLUSH("dragon_plush", "Jad plush", 100, CompanionPersonality.LOYAL);

	private final String assetId;
	private final String displayName;
	private final long price;
	private final CompanionPersonality affinity;

	Toy(String assetId, String displayName, long price, CompanionPersonality affinity)
	{
		this.assetId = assetId;
		this.displayName = displayName;
		this.price = price;
		this.affinity = affinity;
	}

	public String getAssetId() { return assetId; }
	public String getDisplayName() { return displayName; }
	public long getPrice() { return price; }
	public CompanionPersonality getAffinity() { return affinity; }

	public static Toy fromId(String id)
	{
		if (id != null) { for (Toy toy : values()) { if (toy.assetId.equals(id)) { return toy; } } }
		return null;
	}
}
