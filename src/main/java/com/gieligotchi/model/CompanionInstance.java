package com.gieligotchi.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CompanionInstance
{
	private String instanceId;
	private String speciesId;
	private SpeciesRarity speciesRarity;
	private Palette palette;
	private long lifetimeXp;
	private long hatchedAt;
	private EggTier eggTierOrigin;
	private String customName;
	private int affectionHearts;
	private CompanionPersonality personality;
	private Map<String, Integer> personalityPoints = new LinkedHashMap<>();
	private Map<String, Long> skillXpTogether = new LinkedHashMap<>();
	private Map<String, Integer> toyInteractions = new LinkedHashMap<>();
	private List<Integer> regionsVisited = new ArrayList<>();
	private List<MemoryEntry> memories = new ArrayList<>();
	private CompanionWish wish;
	private String favoriteToyId;
	private int highestNpcLevel;
	private String highestNpcName;
	private boolean legacy;
	private long legacyAt;
	private long lastToyPlayedAt;

	public static CompanionInstance from(HatchReceipt receipt)
	{
		CompanionInstance companion = new CompanionInstance();
		companion.instanceId = UUID.randomUUID().toString();
		companion.speciesId = receipt.getSpeciesId();
		companion.speciesRarity = receipt.getSpeciesRarity();
		companion.palette = receipt.getPalette();
		companion.hatchedAt = System.currentTimeMillis();
		companion.eggTierOrigin = receipt.getEggTier();
		companion.memories.add(new MemoryEntry("Hatched", "Began life as a "
			+ receipt.getEggTier().getDisplayName() + " egg"));
		companion.wish = companion.createWish(null);
		return companion;
	}

	public String getInstanceId() { return instanceId; }
	public String getSpeciesId() { return speciesId; }
	public SpeciesRarity getSpeciesRarity() { return speciesRarity; }
	public Palette getPalette() { return palette; }
	public long getLifetimeXp() { return lifetimeXp; }
	public long getHatchedAt() { return hatchedAt; }
	public EggTier getEggTierOrigin() { return eggTierOrigin; }
	public void addXp(long amount) { lifetimeXp = Math.max(0, lifetimeXp + amount); }
	public String getCustomName() { return customName; }
	public String getDisplayName(String speciesName) { return customName == null || customName.trim().isEmpty() ? speciesName : customName; }
	public int getAffectionHearts() { return affectionHearts; }
	public RelationshipStage getRelationshipStage() { return RelationshipStage.forHearts(affectionHearts); }
	public CompanionPersonality getPersonality() { return personality; }
	public CompanionWish getWish() { return wish; }
	public List<MemoryEntry> getMemories() { return memories; }
	public String getFavoriteToyId() { return favoriteToyId; }
	public int getHighestNpcLevel() { return highestNpcLevel; }
	public String getHighestNpcName() { return highestNpcName; }
	public boolean isLegacy() { return legacy; }
	public long getLegacyAt() { return legacyAt; }
	public long getLastToyPlayedAt() { return lastToyPlayedAt; }

	public void repair()
	{
		if (personalityPoints == null) { personalityPoints = new LinkedHashMap<>(); }
		if (skillXpTogether == null) { skillXpTogether = new LinkedHashMap<>(); }
		if (toyInteractions == null) { toyInteractions = new LinkedHashMap<>(); }
		if (regionsVisited == null) { regionsVisited = new ArrayList<>(); }
		if (memories == null) { memories = new ArrayList<>(); }
		if (wish == null) { wish = createWish(null); }
	}

	public void rename(String name)
	{
		if (affectionHearts < 5 || name == null) { return; }
		String clean = name.trim().replaceAll("[^A-Za-z0-9 '\\-]", "");
		if (clean.isEmpty()) { return; }
		customName = clean.substring(0, Math.min(18, clean.length()));
		addMemoryOnce("Named", "Became known as " + customName);
	}

	public void rerollWish() { wish = createWish(wish == null ? null : wish.getType()); }

	public boolean claimWish()
	{
		if (wish == null || !wish.isComplete()) { return false; }
		CompanionWish.Type completedType = wish.getType();
		affectionHearts = Math.min(100, affectionHearts + 1);
		addPersonalityPoint(personalityFor(completedType), 1);
		addMemoryOnce("Wish fulfilled", wish.getLabel());
		checkRelationshipMilestones();
		wish = createWish(completedType);
		return true;
	}

	public void recordSkill(String skillName, long rawXp)
	{
		if (rawXp <= 0) { return; }
		skillXpTogether.merge(skillName, rawXp, Long::sum);
		addPersonalityPoint(CompanionPersonality.INDUSTRIOUS, rawXp >= 5_000 ? 1 : 0);
		progressWish(CompanionWish.Type.SKILLING, rawXp);
	}

	public void recordNpcKill(String npcName, int combatLevel)
	{
		int level = Math.max(1, combatLevel);
		addPersonalityPoint(CompanionPersonality.FIERCE, level >= 50 ? 2 : 1);
		progressWish(CompanionWish.Type.COMBAT, level);
		if (level > highestNpcLevel)
		{
			highestNpcLevel = level;
			highestNpcName = npcName == null ? "Unknown foe" : npcName;
			addMemoryOnce("Strongest foe", highestNpcName + " (level " + level + ")");
		}
	}

	public void recordQuestOrClue(String label)
	{
		addPersonalityPoint(CompanionPersonality.ADVENTUROUS, 2);
		progressWish(CompanionWish.Type.ADVENTURE, 1);
		if (label != null && !label.isEmpty()) { addMemoryOnce("Adventure", label); }
	}

	public void recordMajorChallenge(String label)
	{
		addPersonalityPoint(CompanionPersonality.ADVENTUROUS, 2);
		if (label != null && !label.isEmpty()) { addMemoryOnce("Adventure", label); }
	}

	public void recordRegionVisit(int regionId)
	{
		if (regionId <= 0 || regionsVisited.contains(regionId)) { return; }
		regionsVisited.add(regionId);
		addPersonalityPoint(CompanionPersonality.ADVENTUROUS, 1);
		progressWish(CompanionWish.Type.EXPLORATION, 1);
		if (regionsVisited.size() % 10 == 0)
		{
			addMemoryOnce("Explorer", "Visited " + regionsVisited.size() + " regions together");
		}
	}

	public void recordGame(boolean won)
	{
		addPersonalityPoint(CompanionPersonality.PLAYFUL, won ? 2 : 1);
		progressWish(CompanionWish.Type.PLAY, won ? 1 : 0);
	}

	public void playWithToy(Toy toy)
	{
		if (toy == null) { return; }
		lastToyPlayedAt = System.currentTimeMillis();
		addPersonalityPoint(toy.getAffinity(), 1);
		int uses = toyInteractions.merge(toy.getAssetId(), 1, Integer::sum);
		if (affectionHearts >= 30 && favoriteToyId == null && uses >= 3)
		{
			favoriteToyId = toy.getAssetId();
			addMemoryOnce("Favourite toy", "Discovered a love for the " + toy.getDisplayName());
		}
	}

	public void recordLevel99()
	{
		addMemoryOnce("Level 99", "Reached the highest companion level");
		if (!legacy)
		{
			legacy = true;
			legacyAt = System.currentTimeMillis();
			addMemoryOnce("Legacy companion", "A lifelong journey was commemorated");
		}
	}

	public String getFavoriteSkill()
	{
		String result = null;
		long best = -1;
		for (Map.Entry<String, Long> entry : skillXpTogether.entrySet())
		{
			if (entry.getValue() > best) { result = entry.getKey(); best = entry.getValue(); }
		}
		return result;
	}

	private void progressWish(CompanionWish.Type type, long amount)
	{
		if (wish != null && wish.getType() == type && !wish.isComplete()) { wish.addProgress(amount); }
	}

	private void addPersonalityPoint(CompanionPersonality candidate, int amount)
	{
		if (amount > 0) { personalityPoints.merge(candidate.name(), amount, Integer::sum); }
		if (personality == null && affectionHearts >= 15)
		{
			CompanionPersonality best = CompanionPersonality.LOYAL;
			int bestScore = -1;
			boolean tie = false;
			for (CompanionPersonality value : CompanionPersonality.values())
			{
				int score = personalityPoints.getOrDefault(value.name(), 0);
				if (score > bestScore) { best = value; bestScore = score; tie = false; }
				else if (score == bestScore) { tie = true; }
			}
			personality = tie ? CompanionPersonality.LOYAL : best;
			addMemoryOnce("Personality revealed", personality.getDisplayName());
		}
	}

	private CompanionWish createWish(CompanionWish.Type avoid)
	{
		long seed = System.nanoTime() ^ (instanceId == null ? 0 : instanceId.hashCode()) ^ affectionHearts;
		Random random = new Random(seed);
		CompanionWish.Type[] types = CompanionWish.Type.values();
		CompanionWish.Type type;
		if (personality != null && random.nextInt(100) < 55) { type = typeFor(personality); }
		else { type = types[random.nextInt(types.length)]; }
		if (type == avoid) { type = types[(type.ordinal() + 1 + random.nextInt(types.length - 1)) % types.length]; }
		switch (type)
		{
			case COMBAT: return new CompanionWish(type, "Defeat foes worth " + (80 + affectionHearts * 3) + " combat levels", 80 + affectionHearts * 3L);
			case SKILLING: return new CompanionWish(type, "Earn " + (4_000 + affectionHearts * 100) + " XP", 4_000 + affectionHearts * 100L);
			case ADVENTURE: return new CompanionWish(type, "Complete a quest or clue", 1);
			case EXPLORATION: return new CompanionWish(type, "Visit a new area", 1);
			default: return new CompanionWish(type, "Win a round of Higher or Lower", 1);
		}
	}

	private void checkRelationshipMilestones()
	{
		RelationshipStage stage = getRelationshipStage();
		if (stage != RelationshipStage.NEWLY_HATCHED)
		{
			addMemoryOnce(stage.getDisplayName(), "Reached " + stage.getHearts() + " affection hearts");
		}
		if (affectionHearts >= 15) { addPersonalityPoint(CompanionPersonality.LOYAL, 0); }
	}

	private void addMemoryOnce(String title, String detail)
	{
		for (MemoryEntry memory : memories)
		{
			if (title.equals(memory.getTitle()) && detail.equals(memory.getDetail())) { return; }
		}
		memories.add(new MemoryEntry(title, detail));
	}

	private static CompanionPersonality personalityFor(CompanionWish.Type type)
	{
		switch (type)
		{
			case COMBAT: return CompanionPersonality.FIERCE;
			case SKILLING: return CompanionPersonality.INDUSTRIOUS;
			case ADVENTURE: return CompanionPersonality.ADVENTUROUS;
			case EXPLORATION: return CompanionPersonality.ADVENTUROUS;
			default: return CompanionPersonality.PLAYFUL;
		}
	}

	private static CompanionWish.Type typeFor(CompanionPersonality personality)
	{
		switch (personality)
		{
			case FIERCE: return CompanionWish.Type.COMBAT;
			case INDUSTRIOUS: return CompanionWish.Type.SKILLING;
			case ADVENTUROUS: return CompanionWish.Type.ADVENTURE;
			default: return CompanionWish.Type.PLAY;
		}
	}
}
