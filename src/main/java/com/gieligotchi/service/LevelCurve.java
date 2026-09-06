package com.gieligotchi.service;

import com.gieligotchi.model.CompanionInstance;

public final class LevelCurve
{
	private static final int[] OSRS_XP = buildOsrsTable();
	private static final double MAX_COMBINED_MULTIPLIER = 6.1875d;

	private LevelCurve() {}

	public static int levelFor(CompanionInstance companion)
	{
		for (int level = 99; level >= 2; level--)
		{
			if (companion.getLifetimeXp() >= xpForLevel(companion, level)) { return level; }
		}
		return 1;
	}

	public static long xpForLevel(CompanionInstance companion, int level)
	{
		int clamped = Math.max(1, Math.min(99, level));
		double combined = companion.getSpeciesRarity().getXpMultiplier()
			* companion.getPalette().getXpMultiplier();
		return Math.round(40_000_000d * OSRS_XP[clamped] / OSRS_XP[99]
			* combined / MAX_COMBINED_MULTIPLIER);
	}

	private static int[] buildOsrsTable()
	{
		int[] table = new int[100];
		int points = 0;
		for (int level = 1; level < 99; level++)
		{
			points += Math.floor(level + 300d * Math.pow(2d, level / 7d));
			table[level + 1] = points / 4;
		}
		return table;
	}
}
