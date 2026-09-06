package com.gieligotchi.model;

public enum Backdrop
{
	CLASSIC("Classic LCD", 0),
	LUMBRIDGE("Lumbridge", 150), DRAYNOR_MANOR("Draynor Manor", 200),
	VARROCK("Varrock", 250), FALADOR("Falador", 250),
	ARDOUGNE("Ardougne", 300), RELLEKKA("Rellekka", 300),
	POLLNIVNEACH("Pollnivneach", 300),
	ICE_WOLF_MOUNTAIN("Ice Wolf Mountain", 300),
	TREE_GNOME_STRONGHOLD("Tree Gnome Stronghold", 350),
	GRAND_EXCHANGE("Grand Exchange", 400), MORYTANIA("Morytania", 400),
	WILDERNESS("Wilderness", 450), TZHAAR("TzHaar", 450),
	APE_ATOLL("Ape Atoll", 450), PRIFDDINAS("Prifddinas", 500),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis", 500), ALDARIN("Aldarin", 500),
	FORTIS_COLOSSEUM("Fortis Colosseum", 750),
	CHAMBERS_OF_XERIC("Chambers of Xeric", 750),
	TOMBS_OF_AMASCUT("Tombs of Amascut", 900),
	THEATRE_OF_BLOOD("Theatre of Blood", 900);

	private final String displayName;
	private final long price;

	Backdrop(String displayName, long price)
	{
		this.displayName = displayName;
		this.price = price;
	}

	public String getDisplayName() { return displayName; }
	public long getPrice() { return price; }
	public String getAssetId() { return name().toLowerCase(); }

	public static Backdrop fromId(String id)
	{
		if (id != null)
		{
			for (Backdrop backdrop : values()) { if (backdrop.getAssetId().equals(id)) { return backdrop; } }
		}
		return CLASSIC;
	}
}
