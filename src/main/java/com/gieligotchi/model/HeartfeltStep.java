package com.gieligotchi.model;

import java.util.Arrays;

/** One persisted, independently verifiable part of a Heartfelt Wish. */
public class HeartfeltStep
{
	public enum Kind { ITEM_GAIN, SKILL_XP, NPC_KILL, ACTIVITY }

	private Kind kind;
	private String label;
	private long target;
	private long progress;
	private String skill;
	private int[] itemIds;
	private String[] names;
	private int minX = -1;
	private int maxX = -1;
	private int minY = -1;
	private int maxY = -1;
	private int plane = -1;

	public HeartfeltStep() {}

	private HeartfeltStep(Kind kind, String label, long target)
	{
		this.kind = kind;
		this.label = label;
		this.target = Math.max(1, target);
	}

	public static HeartfeltStep items(String label, long target, int... itemIds)
	{
		HeartfeltStep step = new HeartfeltStep(Kind.ITEM_GAIN, label, target);
		step.itemIds = itemIds == null ? new int[0] : itemIds.clone();
		return step;
	}

	public static HeartfeltStep skill(String label, String skill, long target)
	{
		HeartfeltStep step = new HeartfeltStep(Kind.SKILL_XP, label, target);
		step.skill = skill;
		return step;
	}

	public static HeartfeltStep kills(String label, long target, String... npcNames)
	{
		HeartfeltStep step = new HeartfeltStep(Kind.NPC_KILL, label, target);
		step.names = npcNames == null ? new String[0] : npcNames.clone();
		return step;
	}

	public static HeartfeltStep activity(String label, long target, String... activityIds)
	{
		HeartfeltStep step = new HeartfeltStep(Kind.ACTIVITY, label, target);
		step.names = activityIds == null ? new String[0] : activityIds.clone();
		return step;
	}

	public HeartfeltStep inArea(int minX, int maxX, int minY, int maxY, int plane)
	{
		this.minX = Math.min(minX, maxX);
		this.maxX = Math.max(minX, maxX);
		this.minY = Math.min(minY, maxY);
		this.maxY = Math.max(minY, maxY);
		this.plane = plane;
		return this;
	}

	public HeartfeltStep copy()
	{
		HeartfeltStep copy = new HeartfeltStep(kind, label, target);
		copy.progress = progress;
		copy.skill = skill;
		copy.itemIds = itemIds == null ? null : itemIds.clone();
		copy.names = names == null ? null : names.clone();
		copy.minX = minX; copy.maxX = maxX; copy.minY = minY; copy.maxY = maxY; copy.plane = plane;
		return copy;
	}

	public boolean recordItem(int itemId, long quantity, int x, int y, int currentPlane)
	{
		if (kind != Kind.ITEM_GAIN || quantity <= 0 || !inside(x, y, currentPlane) || !contains(itemIds, itemId)) { return false; }
		return add(quantity);
	}

	public boolean recordSkill(String observedSkill, long xp, int x, int y, int currentPlane)
	{
		if (kind != Kind.SKILL_XP || xp <= 0 || skill == null || observedSkill == null
			|| !skill.equalsIgnoreCase(observedSkill) || !inside(x, y, currentPlane)) { return false; }
		return add(xp);
	}

	public boolean recordNpc(String npcName)
	{
		if (kind != Kind.NPC_KILL || npcName == null || !containsIgnoreCase(names, npcName)) { return false; }
		return add(1);
	}

	public boolean recordActivity(String activityId)
	{
		if (kind != Kind.ACTIVITY || activityId == null || !containsIgnoreCase(names, activityId)) { return false; }
		return add(1);
	}

	public void complete() { progress = Math.max(1, target); }
	public boolean isComplete() { return progress >= Math.max(1, target); }
	public Kind getKind() { return kind; }
	public String getLabel() { return label; }
	public long getTarget() { return target; }
	public long getProgress() { return progress; }

	private boolean add(long amount)
	{
		long before = progress;
		progress = Math.min(Math.max(1, target), progress + Math.max(0, amount));
		return progress != before;
	}

	private boolean inside(int x, int y, int currentPlane)
	{
		if (minX < 0) { return true; }
		return x >= minX && x <= maxX && y >= minY && y <= maxY && (plane < 0 || plane == currentPlane);
	}

	private static boolean contains(int[] values, int value)
	{
		if (values == null) { return false; }
		for (int candidate : values) { if (candidate == value) { return true; } }
		return false;
	}

	private static boolean containsIgnoreCase(String[] values, String value)
	{
		if (values == null) { return false; }
		for (String candidate : values) { if (candidate != null && candidate.equalsIgnoreCase(value)) { return true; } }
		return false;
	}

	@Override public String toString() { return label + " " + progress + "/" + target + " " + Arrays.toString(itemIds); }
}
