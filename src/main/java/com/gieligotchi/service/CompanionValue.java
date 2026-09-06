package com.gieligotchi.service;

import com.gieligotchi.model.CompanionInstance;

public final class CompanionValue
{
	public static final long MINIMUM_VALUE = 100L;
	public static final long XP_PER_POINT_ON_SALE = 5_000L;

	private CompanionValue() {}

	public static long saleValue(CompanionInstance companion)
	{
		if (companion == null) { return 0L; }
		long rarityAndColour = Math.round(companion.getSpeciesRarity().getSaleBase()
			* companion.getPalette().getSaleMultiplier());
		long experience = companion.getLifetimeXp() / XP_PER_POINT_ON_SALE;
		return Math.max(MINIMUM_VALUE, rarityAndColour + experience);
	}
}
