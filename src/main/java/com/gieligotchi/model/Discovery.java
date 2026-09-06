package com.gieligotchi.model;

import java.util.EnumSet;

public class Discovery
{
	private long firstHatchedAt;
	private int totalHatched;
	private EnumSet<Palette> palettes = EnumSet.noneOf(Palette.class);

	public void record(Palette palette)
	{
		if (firstHatchedAt == 0) { firstHatchedAt = System.currentTimeMillis(); }
		totalHatched++;
		palettes.add(palette);
	}

	public long getFirstHatchedAt() { return firstHatchedAt; }
	public int getTotalHatched() { return totalHatched; }
	public EnumSet<Palette> getPalettes() { return palettes == null ? EnumSet.noneOf(Palette.class) : palettes; }
}
