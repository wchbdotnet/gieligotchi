package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

public final class SkillRewardPolicy
{
	/** A background contribution: one Bonding XP per 5 RuneScape XP. */
	private static final int BONDING_UNITS_PER_OSRS_XP = 1;
	private static final int BONDING_UNIT_SCALE = 5;

	private SkillRewardPolicy() {}

	public static long npcKillAward(int combatLevel)
	{
		return Math.max(0, combatLevel) * 20L;
	}

	public static long observe(ProfileState state, Skill skill, int xp)
	{
		if (state == null || skill == Skill.OVERALL || xp < 0) { return 0; }
		Map<String, Integer> baselines = state.getSkillBaselines();
		String key = skill.name();
		Integer previous = baselines.get(key);
		if (previous == null)
		{
			baselines.put(key, xp);
			state.getSkillLevelBaselines().put(key, levelForXp(xp));
			return 0;
		}
		if (xp <= previous) { return 0; }
		baselines.put(key, xp);
		int delta = xp - previous;
		int accumulatedUnits = state.getSkillRemainders().getOrDefault(key, 0)
			+ delta * BONDING_UNITS_PER_OSRS_XP;
		long award = accumulatedUnits / BONDING_UNIT_SCALE;
		state.getSkillRemainders().put(key, accumulatedUnits % BONDING_UNIT_SCALE);

		int previousLevel = state.getSkillLevelBaselines().getOrDefault(key, levelForXp(previous));
		int currentLevel = levelForXp(xp);
		for (int level = previousLevel + 1; level <= currentLevel; level++)
		{
			award += levelUpAward(level);
		}
		state.getSkillLevelBaselines().put(key, currentLevel);
		return award;
	}

	public static int levelUpAward(int level)
	{
		int clamped = Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, level));
		if (clamped <= 2) { return 1_000; }
		if (clamped >= Experience.MAX_REAL_LEVEL) { return 20_000; }
		double progress = (clamped - 2d) / 97d;
		double curve = Math.pow(progress, 2.5d);
		return (int) Math.round(1_000d * Math.pow(20d, curve));
	}

	public static int levelForXp(int xp)
	{
		return Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, Experience.getLevelForXp(Math.max(0, xp))));
	}
}
