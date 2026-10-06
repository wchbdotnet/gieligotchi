package com.gieligotchi.service;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.Backdrop;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.HeartfeltWish;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SkillingGoal;
import com.gieligotchi.model.SkillingActivity;
import com.gieligotchi.model.Toy;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;

@Singleton
public class GieligotchiStateService
{
	private static final long TOY_PLAY_COOLDOWN_MILLIS = 5_000L;
	private final ProfileStore store;
	private final HatchService hatchService;
	private final SaveCodec codec;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile ProfileState state;
	private volatile String profileKey;
	@Inject private ProfileSync profileSync;
	private String rsProfile;
	private String lastSnapshot;
	private boolean newProfile;
	private long loadGeneration;
	private volatile long replacementVersion;
	private volatile String syncStatus = "Log into a character to manage saves.";
	private List<ProfileState> conflicts = new java.util.ArrayList<>();
	private final Random heartfeltRandom = new Random();

	@Inject
	public GieligotchiStateService(ProfileStore store, HatchService hatchService, SaveCodec codec)
	{
		this.store = store;
		this.hatchService = hatchService;
		this.codec = codec;
	}

	GieligotchiStateService(ProfileStore store, HatchService hatchService, ProfileSync profileSync, SaveCodec codec)
	{
		this(store, hatchService, codec);
		this.profileSync = profileSync;
	}

	public void load(String key)
	{
		load(key, null);
	}

	public synchronized void load(String key, String runeScapeProfile)
	{
		final long generation = ++loadGeneration;
		rsProfile = runeScapeProfile;
		profileKey = key;
		state = null;
		lastSnapshot = null;
		conflicts.clear();
		newProfile = !store.exists(key);
		fireChanged();
		store.load(key, loaded ->
		{
			synchronized (this)
			{
				if (generation != loadGeneration || !key.equals(profileKey)) { return; }
				state = loaded;
				lastSnapshot = codec.json(state);
				sync(false);
				newProfile = false;
				recordLevel99IfNeeded();
				sealIfReady();
				persist();
				fireChanged();
			}
		});
	}

	public String getSyncStatus() { return syncStatus; }
	public synchronized void unload()
	{
		loadGeneration++;
		state = null;
		profileKey = null;
		rsProfile = null;
		lastSnapshot = null;
		conflicts.clear();
		syncStatus = "Log into a character to manage saves.";
		fireChanged();
	}
	public long getReplacementVersion() { return replacementVersion; }
	public synchronized boolean hasSaveConflict() { return !conflicts.isEmpty(); }
	public java.nio.file.Path getSaveDirectory() { return store.getDirectory(); }
	public synchronized String exportSave()
	{
		if (state == null) { throw new IllegalStateException("Log into a character first."); }
		return codec.json(state);
	}

	public synchronized void importSave(String json, String expectedCharacter) throws java.io.IOException
	{
		if (state == null || !state.getProfileKey().equals(expectedCharacter))
		{ throw new java.io.IOException("Character changed. Please try again."); }
		ProfileState imported = codec.read(json, profileKey);
		store.archive(state);
		store.archive(imported);
		SaveCodec.acknowledge(imported, state);
		state = imported;
		lastSnapshot = null;
		replacementVersion++;
		persist();
		sealIfReady();
		persist();
		syncStatus = "Save imported. Previous progress kept in recovery files.";
		fireChanged();
	}

	public synchronized List<String> getSaveChoices()
	{
		List<String> choices = new java.util.ArrayList<>();
		choices.add("Keep this device · " + saveDescription(state));
		for (int i = 0; i < conflicts.size(); i++)
		{ choices.add("Other save " + (i + 1) + " · " + saveDescription(conflicts.get(i))); }
		return choices;
	}

	private String saveDescription(ProfileState saved)
	{
		if (saved == null) { return "not loaded"; }
		String journey = "empty slot";
		if (saved.getActiveEgg() != null)
		{
			EggState egg = saved.getActiveEgg();
			journey = egg.getTier().getDisplayName() + " egg " + egg.getHatchXp() + "/" + egg.getTargetXp() + " XP";
		}
		else if (saved.getActiveCompanion() != null)
		{
			CompanionInstance companion = saved.getActiveCompanion();
			journey = companion.getDisplayName(companion.getSpeciesId()) + ", " + companion.getLifetimeXp() + " XP";
		}
		return saved.getGotchiPoints() + " points, " + saved.getHatchHistory().size() + " hatches, "
			+ journey;
	}

	public synchronized String getConflictToken()
	{
		StringBuilder token = new StringBuilder();
		for (ProfileState alternative : conflicts) { token.append(codec.json(alternative)); }
		return token.toString();
	}

	public synchronized void resolveSave(int choice, String expectedCharacter, String token) throws java.io.IOException
	{
		if (state == null || !profileKey.equals(expectedCharacter) || conflicts.isEmpty()
			|| !getConflictToken().equals(token) || choice < 0 || choice > conflicts.size())
		{ throw new java.io.IOException("Save choices changed. Open them again."); }
		ProfileState selected = codec.read(codec.json(choice == 0 ? state : conflicts.get(choice - 1)), profileKey);
		store.archive(state);
		for (ProfileState alternative : conflicts) { store.archive(alternative); }
		SaveCodec.acknowledge(selected, state);
		for (ProfileState alternative : conflicts) { SaveCodec.acknowledge(selected, alternative); }
		state = selected;
		conflicts.clear();
		lastSnapshot = null;
		replacementVersion++;
		persist();
		sync(true);
		fireChanged();
	}

	public synchronized void syncNow(String runeScapeProfile)
	{
		if (runeScapeProfile != null) { rsProfile = runeScapeProfile; }
		sync(true);
	}

	private void sync(boolean publish)
	{
		if (state == null || profileSync == null) { return; }
		String oldStatus = syncStatus;
		try
		{
			String unavailable = profileSync.unavailable(rsProfile);
			if (unavailable != null) { syncStatus = unavailable; return; }
			List<ProfileState> remote = profileSync.read(rsProfile, profileKey);
			List<ProfileState> candidates = new java.util.ArrayList<>();
			if (!newProfile || remote.isEmpty()) { candidates.add(state); }
			candidates.addAll(remote);
			List<ProfileState> latest = new java.util.ArrayList<>();
			for (ProfileState candidate : candidates)
			{
				boolean superseded = false;
				for (ProfileState other : candidates)
				{
					// An unversioned existing local save predates sync: require an explicit choice.
					if (!candidate.getSaveVersions().isEmpty() && SaveCodec.dominates(other, candidate)) { superseded = true; break; }
				}
				if (!superseded && latest.stream().noneMatch(existing -> codec.json(existing).equals(codec.json(candidate))))
				{ latest.add(candidate); }
			}
			conflicts.clear();
			if (latest.size() > 1)
			{
				for (ProfileState alternative : latest)
				{ if (!codec.json(alternative).equals(codec.json(state))) { conflicts.add(alternative); } }
				syncStatus = "Two devices have different progress. Open Save & sync to choose. Neither is overwritten.";
				return;
			}
			if (!latest.isEmpty() && latest.get(0) != state && !codec.json(latest.get(0)).equals(codec.json(state)))
			{
				store.archive(state);
				state = latest.get(0);
				lastSnapshot = codec.json(state);
				replacementVersion++;
				store.save(profileKey, state);
				fireChanged();
			}
			if (publish)
			{
				persist();
				profileSync.publish(rsProfile, store.deviceId(), state);
			}
			syncStatus = "Save queued for RuneLite sync. Close this client before switching devices; keep an export backup.";
		}
		catch (Exception error) { syncStatus = "Sync paused: " + error.getMessage() + " Local progress is safe."; }
		finally { if (!syncStatus.equals(oldStatus)) { fireChanged(); } }
	}

	public synchronized long observeSkill(Skill skill, int xp, long overallXp)
	{
		return observeSkill(skill, xp, overallXp, -1, -1, -1);
	}

	public synchronized long observeSkill(Skill skill, int xp, long overallXp, int x, int y, int plane)
	{
		if (state == null) { return 0; }
		Integer previous = skill == null ? null : state.getSkillBaselines().get(skill.name());
		long rawDelta = previous == null ? 0 : Math.max(0, xp - previous);
		long award = SkillRewardPolicy.observe(state, skill, xp);
		state.updateOverallXpBaseline(overallXp);
		if (award > 0) { state.award(award); recordLevel99IfNeeded(); }
		state.recordIncubationSkill(skill, rawDelta);
		sealIfReady();
		CompanionInstance companion = state.getActiveCompanion();
		if (companion != null && rawDelta > 0)
		{
			companion.recordSkill(skill.name(), rawDelta);
			companion.recordHeartfeltSkill(skill.name(), rawDelta, x, y, plane);
			if (companion.getMegaWish() != null) { companion.getMegaWish().record(skill, rawDelta); }
		}
		persist();
		if (award > 0 || rawDelta > 0) { fireChanged(); }
		return award;
	}

	public synchronized long reconcileLoginXp(Map<Skill, Integer> currentXp, long currentOverallXp)
	{
		if (state == null || currentXp == null) { return 0; }
		if (state.getOverallXpBaseline() <= 0 && !currentXp.isEmpty())
		{
			long inferredOverallXp = 0;
			boolean completeBaseline = true;
			for (Skill skill : currentXp.keySet())
			{
				Integer savedXp = state.getSkillBaselines().get(skill.name());
				if (savedXp == null)
				{
					completeBaseline = false;
					break;
				}
				inferredOverallXp += savedXp;
			}
			if (completeBaseline) { state.updateOverallXpBaseline(inferredOverallXp); }
		}
		long previousOverallXp = state.getOverallXpBaseline();
		long award = state.reconcileOfflineXp(currentOverallXp);
		boolean validOfflineDelta = previousOverallXp > 0 && currentOverallXp >= previousOverallXp;
		for (Map.Entry<Skill, Integer> entry : currentXp.entrySet())
		{
			Skill skill = entry.getKey();
			Integer xp = entry.getValue();
			if (skill != null && skill != Skill.OVERALL && xp != null && xp >= 0)
			{
				Integer prior = state.getSkillBaselines().get(skill.name());
				if (validOfflineDelta && prior != null && xp > prior)
				{
					long delta = (long) xp - prior;
					state.recordIncubationSkill(skill, delta);
					CompanionInstance companion = state.getActiveCompanion();
					if (companion != null && companion.getMegaWish() != null)
					{
						companion.getMegaWish().record(skill, delta);
					}
				}
				state.getSkillBaselines().put(skill.name(), xp);
				state.getSkillLevelBaselines().put(skill.name(), SkillRewardPolicy.levelForXp(xp));
			}
		}
		if (award > 0) { recordLevel99IfNeeded(); }
		sealIfReady();
		persist();
		if (award > 0 || validOfflineDelta) { fireChanged(); }
		return award;
	}

	public synchronized long awardNpcKill(int npcId, String name, int combatLevel)
	{
		if (state == null) { return 0; }
		long amount = ActivityRewardPolicy.npcKillAward(npcId, name, combatLevel);
		state.award(amount);
		recordLevel99IfNeeded();
		if (state.getActiveCompanion() != null)
		{
			state.getActiveCompanion().recordNpcKill(name, combatLevel);
			state.getActiveCompanion().recordHeartfeltNpc(name);
		}
		sealIfReady();
		persist();
		fireChanged();
		return amount;
	}

	public synchronized long awardActivity(String id, String label, long amount)
	{
		if (state == null || amount <= 0) { return 0; }
		state.award(amount);
		recordLevel99IfNeeded();
		if (state.getActiveCompanion() != null)
		{
			state.getActiveCompanion().recordHeartfeltActivity(id);
			if (ActivityRewardPolicy.isQuestOrClue(id))
			{
				state.getActiveCompanion().recordQuestOrClue(label);
			}
			else if (ActivityRewardPolicy.isMajorChallenge(id))
			{
				state.getActiveCompanion().recordMajorChallenge(label);
			}
		}
		sealIfReady();
		persist();
		fireChanged();
		return amount;
	}

	public synchronized long awardGameplay(long amount)
	{
		if (state == null || amount <= 0) { return 0; }
		state.award(amount);
		recordLevel99IfNeeded();
		sealIfReady();
		persist();
		fireChanged();
		return amount;
	}

	public synchronized long awardSlayerTask()
	{
		if (state == null) { return 0; }
		long amount = ActivityRewardPolicy.slayerTaskAward();
		state.award(amount);
		recordLevel99IfNeeded();
		if (state.getActiveCompanion() != null) { state.getActiveCompanion().recordSlayerTask(); }
		sealIfReady();
		persist();
		fireChanged();
		return amount;
	}

	public synchronized CompanionInstance reveal()
	{
		if (state == null) { return null; }
		CompanionInstance companion = state.revealActiveEgg();
		if (companion != null) { maybeOfferHeartfelt(companion); persist(); fireChanged(); }
		return companion;
	}

	public synchronized void markReadyNotificationSent()
	{
		if (state == null || state.getActiveEgg() == null) { return; }
		state.getActiveEgg().setReadyNotificationSent(true);
		persist();
		fireChanged();
	}

	public synchronized void markWelcomeSeen()
	{
		if (state == null || state.isWelcomeSeen()) { return; }
		state.setWelcomeSeen(true);
		persist();
		fireChanged();
	}

	public synchronized boolean stashActive()
	{
		if (state == null || !state.stashActive()) { return false; }
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean activateEgg(int index)
	{
		if (state == null || !state.activateEgg(index)) { return false; }
		sealIfReady();
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean activateCompanion(int index)
	{
		if (state == null || !state.activateCompanion(index)) { return false; }
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean buyEgg(EggTier tier)
	{
		if (state == null || !state.buyEgg(tier)) { return false; }
		sealIfReady();
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean purchaseBackdrop(Backdrop backdrop)
	{
		if (state == null || !state.purchaseBackdrop(backdrop)) { return false; }
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean equipBackdrop(Backdrop backdrop)
	{
		if (state == null || !state.equipBackdrop(backdrop)) { return false; }
		persist();
		fireChanged();
		return true;
	}

	public synchronized boolean purchaseToy(Toy toy)
	{
		if (state == null || !state.purchaseToy(toy)) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized boolean equipToy(Toy toy)
	{
		if (state == null || !state.equipToy(toy)) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized boolean playWithToy(Toy toy)
	{
		if (state == null || state.getActiveCompanion() == null || !state.ownsToy(toy)) { return false; }
		if (System.currentTimeMillis() - state.getActiveCompanion().getLastToyPlayedAt()
			< TOY_PLAY_COOLDOWN_MILLIS) { return false; }
		state.equipToy(toy);
		state.getActiveCompanion().playWithToy(toy);
		persist(); fireChanged(); return true;
	}

	public synchronized boolean recordGame(boolean won)
	{
		if (state == null || state.getActiveCompanion() == null) { return false; }
		state.getActiveCompanion().recordGame(won);
		persist(); fireChanged(); return true;
	}

	public synchronized void recordRegionVisit(int regionId)
	{
		if (state == null || state.getActiveCompanion() == null) { return; }
		state.getActiveCompanion().recordRegionVisit(regionId);
		persist(); fireChanged();
	}

	public synchronized boolean claimWish()
	{
		if (state == null || state.getActiveCompanion() == null) { return false; }
		CompanionInstance companion = state.getActiveCompanion();
		com.gieligotchi.model.CompanionWish wish = companion.getWish();
		if (wish == null || !wish.isComplete()) { return false; }
		long rewardXp = com.gieligotchi.model.CompanionWish.rewardXp(wish.getType(), LevelCurve.levelFor(companion));
		state.award(rewardXp);
		recordLevel99IfNeeded();
		if (!companion.claimWish(LevelCurve.levelFor(companion))) { return false; }
		state.grantGotchiPoints(3);
		maybeOfferHeartfelt(companion);
		persist(); fireChanged(); return true;
	}

	public synchronized boolean rerollWish()
	{
		if (state == null || state.getActiveCompanion() == null) { return false; }
		CompanionInstance companion = state.getActiveCompanion();
		if (!companion.rerollWish(LevelCurve.levelFor(companion))) { return false; }
		maybeOfferHeartfelt(companion);
		persist(); fireChanged(); return true;
	}

	public synchronized boolean acceptHeartfeltWish()
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion == null || !companion.acceptHeartfeltWish()) { return false; }
		persist(); fireChanged(); return true;
	}

	/** Heartfelt Wishes always pass for free and never consume an ordinary wish skip. */
	public synchronized boolean passHeartfeltWish()
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion == null || !companion.passHeartfeltWish()) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized boolean claimHeartfeltWish()
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		HeartfeltWish wish = companion == null ? null : companion.getHeartfeltWish();
		if (wish == null || !wish.isComplete()) { return false; }
		long rewardXp = wish.getRewardXp();
		long rewardPoints = wish.getRewardPoints();
		if (companion.finishHeartfeltWish() == null) { return false; }
		state.award(rewardXp);
		state.grantGotchiPoints(rewardPoints);
		recordLevel99IfNeeded();
		persist(); fireChanged(); return true;
	}

	public synchronized void recordHeartfeltItem(int itemId, long quantity, int x, int y, int plane)
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion == null || companion.getHeartfeltWish() == null || quantity <= 0) { return; }
		long before = companion.getHeartfeltWish().getProgress();
		companion.recordHeartfeltItem(itemId, quantity, x, y, plane);
		if (companion.getHeartfeltWish().getProgress() != before) { persist(); fireChanged(); }
	}

	public synchronized boolean startEggSkillingGoal(Skill skill, int target)
	{
		if (state == null || !state.startEggSkillingGoal(skill, target)) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized long claimEggSkillingGoal()
	{
		EggState egg = state == null ? null : state.getActiveEgg();
		if (egg == null) { return 0; }
		SkillingGoal goal = egg.getSkillingGoal();
		if (goal == null || !goal.isComplete()) { return 0; }
		long reward = goal.getReward();
		state.settleCompletedIncubationGoals();
		sealIfReady();
		persist(); fireChanged(); return reward;
	}

	public synchronized boolean purchaseMegaWish(Skill skill)
	{
		if (state == null || !state.purchaseMegaWish(skill)) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized boolean purchaseMegaWish(SkillingActivity activity)
	{
		if (state == null || !state.purchaseMegaWish(activity)) { return false; }
		persist(); fireChanged(); return true;
	}

	public synchronized void recordSkillingActivity(SkillingActivity.Completion completion)
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion == null || completion == null) { return; }
		long megaBefore = companion.getMegaWish() == null ? -1 : companion.getMegaWish().getProgress();
		long heartfeltBefore = companion.getHeartfeltWish() == null ? -1 : companion.getHeartfeltWish().getProgress();
		if (companion.getMegaWish() != null) { companion.getMegaWish().record(completion); }
		companion.recordHeartfeltActivity(completion.getActivity().name());
		if (companion.getMegaWish() != null && companion.getMegaWish().getProgress() > megaBefore
			|| companion.getHeartfeltWish() != null && companion.getHeartfeltWish().getProgress() != heartfeltBefore)
		{ persist(); fireChanged(); }
	}

	public synchronized long claimMegaWish()
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion == null) { return 0; }
		long reward = companion.claimMegaWish();
		if (reward <= 0) { return 0; }
		state.award(reward);
		recordLevel99IfNeeded();
		persist(); fireChanged(); return reward;
	}

	public synchronized void renameActiveCompanion(String name)
	{
		if (state == null || state.getActiveCompanion() == null) { return; }
		state.getActiveCompanion().rename(name); persist(); fireChanged();
	}

	public synchronized long sellStashedCompanion(String instanceId)
	{
		if (state == null || instanceId == null) { return 0L; }
		CompanionInstance companion = state.getStashedCompanions().stream()
			.filter(candidate -> instanceId.equals(candidate.getInstanceId())).findFirst().orElse(null);
		long value = CompanionValue.saleValue(companion);
		if (companion == null || !state.sellStashedCompanion(instanceId, value)) { return 0L; }
		persist();
		fireChanged();
		return value;
	}

	public ProfileState getState() { return state; }
	public void addListener(Runnable listener) { listeners.add(listener); }
	public void removeListener(Runnable listener) { listeners.remove(listener); }
	public synchronized void backup()
	{
		ProfileState current = state;
		String key = profileKey;
		if (current != null && key != null) { store.backup(key, current); }
	}

	public synchronized boolean resetProfile()
	{
		if (profileKey == null) { return false; }
		ProfileState fresh = ProfileState.fresh(profileKey);
		if (state != null) { SaveCodec.acknowledge(fresh, state); }
		state = fresh;
		persist();
		store.reset(profileKey, state);
		fireChanged();
		return true;
	}

	private void maybeOfferHeartfelt(CompanionInstance companion)
	{
		if (companion != null) { companion.considerHeartfeltWish(accountLevels(), accountTotalLevel(), heartfeltRandom); }
	}

	private Map<String, Integer> accountLevels()
	{
		return state == null ? java.util.Collections.emptyMap() : state.getSkillLevelBaselines();
	}

	private int accountTotalLevel()
	{
		if (state == null) { return 1; }
		int total = 0;
		for (Map.Entry<String, Integer> entry : state.getSkillLevelBaselines().entrySet())
		{
			if (!"OVERALL".equals(entry.getKey()) && entry.getValue() != null) { total += Math.max(1, entry.getValue()); }
		}
		return Math.max(1, total);
	}

	private void sealIfReady()
	{
		ProfileState current = state;
		if (current == null) { return; }
		EggState egg = current.getActiveEgg();
		if (egg != null && egg.isReady() && egg.getSealedResult() == null)
		{
			HatchReceipt receipt = hatchService.roll(egg);
			egg.seal(receipt);
		}
	}

	private void recordLevel99IfNeeded()
	{
		CompanionInstance companion = state == null ? null : state.getActiveCompanion();
		if (companion != null && LevelCurve.levelFor(companion) >= 99) { companion.recordLevel99(); }
	}

	private void persist()
	{
		ProfileState current = state;
		String key = profileKey;
		if (current != null && key != null)
		{
			String snapshot = codec.json(current);
			if (!snapshot.equals(lastSnapshot) || current.getSaveVersions().isEmpty())
			{
				try { current.getSaveVersions().merge(store.deviceId(), 1L, Long::sum); }
				catch (java.io.IOException error) { syncStatus = "Local save only: cannot create device identity."; }
				lastSnapshot = codec.json(current);
				store.save(key, current);
			}
		}
	}

	private void fireChanged()
	{
		SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
	}
}
