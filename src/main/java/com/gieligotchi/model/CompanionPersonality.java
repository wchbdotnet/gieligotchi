package com.gieligotchi.model;

public enum CompanionPersonality
{
	FIERCE("Fierce", "Thrives on combat and worthy foes"),
	INDUSTRIOUS("Industrious", "Finds joy in steady skilling"),
	ADVENTUROUS("Adventurous", "Lives for quests and discoveries"),
	PLAYFUL("Playful", "Loves toys and handheld games"),
	LOYAL("Loyal", "Values a balanced life together");

	private final String displayName;
	private final String description;

	CompanionPersonality(String displayName, String description)
	{
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName() { return displayName; }
	public String getDescription() { return description; }
}
