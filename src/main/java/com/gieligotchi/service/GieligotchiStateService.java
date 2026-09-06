package com.gieligotchi.service;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.Backdrop;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.Toy;
import java.util.List;
import java.util.Map;
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
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile ProfileState state;
	private volatile String profileKey;

	@Inject
	public GieligotchiStateService(ProfileStore store, HatchService hatchService)
	{
		this.store = store;
		this.hatchService = hatchService;
	}

	public void load(String key)
	{
		profileKey = key;
		state = null;
		fireChanged();
		store.load(key, loaded ->
		{
			if (!key.equals(profileKey)) { return; }
			state = loaded;
			recordLevel99IfNeeded();
			sealIfReady();
			persist();
			fireChanged();
		});
	}

	public synchronized long observeSkill(Skill skill, int xp)
	{
		if (state == null) { return 0; }
		Integer previous = skill == null ? null : state.getSkillBaselines().get(skill.name());
		long rawDelta = previous == null ? 0 : Math.max(0, xp - previous);
		long award = SkillRewardPolicy.observe(state, skill, xp);
		if (award > 0) { state.award(award); recordLevel99IfNeeded(); sealIfReady(); }
		CompanionInstance companion = state.getActiveCompanion();
		if (companion != null && rawDelta > 0) { companion.recordSkill(skill.name(), rawDelta); }
		persist();
		if (award > 0 || rawDelta > 0) { fireChanged(); }
		return award;
	}

	public synchronized void synchronizeSkillBaselines(Map<Skill, Integer> currentXp)
	{
		if (state == null || currentXp == null) { return; }
		for (Map.Entry<Skill, Integer> entry : currentXp.entrySet())
		{
			Skill skill = entry.getKey();
			Integer xp = entry.getValue();
			if (skill != null && skill != Skill.OVERALL && xp != null && xp >= 0)
			{
				state.getSkillBaselines().put(skill.name(), xp);
				state.getSkillLevelBaselines().put(skill.name(), SkillRewardPolicy.levelForXp(xp));
			}
		}
		persist();
	}

	public synchronized long awardNpcKill(String name, int combatLevel)
	{
		if (state == null) { return 0; }
		long amount = ActivityRewardPolicy.npcKillAward(name, combatLevel);
		state.award(amount);
		recordLevel99IfNeeded();
		if (state.getActiveCompanion() != null) { state.getActiveCompanion().recordNpcKill(name, combatLevel); }
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

	public synchronized CompanionInstance reveal()
	{
		if (state == null) { return null; }
		CompanionInstance companion = state.revealActiveEgg();
		if (companion != null) { persist(); fireChanged(); }
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
		if (state == null || state.getActiveCompanion() == null || !state.getActiveCompanion().claimWish()) { return false; }
		state.grantGotchiPoints(3);
		persist(); fireChanged(); return true;
	}

	public synchronized void rerollWish()
	{
		if (state == null || state.getActiveCompanion() == null) { return; }
		state.getActiveCompanion().rerollWish(); persist(); fireChanged();
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
		if (current != null && key != null) { store.save(key, current); }
	}

	private void fireChanged()
	{
		SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
	}
}
