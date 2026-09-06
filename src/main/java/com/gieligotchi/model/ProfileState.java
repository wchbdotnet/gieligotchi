package com.gieligotchi.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
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
	@SerializedName(value = "gotchiPoints", alternate = {"eggshells"})
	private long gotchiPoints;
	private long companionshipRemainder;

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
		if (activeEgg != null)
		{
			activeEgg.rebalanceTarget(activeEgg.isStarter() ? 16_000L : activeEgg.getTier().getHatchXp());
		}
		if (activeCompanion != null) { activeCompanion.repair(); }
		for (CompanionInstance companion : stashedCompanions) { if (companion != null) { companion.repair(); } }
		for (EggState egg : stashedEggs)
		{
			if (egg != null) { egg.rebalanceTarget(egg.isStarter() ? 16_000L : egg.getTier().getHatchXp()); }
		}
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
		return true;
	}

	public void grantGotchiPoints(long amount)
	{
		if (amount > 0) { gotchiPoints += amount; }
	}

	public boolean ownsBackdrop(Backdrop backdrop)
	{
		return backdrop != null && ownedBackdropIds.contains(backdrop.getAssetId());
	}

	public boolean purchaseBackdrop(Backdrop backdrop)
	{
		if (backdrop == null || ownsBackdrop(backdrop) || gotchiPoints < backdrop.getPrice()) { return false; }
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
		if (toy == null || ownsToy(toy) || gotchiPoints < toy.getPrice()) { return false; }
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
