package com.gieligotchi.model;

import net.runelite.api.Skill;

/** A player-chosen, non-combat XP goal. Progress is measured in raw OSRS XP. */
public class SkillingGoal
{
	public static final int[] EGG_TARGETS = {5_000, 10_000, 25_000, 50_000, 100_000};
	public static final long[] EGG_REWARDS = {1_500, 3_500, 10_000, 22_500, 50_000};
	public static final int MEGA_TARGET = 100_000;
	public static final long MEGA_REWARD = 35_000;
	public static final long MEGA_PRICE = 5;
	public static final Skill[] SKILLS = {
		Skill.AGILITY, Skill.CONSTRUCTION, Skill.COOKING, Skill.CRAFTING,
		Skill.FARMING, Skill.FIREMAKING, Skill.FISHING, Skill.FLETCHING,
		Skill.HERBLORE, Skill.HUNTER, Skill.MINING, Skill.RUNECRAFT,
		Skill.SMITHING, Skill.THIEVING, Skill.WOODCUTTING
	};

	private String skill;
	private String activity;
	private long target;
	private long progress;
	private long reward;
	private long lastActivityCount = -1;

	public static boolean eligible(Skill skill)
	{
		if (skill == null) { return false; }
		for (Skill candidate : SKILLS) { if (candidate == skill) { return true; } }
		return false;
	}

	public static SkillingGoal egg(Skill skill, int target)
	{
		if (!eligible(skill)) { return null; }
		for (int i = 0; i < EGG_TARGETS.length; i++)
		{
			if (EGG_TARGETS[i] == target) { return create(skill, target, EGG_REWARDS[i]); }
		}
		return null;
	}

	public static SkillingGoal mega(Skill skill)
	{
		return eligible(skill) ? create(skill, MEGA_TARGET, MEGA_REWARD) : null;
	}

	public static SkillingGoal mega(SkillingActivity activity)
	{
		if (activity == null) { return null; }
		SkillingGoal goal = new SkillingGoal();
		goal.activity = activity.name();
		goal.target = activity.getTarget();
		goal.reward = MEGA_REWARD;
		return goal;
	}

	private static SkillingGoal create(Skill skill, long target, long reward)
	{
		SkillingGoal goal = new SkillingGoal();
		goal.skill = skill.name();
		goal.target = target;
		goal.reward = reward;
		return goal;
	}

	public String getSkill() { return skill; }
	public SkillingActivity getActivity()
	{
		if (activity == null) { return null; }
		try { return SkillingActivity.valueOf(activity); }
		catch (IllegalArgumentException ignored) { return null; }
	}
	public String getLabel() { return getActivity() == null ? skill + " XP" : getActivity().getLabel() + " completions"; }
	public long getTarget() { return target; }
	public long getProgress() { return progress; }
	public long getReward() { return reward; }
	public boolean isComplete() { return target > 0 && progress >= target; }
	public void record(Skill observed, long rawXp)
	{
		recordAndReturnRemainder(observed, rawXp);
	}

	/** Returns unspent XP so a large offline delta can advance the next existing goal. */
	public long recordAndReturnRemainder(Skill observed, long rawXp)
	{
		if (rawXp <= 0) { return 0; }
		if (!eligible(observed) || !observed.name().equals(skill) || isComplete()) { return rawXp; }
		long applied = Math.min(rawXp, target - progress);
		progress += applied;
		return rawXp - applied;
	}

	public void record(SkillingActivity.Completion completion)
	{
		if (completion == null || completion.getActivity() != getActivity() || isComplete()) { return; }
		long count = completion.getCount();
		if (count <= lastActivityCount) { return; }
		lastActivityCount = count;
		progress = Math.min(target, progress + 1);
	}
}
