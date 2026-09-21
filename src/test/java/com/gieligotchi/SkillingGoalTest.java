package com.gieligotchi;

import com.google.gson.Gson;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SkillingGoal;
import com.gieligotchi.model.SkillingActivity;
import com.gieligotchi.model.SpeciesRarity;
import java.util.ArrayList;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkillingGoalTest
{
	@Test
	public void eggGoalOnlyCountsSelectedNonCombatSkillAndClaimsOnce()
	{
		EggState egg = EggState.starter();
		assertFalse(egg.startSkillingGoal(Skill.ATTACK, 5_000));
		assertFalse(egg.startSkillingGoal(Skill.COOKING, 7_000));
		assertFalse(egg.startSkillingGoal(Skill.COOKING, 10_000));
		assertTrue(egg.startSkillingGoal(Skill.COOKING, 5_000));
		assertFalse(egg.startSkillingGoal(Skill.FISHING, 10_000));
		SkillingGoal goal = egg.getSkillingGoal();
		goal.record(Skill.ATTACK, 100_000);
		goal.record(Skill.FISHING, 100_000);
		assertEquals(0, goal.getProgress());
		goal.record(Skill.COOKING, 4_999);
		assertEquals(0, egg.claimSkillingGoal());
		goal.record(Skill.COOKING, 10);
		assertTrue(goal.isComplete());
		assertEquals(5_000, goal.getProgress());
		assertEquals(1_500, egg.claimSkillingGoal());
		assertEquals(0, egg.claimSkillingGoal());
	}

	@Test
	public void higherEggGoalsHaveBetterReturnAndPersist()
	{
		for (int i = 1; i < SkillingGoal.EGG_TARGETS.length; i++)
		{
			assertTrue(SkillingGoal.EGG_REWARDS[i] * SkillingGoal.EGG_TARGETS[i - 1]
				> SkillingGoal.EGG_REWARDS[i - 1] * SkillingGoal.EGG_TARGETS[i]);
		}
		ProfileState profile = ProfileState.fresh("skiller");
		assertTrue(profile.stashActive());
		profile.grantGotchiPoints(500);
		assertTrue(profile.buyEgg(EggTier.MEGA_RARE));
		assertTrue(profile.getActiveEgg().startSkillingGoal(Skill.WOODCUTTING, 100_000));
		profile.getActiveEgg().getSkillingGoal().record(Skill.WOODCUTTING, 12_345);
		ProfileState loaded = new Gson().fromJson(new Gson().toJson(profile), ProfileState.class);
		loaded.repair();
		assertEquals(12_345, loaded.getActiveEgg().getSkillingGoal().getProgress());
		assertEquals(50_000, loaded.getActiveEgg().getSkillingGoal().getReward());
	}

	@Test
	public void megaWishRequiresPointsAndDoesNotAllowAnotherUntilClaimed()
	{
		ProfileState profile = ProfileState.fresh("skiller");
		assertFalse(profile.purchaseMegaWish(Skill.COOKING));
		assertNull(SkillingGoal.mega(Skill.STRENGTH));
		EggState egg = profile.getActiveEgg();
		egg.addXp(egg.getTargetXp());
		egg.seal(new HatchReceipt(egg.getInstanceId(), egg.getTier(), "soup",
			SpeciesRarity.COMMON, 1, 1, Palette.BASE, 1, 1));
		profile.revealActiveEgg();
		profile.grantGotchiPoints(5);
		assertFalse(profile.purchaseMegaWish(Skill.ATTACK));
		assertTrue(profile.purchaseMegaWish(Skill.COOKING));
		assertEquals(0, profile.getGotchiPoints());
		assertFalse(profile.purchaseMegaWish(Skill.FISHING));
		SkillingGoal wish = profile.getActiveCompanion().getMegaWish();
		assertEquals(0, profile.getActiveCompanion().claimMegaWish());
		wish.record(Skill.COOKING, 100_000);
		assertEquals(35_000, profile.getActiveCompanion().claimMegaWish());
		assertEquals(0, profile.getActiveCompanion().claimMegaWish());
	}

	@Test
	public void activityMegaWishUsesCompletionCountersWithoutDuplicates()
	{
		SkillingGoal goal = SkillingGoal.mega(SkillingActivity.HALLOWED_SEPULCHRE);
		SkillingActivity.Completion floor = SkillingActivity.match(
			"You have completed Floor 5 of the Hallowed Sepulchre! Total completions: 42.");
		assertNotNull(floor);
		goal.record(floor);
		goal.record(floor);
		assertEquals(1, goal.getProgress());
		goal.record(SkillingActivity.match("Amount of Rifts you have closed: 9."));
		assertEquals(1, goal.getProgress());
		goal.record(SkillingActivity.match(
			"You have completed Floor 5 of the Hallowed Sepulchre! Total completions: 43."));
		assertEquals(2, goal.getProgress());
		assertNotNull(SkillingActivity.match("Your Wintertodt kill count is: 1"));
		assertNotNull(SkillingActivity.match("Your subdued Tempoross count is: 1"));
		assertNotNull(SkillingActivity.match("Your Zalcano kill count is: 1"));
		assertNotNull(SkillingActivity.match("Your Herbiboar harvest count is: 1"));
		assertNull(SkillingActivity.match("Your Araxxor kill count is: 1"));
	}

	@Test
	public void unfinishedIncubationContinuesAfterHatchAndOnlyCreditsSameTier()
	{
		ProfileState profile = ProfileState.fresh("carryover");
		assertTrue(profile.startEggSkillingGoal(Skill.COOKING, 5_000));
		profile.recordIncubationSkill(Skill.COOKING, 3_000);
		EggState starter = profile.getActiveEgg();
		starter.addXp(starter.getTargetXp());
		starter.seal(receipt(starter));
		assertNotNull(profile.revealActiveEgg());
		profile = new Gson().fromJson(new Gson().toJson(profile), ProfileState.class);
		profile.repair();
		assertEquals(3_000, profile.getCarriedIncubationGoal(EggTier.COMMON).getProgress());
		profile.recordIncubationSkill(Skill.COOKING, 2_000);
		assertNull(profile.getCarriedIncubationGoal(EggTier.COMMON));
		assertEquals(1_500, profile.getIncubationCredit(EggTier.COMMON));
		assertTrue(profile.stashActive());
		profile.grantGotchiPoints(350);
		assertTrue(profile.buyEgg(EggTier.RARE));
		assertEquals(0, profile.getActiveEgg().getHatchXp());
		assertEquals(1_500, profile.getIncubationCredit(EggTier.COMMON));
		assertTrue(profile.stashActive());
		assertTrue(profile.buyEgg(EggTier.COMMON));
		assertEquals(1_500, profile.getActiveEgg().getHatchXp());
		assertEquals(0, profile.getIncubationCredit(EggTier.COMMON));
	}

	@Test
	public void incubationBonusOverflowIsSavedRatherThanLost()
	{
		ProfileState profile = ProfileState.fresh("overflow");
		assertTrue(profile.startEggSkillingGoal(Skill.COOKING, 5_000));
		profile.award(12_000);
		profile.recordIncubationSkill(Skill.COOKING, 5_000);
		assertEquals(12_500, profile.getActiveEgg().getHatchXp());
		assertEquals(1_000, profile.getIncubationCredit(EggTier.COMMON));
		EggState starter = profile.getActiveEgg();
		starter.seal(receipt(starter));
		profile.revealActiveEgg();
		profile = new Gson().fromJson(new Gson().toJson(profile), ProfileState.class);
		profile.repair();
		assertEquals(1_000, profile.getIncubationCredit(EggTier.COMMON));
		assertTrue(profile.stashActive());
		profile.grantGotchiPoints(100);
		assertTrue(profile.buyEgg(EggTier.COMMON));
		assertEquals(1_000, profile.getActiveEgg().getHatchXp());
		assertEquals(0, profile.getIncubationCredit(EggTier.COMMON));
	}

	@Test
	public void largeOfflineDeltaAdvancesNextExistingCarriedGoalWithoutDuplication()
	{
		ProfileState profile = ProfileState.fresh("offline-carryover");
		profile.getCarriedIncubationGoals().put(EggTier.RARE.name(), new ArrayList<>());
		profile.getCarriedIncubationGoals().get(EggTier.RARE.name()).add(SkillingGoal.egg(Skill.COOKING, 5_000));
		profile.getCarriedIncubationGoals().get(EggTier.RARE.name()).add(SkillingGoal.egg(Skill.COOKING, 10_000));
		profile.recordIncubationSkill(Skill.COOKING, 8_000);
		assertEquals(1, profile.getCarriedIncubationGoals().get(EggTier.RARE.name()).size());
		assertEquals(3_000, profile.getCarriedIncubationGoal(EggTier.RARE).getProgress());
		assertEquals(1_500, profile.getIncubationCredit(EggTier.RARE));
		profile.recordIncubationSkill(Skill.COOKING, 7_000);
		assertNull(profile.getCarriedIncubationGoal(EggTier.RARE));
		assertEquals(5_000, profile.getIncubationCredit(EggTier.RARE));
	}

	private static HatchReceipt receipt(EggState egg)
	{
		return new HatchReceipt(egg.getInstanceId(), egg.getTier(), "soup",
			SpeciesRarity.COMMON, 1, 1, Palette.BASE, 1, 1);
	}
}
