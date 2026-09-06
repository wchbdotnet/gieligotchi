package com.gieligotchi.model;

public enum RelationshipStage
{
	NEWLY_HATCHED(0, "Newly hatched"),
	ACQUAINTANCE(5, "Acquaintance"),
	BUDDY(15, "Buddy"),
	FRIEND(30, "Friend"),
	BEST_FRIEND(60, "Best friend"),
	PARTNER(100, "Partner");

	private final int hearts;
	private final String displayName;

	RelationshipStage(int hearts, String displayName)
	{
		this.hearts = hearts;
		this.displayName = displayName;
	}

	public int getHearts() { return hearts; }
	public String getDisplayName() { return displayName; }

	public static RelationshipStage forHearts(int hearts)
	{
		RelationshipStage result = NEWLY_HATCHED;
		for (RelationshipStage stage : values()) { if (hearts >= stage.hearts) { result = stage; } }
		return result;
	}
}
