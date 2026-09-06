package com.gieligotchi.model;

public class CompanionWish
{
	public enum Type { COMBAT, SKILLING, ADVENTURE, EXPLORATION, PLAY }

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

	public Type getType() { return type; }
	public String getLabel() { return label; }
	public long getTarget() { return target; }
	public long getProgress() { return progress; }
	public boolean isComplete() { return progress >= target; }
	public void addProgress(long amount) { progress = Math.min(target, progress + Math.max(0, amount)); }
}
