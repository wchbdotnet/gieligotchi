package com.gieligotchi.model;

import java.util.ArrayList;
import java.util.List;

/** A frozen, save-safe bespoke adventure offered in place of an ordinary wish. */
public class HeartfeltWish
{
	public enum Readiness { READY, WITHIN_REACH, DISTANT }

	private String taskId;
	private String title;
	private String description;
	private String requirements;
	private Readiness readiness;
	private long rewardXp;
	private long rewardPoints;
	private boolean apex;
	private boolean accepted;
	private long offeredAt;
	private List<HeartfeltStep> steps = new ArrayList<>();

	public HeartfeltWish() {}

	public HeartfeltWish(String taskId, String title, String description, String requirements,
		Readiness readiness, long rewardXp, long rewardPoints, boolean apex, List<HeartfeltStep> steps)
	{
		this.taskId = taskId;
		this.title = title;
		this.description = description;
		this.requirements = requirements;
		this.readiness = readiness == null ? Readiness.READY : readiness;
		this.rewardXp = Math.max(0, rewardXp);
		this.rewardPoints = Math.max(0, rewardPoints);
		this.apex = apex;
		this.offeredAt = System.currentTimeMillis();
		if (steps != null) { for (HeartfeltStep step : steps) { this.steps.add(step.copy()); } }
	}

	public void repair()
	{
		if (readiness == null) { readiness = Readiness.READY; }
		if (steps == null) { steps = new ArrayList<>(); }
	}

	public void accept() { accepted = true; }

	public boolean recordItem(int itemId, long quantity, int x, int y, int plane)
	{
		if (!accepted || isComplete()) { return false; }
		for (HeartfeltStep step : steps) { if (!step.isComplete() && step.recordItem(itemId, quantity, x, y, plane)) { return true; } }
		return false;
	}

	public boolean recordSkill(String skill, long xp, int x, int y, int plane)
	{
		if (!accepted || isComplete()) { return false; }
		for (HeartfeltStep step : steps) { if (!step.isComplete() && step.recordSkill(skill, xp, x, y, plane)) { return true; } }
		return false;
	}

	public boolean recordNpc(String npcName)
	{
		if (!accepted || isComplete()) { return false; }
		for (HeartfeltStep step : steps) { if (!step.isComplete() && step.recordNpc(npcName)) { return true; } }
		return false;
	}

	public boolean recordActivity(String activityId)
	{
		if (!accepted || isComplete()) { return false; }
		for (HeartfeltStep step : steps) { if (!step.isComplete() && step.recordActivity(activityId)) { return true; } }
		return false;
	}

	public boolean isComplete()
	{
		if (steps == null || steps.isEmpty()) { return false; }
		for (HeartfeltStep step : steps) { if (!step.isComplete()) { return false; } }
		return true;
	}

	public long getProgress()
	{
		if (steps == null || steps.isEmpty()) { return 0; }
		long total = 0;
		for (HeartfeltStep step : steps)
		{
			total += Math.min(1000, 1000 * step.getProgress() / Math.max(1, step.getTarget()));
		}
		return total / steps.size();
	}

	public String getTaskId() { return taskId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public String getRequirements() { return requirements; }
	public Readiness getReadiness() { return readiness; }
	public long getRewardXp() { return rewardXp; }
	public long getRewardPoints() { return rewardPoints; }
	public boolean isApex() { return apex; }
	public boolean isAccepted() { return accepted; }
	public long getOfferedAt() { return offeredAt; }
	public List<HeartfeltStep> getSteps() { return steps; }
}
