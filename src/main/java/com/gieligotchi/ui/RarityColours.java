package com.gieligotchi.ui;

import com.gieligotchi.model.Palette;
import com.gieligotchi.model.SpeciesRarity;
import java.awt.Color;

final class RarityColours
{
	private RarityColours() {}

	static Color species(SpeciesRarity rarity)
	{
		switch (rarity)
		{
			case UNCOMMON: return new Color(0x62B85A);
			case RARE: return new Color(0x4AA7E8);
			case EPIC: return new Color(0xB06BE3);
			case LEGENDARY: return new Color(0xF2B53D);
			case COMMON:
			default: return new Color(0xD3D3D3);
		}
	}

	static Color palette(Palette palette)
	{
		switch (palette)
		{
			case COPPER: return new Color(0xD17A42);
			case MOSS: return new Color(0x79A94A);
			case AZURE: return new Color(0x4ABBE8);
			case VIOLET: return new Color(0x9A68DB);
			case ROSE: return new Color(0xE76DA9);
			case CRIMSON: return new Color(0xE34D4D);
			case FROST: return new Color(0xBCEEFF);
			case GILDED: return new Color(0xFFD057);
			case VOID: return new Color(0x7557C8);
			case OBSIDIAN: return new Color(0x747B86);
			case BASE:
			default: return new Color(0xF0E2B6);
		}
	}
}
