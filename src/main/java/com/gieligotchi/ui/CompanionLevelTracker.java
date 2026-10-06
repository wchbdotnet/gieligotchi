package com.gieligotchi.ui;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.service.LevelCurve;
import java.util.Objects;

/** Converts any level jump from one XP award into exactly one visual celebration. */
final class CompanionLevelTracker
{
	private String companionId;
	private int level = -1;

	CompanionEffectController.Effect observe(CompanionInstance companion)
	{
		if (companion == null)
		{
			companionId = null;
			level = -1;
			return null;
		}
		String currentId = companion.getInstanceId();
		if (currentId == null) { currentId = companion.getSpeciesId() + ":" + companion.getHatchedAt(); }
		int currentLevel = LevelCurve.levelFor(companion);
		if (!Objects.equals(companionId, currentId))
		{
			companionId = currentId;
			level = currentLevel;
			return null;
		}
		int previousLevel = level;
		level = currentLevel;
		if (currentLevel <= previousLevel) { return null; }
		return currentLevel >= 99 && previousLevel < 99
			? CompanionEffectController.Effect.MAX_LEVEL
			: CompanionEffectController.Effect.LEVEL_UP;
	}
}
