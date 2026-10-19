package com.gieligotchi.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authoritative personal-purple tables for Apex Heartfelt Wishes. */
public final class ApexRewardPolicy
{
	public static final String CHAMBERS = "cox_purple";
	public static final String THEATRE = "tob_purple";
	public static final String TOMBS = "toa_purple";

	private static final Map<String, int[]> REWARDS = rewards();

	private ApexRewardPolicy() {}

	public static boolean matches(String wishTaskId, String rewardSource, int itemId)
	{
		if (wishTaskId == null || !wishTaskId.equals(rewardSource)) { return false; }
		for (int candidate : itemIds(wishTaskId))
		{
			if (candidate == itemId) { return true; }
		}
		return false;
	}

	public static int[] itemIds(String taskId)
	{
		int[] ids = REWARDS.get(taskId);
		return ids == null ? new int[0] : ids.clone();
	}

	private static Map<String, int[]> rewards()
	{
		Map<String, int[]> rewards = new LinkedHashMap<>();
		// Chambers: prayer scrolls, weapons, shield and the base Ancestral set.
		rewards.put(CHAMBERS, new int[]{
			13652, 20997, 21000, 21003, 21012, 21015, 21018, 21021, 21024, 21034, 21043, 21079
		});
		// Theatre: rapier, Justiciar, Avernic hilt, Sanguinesti staff and Scythe.
		rewards.put(THEATRE, new int[]{22324, 22326, 22327, 22328, 22477, 22481, 22486});
		// Tombs: only items from the purple unique table. Thread, jewels and rune caches are excluded.
		rewards.put(TOMBS, new int[]{25975, 25985, 26219, 27226, 27229, 27232, 27277});
		return Collections.unmodifiableMap(rewards);
	}
}
