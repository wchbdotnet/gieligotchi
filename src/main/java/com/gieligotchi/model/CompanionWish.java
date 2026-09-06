package com.gieligotchi.model;

public class CompanionWish
{
	public enum Type { COMBAT, SKILLING, SLAYER, ADVENTURE, CHALLENGE, EXPLORATION, PLAY }
	private static final int MAX_LEVEL = 99;

	private Type type;
	private String label;
	private long target;
	private long progress;

	public CompanionWish(Type type, String label, long target)
	{
		this.type = type;
		this.label = label;
		this.target = Math.max(1, target);
	}

	public static CompanionWish forLevel(Type type, int companionLevel)
	{
		int level = clampLevel(companionLevel);
		switch (type)
		{
			case COMBAT:
				long combatTarget = 80L + (level - 1L) * 4L;
				return new CompanionWish(type, "Defeat foes worth " + combatTarget + " combat levels", combatTarget);
			case SKILLING:
				long xpTarget = 4_000L + (level - 1L) * 250L;
				return new CompanionWish(type, "Earn " + xpTarget + " XP", xpTarget);
			case SLAYER:
				long taskTarget = 1L + (level - 1L) / 50L;
				return new CompanionWish(type, taskTarget == 1 ? "Complete a Slayer task"
					: "Complete " + taskTarget + " Slayer tasks", taskTarget);
			case ADVENTURE:
				return new CompanionWish(type, "Complete a quest or clue", 1);
			case CHALLENGE:
				return new CompanionWish(type, "Complete a raid, Gauntlet or Barbarian Assault Wave 10", 1);
			case EXPLORATION:
				long areaTarget = 1L + (level - 1L) / 40L;
				return new CompanionWish(type, areaTarget == 1 ? "Visit a new area"
					: "Visit " + areaTarget + " new areas", areaTarget);
			case PLAY:
			default:
				long winsTarget = 1L + (level - 1L) / 33L;
				return new CompanionWish(type, winsTarget == 1 ? "Win a round of Higher or Lower"
					: "Win " + winsTarget + " rounds of Higher or Lower", winsTarget);
		}
	}

	public static long rewardXp(Type type, int companionLevel)
	{
		long base;
		switch (type)
		{
			case CHALLENGE: base = 15_000L; break;
			case ADVENTURE: base = 4_000L; break;
			case SLAYER: base = 3_000L; break;
			case COMBAT:
			case SKILLING: base = 1_500L; break;
			case EXPLORATION: base = 1_000L; break;
			case PLAY:
			default: base = 500L; break;
		}
		double multiplier = 1d + 4d * (clampLevel(companionLevel) - 1d) / (MAX_LEVEL - 1d);
		return Math.round(base * multiplier / 100d) * 100L;
	}

	public Type getType() { return type; }
	public String getLabel() { return label; }
	public long getTarget() { return target; }
	public long getProgress() { return progress; }
	public boolean isComplete() { return progress >= target; }
	public void addProgress(long amount) { progress = Math.min(target, progress + Math.max(0, amount)); }

	private static int clampLevel(int level)
	{
		return Math.max(1, Math.min(MAX_LEVEL, level));
	}
}
