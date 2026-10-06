package com.gieligotchi.service;

import com.gieligotchi.model.HeartfeltStep;
import com.gieligotchi.model.HeartfeltWish;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Immutable catalogue definition. Runtime progress lives in HeartfeltWish. */
public final class HeartfeltTask
{
	public enum Scale
	{
		SIMPLE(new long[]{25_000, 75_000, 150_000, 250_000}, new long[]{15, 25, 50, 75}),
		MULTI(new long[]{75_000, 175_000, 300_000, 500_000}, new long[]{40, 115, 200, 300}),
		LONG(new long[]{150_000, 300_000, 550_000, 900_000}, new long[]{90, 175, 325, 500}),
		APEX(new long[]{300_000, 600_000, 1_000_000, 1_500_000}, new long[]{250, 400, 650, 1_000});

		private final long[] xp;
		private final long[] points;
		Scale(long[] xp, long[] points) { this.xp = xp; this.points = points; }
	}

	private final String id;
	private final String title;
	private final String description;
	private final String requirements;
	private final String requiredSkill;
	private final int requiredLevel;
	private final int minimumTotalLevel;
	private final Scale scale;
	private final List<HeartfeltStep> steps;

	private HeartfeltTask(String id, String title, String description, String requirements,
		String requiredSkill, int requiredLevel, int minimumTotalLevel, Scale scale, HeartfeltStep... steps)
	{
		this.id = id; this.title = title; this.description = description; this.requirements = requirements;
		this.requiredSkill = requiredSkill; this.requiredLevel = requiredLevel;
		this.minimumTotalLevel = minimumTotalLevel; this.scale = scale;
		this.steps = new ArrayList<>(Arrays.asList(steps));
	}

	public static HeartfeltTask of(String id, String title, String description, String requirements,
		String requiredSkill, int requiredLevel, int minimumTotalLevel, Scale scale, HeartfeltStep... steps)
	{
		return new HeartfeltTask(id, title, description, requirements, requiredSkill, requiredLevel,
			minimumTotalLevel, scale, steps);
	}

	public HeartfeltWish.Readiness readiness(Map<String, Integer> levels, int totalLevel)
	{
		int level = requiredSkill == null ? 99 : levels.getOrDefault(requiredSkill, 1);
		int skillGap = Math.max(0, requiredLevel - level);
		int totalGap = Math.max(0, minimumTotalLevel - totalLevel);
		if (skillGap == 0 && totalGap == 0) { return HeartfeltWish.Readiness.READY; }
		if (skillGap <= 10 && totalGap <= 150) { return HeartfeltWish.Readiness.WITHIN_REACH; }
		return HeartfeltWish.Readiness.DISTANT;
	}

	public HeartfeltWish create(Map<String, Integer> levels, int totalLevel)
	{
		int band = totalLevel < 750 ? 0 : totalLevel < 1_500 ? 1 : totalLevel < 2_000 ? 2 : 3;
		HeartfeltWish.Readiness readiness = readiness(levels, totalLevel);
		double premium = readiness == HeartfeltWish.Readiness.WITHIN_REACH ? 1.10
			: readiness == HeartfeltWish.Readiness.DISTANT ? 1.20 : 1.0;
		long pointCap = scale == Scale.APEX ? 1_000 : 500;
		long points = Math.min(pointCap, Math.round(scale.points[band] * premium));
		long xp = Math.round(scale.xp[band] * premium / 1_000d) * 1_000L;
		return new HeartfeltWish(id, title, description, requirements, readiness, xp, points,
			scale == Scale.APEX, steps);
	}

	public String getId() { return id; }
	public Scale getScale() { return scale; }
}
