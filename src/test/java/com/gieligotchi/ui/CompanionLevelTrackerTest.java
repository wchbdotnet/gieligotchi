package com.gieligotchi.ui;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.service.LevelCurve;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionLevelTrackerTest
{
	@Test public void multipleLevelsFromOneAwardProduceOneEffect()
	{
		CompanionInstance companion = companion();
		CompanionLevelTracker tracker = new CompanionLevelTracker();
		assertNull(tracker.observe(companion));
		companion.addXp(LevelCurve.xpForLevel(companion, 20));
		assertEquals(CompanionEffectController.Effect.LEVEL_UP, tracker.observe(companion));
		assertNull(tracker.observe(companion));
	}

	@Test public void reachingLevel99UsesTheMaxLevelCelebration()
	{
		CompanionInstance companion = companion();
		CompanionLevelTracker tracker = new CompanionLevelTracker();
		assertNull(tracker.observe(companion));
		companion.addXp(LevelCurve.xpForLevel(companion, 99));
		assertEquals(CompanionEffectController.Effect.MAX_LEVEL, tracker.observe(companion));
		assertNull(tracker.observe(companion));
	}

	@Test public void switchingCompanionsDoesNotCreateAFalseLevelUp()
	{
		CompanionLevelTracker tracker = new CompanionLevelTracker();
		CompanionInstance first = companion();
		assertNull(tracker.observe(first));
		CompanionInstance second = companion();
		second.addXp(LevelCurve.xpForLevel(second, 40));
		assertNull(tracker.observe(second));
	}

	private static CompanionInstance companion()
	{
		return CompanionInstance.from(new HatchReceipt("egg", EggTier.COMMON, "soup",
			SpeciesRarity.COMMON, 65, 10, Palette.BASE, 56.4, 0.1));
	}
}
