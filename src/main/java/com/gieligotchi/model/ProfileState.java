package com.gieligotchi.model;

import com.google.gson.annotations.SerializedName;
import net.runelite.api.Skill;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileState
{
	private int schemaVersion = 1;
	private String profileKey;
	private boolean starterEggGranted;
	// Kept distinct from the original modal flag so existing profiles receive the inline welcome once.
	private boolean sidebarWelcomeSeen;
	private EggState activeEgg;
	private CompanionInstance activeCompanion;
	private List<EggState> stashedEggs = new ArrayList<>();
	private List<CompanionInstance> stashedCompanions = new ArrayList<>();
	private List<HatchReceipt> hatchHistory = new ArrayList<>();
	private List<String> ownedBackdropIds = new ArrayList<>();
	private String equippedBackdropId = Backdrop.CLASSIC.getAssetId();
	private List<String> ownedToyIds = new ArrayList<>();
	private String equippedToyId;
	private Map<String, Discovery> discoveries = new LinkedHashMap<>();
	private Map<String, Integer> skillBaselines = new LinkedHashMap<>();
	private Map<String, Integer> skillLevelBaselines = new LinkedHashMap<>();
	private Map<String, Integer> skillRemainders = new LinkedHashMap<>();
	private long overallXpBaseline;
	private int offlineXpRemainder;
	@SerializedName(value = "gotchiPoints", alternate = {"eggshells"})
	private long gotchiPoints;
	private long companionshipRemainder;
	// Unfinished incubation work and unused bonuses belong to the egg tier, not an egg instance.
	private Map<String, List<SkillingGoal>> carriedIncubationGoals = new LinkedHashMap<>();
	private Map<String, Long> incubationCredits = new LinkedHashMap<>();

	public static ProfileState fresh(String profileKey)
	{
		ProfileState state = new ProfileState();
		state.profileKey = profileKey;
		state.activeEgg = EggState.starter();
		state.starterEggGranted = true;
		state.ownedBackdropIds.add(Backdrop.CLASSIC.getAssetId());
		return state;
	}

	public int getSchemaVersion() { return schemaVersion; }
	public String getProfileKey() { return profileKey; }
	public EggState getActiveEgg() { return activeEgg; }
	public CompanionInstance getActiveCompanion() { return activeCompanion; }
	public List<EggState> getStashedEggs() { return stashedEggs; }
	public List<CompanionInstance> getStashedCompanions() { return stashedCompanions; }
	public List<HatchReceipt> getHatchHistory() { return hatchHistory; }
	public List<String> getOwnedBackdropIds() { return ownedBackdropIds; }
	public Backdrop getEquippedBackdrop() { return Backdrop.fromId(equippedBackdropId); }
	public List<String> getOwnedToyIds() { return ownedToyIds; }
	public Toy getEquippedToy() { return Toy.fromId(equippedToyId); }
	public Map<String, Discovery> getDiscoveries() { return discoveries; }
	public long getGotchiPoints() { return gotchiPoints; }
	public long getCompanionshipRemainder() { return companionshipRemainder; }
	public boolean isWelcomeSeen() { return sidebarWelcomeSeen; }
	public void setWelcomeSeen(boolean welcomeSeen) { this.sidebarWelcomeSeen = welcomeSeen; }
	public Map<String, Integer> getSkillBaselines() { return skillBaselines; }
	public Map<String, Integer> getSkillLevelBaselines() { return skillLevelBaselines; }
	public Map<String, Integer> getSkillRemainders() { return skillRemainders; }
	public long getOverallXpBaseline() { return overallXpBaseline; }
	public Map<String, List<SkillingGoal>> getCarriedIncubationGoals() { return carriedIncubationGoals; }
	public long getIncubationCredit(EggTier tier)
	{
		return tier == null || incubationCredits == null ? 0 : incubationCredits.getOrDefault(tier.name(), 0L);
	}
	public SkillingGoal getCarriedIncubationGoal(EggTier tier)
	{
		if (tier == null || carriedIncubationGoals == null) { return null; }
		List<SkillingGoal> goals = carriedIncubationGoals.get(tier.name());
		return goals == null || goals.isEmpty() ? null : goals.get(0);
	}

	public void repair()
	{
		if (stashedEggs == null) { stashedEggs = new ArrayList<>(); }
		if (stashedCompanions == null) { stashedCompanions = new ArrayList<>(); }
		if (hatchHistory == null) { hatchHistory = new ArrayList<>(); }
		if (ownedBackdropIds == null) { ownedBackdropIds = new ArrayList<>(); }
		if (ownedToyIds == null) { ownedToyIds = new ArrayList<>(); }
		if (equippedToyId != null && !ownedToyIds.contains(equippedToyId)) { equippedToyId = null; }
		if (!ownedBackdropIds.contains(Backdrop.CLASSIC.getAssetId())) { ownedBackdropIds.add(Backdrop.CLASSIC.getAssetId()); }
		Backdrop equipped = Backdrop.fromId(equippedBackdropId);
		if (!ownedBackdropIds.contains(equipped.getAssetId())) { equipped = Backdrop.CLASSIC; }
		equippedBackdropId = equipped.getAssetId();
		if (discoveries == null) { discoveries = new LinkedHashMap<>(); }
		if (skillBaselines == null) { skillBaselines = new LinkedHashMap<>(); }
		if (skillLevelBaselines == null) { skillLevelBaselines = new LinkedHashMap<>(); }
		if (skillRemainders == null) { skillRemainders = new LinkedHashMap<>(); }
		if (carriedIncubationGoals == null) { carriedIncubationGoals = new LinkedHashMap<>(); }
		if (incubationCredits == null) { incubationCredits = new LinkedHashMap<>(); }
		if (activeEgg != null)
		{
			if (activeEgg.isStarter()) { activeEgg.retargetPreservingXp(EggState.STARTER_HATCH_XP); }
			else { activeEgg.rebalanceTarget(activeEgg.getTier().getHatchXp()); }
		}
		if (activeCompanion != null) { activeCompanion.repair(); }
		for (CompanionInstance companion : stashedCompanions) { if (companion != null) { companion.repair(); } }
		for (EggState egg : stashedEggs)
		{
			if (egg == null) { continue; }
			if (egg.isStarter()) { egg.retargetPreservingXp(EggState.STARTER_HATCH_XP); }
			else { egg.rebalanceTarget(egg.getTier().getHatchXp()); }
		}
		settleCompletedIncubationGoals();
		applyIncubationCreditToActiveEgg();
	}

	public boolean startEggSkillingGoal(Skill skill, int target)
	{
		return activeEgg != null && getCarriedIncubationGoal(activeEgg.getTier()) == null
			&& activeEgg.startSkillingGoal(skill, target);
	}

	/** Count a skill delta toward just one goal, never several simultaneous egg tiers. */
	public void recordIncubationSkill(Skill skill, long rawXp)
	{
		if (rawXp <= 0 || !SkillingGoal.eligible(skill)) { return; }
		settleCompletedIncubationGoals();
		long remaining = rawXp;
		if (activeEgg != null && activeEgg.getSkillingGoal() != null
			&& skill.name().equals(activeEgg.getSkillingGoal().getSkill()))
		{
			remaining = activeEgg.getSkillingGoal().recordAndReturnRemainder(skill, remaining);
			settleCompletedIncubationGoals();
		}
		while (remaining > 0)
		{
			SkillingGoal next = firstCarriedIncubationGoalForSkill(skill);
			if (next == null) { break; }
			remaining = next.recordAndReturnRemainder(skill, remaining);
			settleCompletedIncubationGoals();
		}
	}

	private SkillingGoal firstCarriedIncubationGoalForSkill(Skill skill)
	{
		for (List<SkillingGoal> goals : carriedIncubationGoals.values())
		{
			if (goals == null) { continue; }
			for (SkillingGoal goal : goals)
			{
				if (goal != null && !goal.isComplete() && skill.name().equals(goal.getSkill())) { return goal; }
			}
		}
		return null;
	}

	public void settleCompletedIncubationGoals()
	{
		if (carriedIncubationGoals == null) { carriedIncubationGoals = new LinkedHashMap<>(); }
		if (incubationCredits == null) { incubationCredits = new LinkedHashMap<>(); }
		if (activeEgg != null && activeEgg.getSkillingGoal() != null
			&& activeEgg.getSkillingGoal().isComplete())
		{
			creditIncubationBonus(activeEgg.getTier(), activeEgg.claimSkillingGoal());
		}
		for (EggState egg : stashedEggs)
		{
			if (egg != null && egg.getSkillingGoal() != null && egg.getSkillingGoal().isComplete())
			{
				creditIncubationBonus(egg.getTier(), egg.claimSkillingGoal());
			}
		}
		Iterator<Map.Entry<String, List<SkillingGoal>>> entries = carriedIncubationGoals.entrySet().iterator();
		while (entries.hasNext())
		{
			Map.Entry<String, List<SkillingGoal>> entry = entries.next();
			EggTier tier;
			try { tier = EggTier.valueOf(entry.getKey()); }
			catch (IllegalArgumentException error) { entries.remove(); continue; }
			List<SkillingGoal> goals = entry.getValue();
			if (goals == null) { entries.remove(); continue; }
			Iterator<SkillingGoal> pending = goals.iterator();
			while (pending.hasNext())
			{
				SkillingGoal goal = pending.next();
				if (goal == null) { pending.remove(); }
				else if (goal.isComplete())
				{
					creditIncubationBonus(tier, goal.getReward());
					pending.remove();
				}
			}
			if (goals.isEmpty()) { entries.remove(); }
		}
	}

	private void creditIncubationBonus(EggTier tier, long amount)
	{
		if (tier == null || amount <= 0) { return; }
		incubationCredits.merge(tier.name(), amount, Long::sum);
		applyIncubationCreditToActiveEgg();
	}

	public void applyIncubationCreditToActiveEgg()
	{
		if (activeEgg == null || activeEgg.isReady() || incubationCredits == null) { return; }
		String tier = activeEgg.getTier().name();
		long credit = incubationCredits.getOrDefault(tier, 0L);
		if (credit <= 0) { return; }
		long applied = Math.min(credit, activeEgg.getTargetXp() - activeEgg.getHatchXp());
		activeEgg.addXp(applied);
		if (credit == applied) { incubationCredits.remove(tier); }
		else { incubationCredits.put(tier, credit - applied); }
	}

	public long reconcileOfflineXp(long currentOverallXp)
	{
		if (currentOverallXp < 0) { return 0; }
		long previous = overallXpBaseline;
		overallXpBaseline = currentOverallXp;
		if (previous <= 0)
		{
			offlineXpRemainder = 0;
			return 0;
		}
		if (currentOverallXp < previous)
		{
			offlineXpRemainder = 0;
			return 0;
		}
		if (currentOverallXp == previous) { return 0; }
		if (!canReceiveBondingXp())
		{
			offlineXpRemainder = 0;
			return 0;
		}
		long accumulated = currentOverallXp - previous + offlineXpRemainder;
		long bondingXp = accumulated / 5L;
		offlineXpRemainder = (int) (accumulated % 5L);
		award(bondingXp);
		return bondingXp;
	}

	public void updateOverallXpBaseline(long currentOverallXp)
	{
		if (currentOverallXp >= 0) { overallXpBaseline = currentOverallXp; }
	}

	public boolean hasEggOrCompanion()
	{
		return activeEgg != null || activeCompanion != null
			|| !stashedEggs.isEmpty() || !stashedCompanions.isEmpty();
	}

	private boolean canReceiveBondingXp()
	{
		return activeCompanion != null || activeEgg != null && !activeEgg.isReady();
	}

	public void award(long bondingXp)
	{
		if (bondingXp <= 0) { return; }
		if (activeEgg != null && !activeEgg.isReady()) { activeEgg.addXp(bondingXp); }
		else if (activeCompanion != null)
		{
			activeCompanion.addXp(bondingXp);
			companionshipRemainder += bondingXp;
			gotchiPoints += companionshipRemainder / 10_000L;
			companionshipRemainder %= 10_000L;
		}
	}

	public CompanionInstance revealActiveEgg()
	{
		if (activeEgg == null || !activeEgg.isReady() || activeEgg.getSealedResult() == null) { return null; }
		HatchReceipt receipt = activeEgg.getSealedResult();
		SkillingGoal unfinished = activeEgg.detachSkillingGoal();
		if (unfinished != null)
		{
			if (unfinished.isComplete()) { creditIncubationBonus(activeEgg.getTier(), unfinished.getReward()); }
			else { carriedIncubationGoals.computeIfAbsent(activeEgg.getTier().name(), key -> new ArrayList<>()).add(unfinished); }
		}
		receipt.markRevealed();
		CompanionInstance companion = CompanionInstance.from(receipt);
		hatchHistory.add(receipt);
		activeEgg = null;
		if (activeCompanion == null) { activeCompanion = companion; }
		else { stashedCompanions.add(companion); }
		Discovery discovery = discoveries.computeIfAbsent(companion.getSpeciesId(), ignored -> new Discovery());
		discovery.record(companion.getPalette());
		return companion;
	}

	public boolean stashActive()
	{
		if (activeEgg != null)
		{
			stashedEggs.add(activeEgg);
			activeEgg = null;
			return true;
		}
		if (activeCompanion != null)
		{
			stashedCompanions.add(activeCompanion);
			activeCompanion = null;
			return true;
		}
		return false;
	}

	public boolean activateEgg(int index)
	{
		if (activeEgg != null || activeCompanion != null || index < 0 || index >= stashedEggs.size()) { return false; }
		activeEgg = stashedEggs.remove(index);
		applyIncubationCreditToActiveEgg();
		return true;
	}

	public boolean activateCompanion(int index)
	{
		if (activeEgg != null || activeCompanion != null || index < 0 || index >= stashedCompanions.size()) { return false; }
		activeCompanion = stashedCompanions.remove(index);
		return true;
	}

	public boolean buyEgg(EggTier tier)
	{
		if (tier == null || activeEgg != null || activeCompanion != null || gotchiPoints < tier.getPrice()) { return false; }
		gotchiPoints -= tier.getPrice();
		activeEgg = EggState.purchased(tier);
		applyIncubationCreditToActiveEgg();
		return true;
	}

	public void grantGotchiPoints(long amount)
	{
		if (amount > 0) { gotchiPoints += amount; }
	}

	public boolean purchaseMegaWish(net.runelite.api.Skill skill)
	{
		if (activeCompanion == null || gotchiPoints < SkillingGoal.MEGA_PRICE
			|| !activeCompanion.startMegaWish(skill)) { return false; }
		gotchiPoints -= SkillingGoal.MEGA_PRICE;
		return true;
	}

	public boolean purchaseMegaWish(SkillingActivity activity)
	{
		if (activeCompanion == null || gotchiPoints < SkillingGoal.MEGA_PRICE
			|| !activeCompanion.startMegaWish(activity)) { return false; }
		gotchiPoints -= SkillingGoal.MEGA_PRICE;
		return true;
	}

	public boolean ownsBackdrop(Backdrop backdrop)
	{
		return backdrop != null && ownedBackdropIds.contains(backdrop.getAssetId());
	}

	public boolean purchaseBackdrop(Backdrop backdrop)
	{
		if (backdrop == null || ownsBackdrop(backdrop) || !hasEggOrCompanion()
			|| gotchiPoints < backdrop.getPrice()) { return false; }
		gotchiPoints -= backdrop.getPrice();
		ownedBackdropIds.add(backdrop.getAssetId());
		equippedBackdropId = backdrop.getAssetId();
		return true;
	}

	public boolean equipBackdrop(Backdrop backdrop)
	{
		if (!ownsBackdrop(backdrop)) { return false; }
		equippedBackdropId = backdrop.getAssetId();
		return true;
	}

	public boolean ownsToy(Toy toy)
	{
		return toy != null && ownedToyIds.contains(toy.getAssetId());
	}

	public boolean purchaseToy(Toy toy)
	{
		if (toy == null || ownsToy(toy) || !hasEggOrCompanion()
			|| gotchiPoints < toy.getPrice()) { return false; }
		gotchiPoints -= toy.getPrice();
		ownedToyIds.add(toy.getAssetId());
		equippedToyId = toy.getAssetId();
		return true;
	}

	public boolean equipToy(Toy toy)
	{
		if (toy != null && !ownsToy(toy)) { return false; }
		equippedToyId = toy == null ? null : toy.getAssetId();
		return true;
	}

	public boolean sellStashedCompanion(String instanceId, long value)
	{
		if (instanceId == null || value < 0) { return false; }
		for (int i = 0; i < stashedCompanions.size(); i++)
		{
			if (instanceId.equals(stashedCompanions.get(i).getInstanceId()))
			{
				stashedCompanions.remove(i);
				gotchiPoints += value;
				return true;
			}
		}
		return false;
	}
}
