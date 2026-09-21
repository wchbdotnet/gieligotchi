package com.gieligotchi;

import com.google.gson.Gson;
import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.Backdrop;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.service.HatchService;
import com.gieligotchi.service.LevelCurve;
import com.gieligotchi.service.CompanionValue;
import com.gieligotchi.service.PetCatalogue;
import com.gieligotchi.service.SkillRewardPolicy;
import com.gieligotchi.service.ActivityRewardPolicy;
import com.gieligotchi.service.BossRegistry;
import com.gieligotchi.service.QuestRewardPolicy;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionModelTest
{
	@Test
	public void everySkillXpAndNpcKillsAwardBondingXp()
	{
		ProfileState state = ProfileState.fresh("rewards");
		assertEquals(0, SkillRewardPolicy.observe(state, Skill.WOODCUTTING, 1_000));
		assertEquals(1, SkillRewardPolicy.observe(state, Skill.WOODCUTTING, 1_006));
		assertEquals(1, SkillRewardPolicy.observe(state, Skill.WOODCUTTING, 1_011));
		assertEquals(2, SkillRewardPolicy.observe(state, Skill.WOODCUTTING, 1_020));
		assertEquals(0, SkillRewardPolicy.observe(state, Skill.ATTACK, 1_000));
		assertEquals(20, SkillRewardPolicy.observe(state, Skill.ATTACK, 1_100));
		assertEquals(10, SkillRewardPolicy.npcKillAward(2));
		assertEquals(0, SkillRewardPolicy.npcKillAward(0));
		assertEquals(400, SkillRewardPolicy.npcKillAward(96));
		assertEquals(400, SkillRewardPolicy.npcKillAward(146));
	}

	@Test
	public void levelUpsAndDocumentedActivitiesAwardBondingXp()
	{
		ProfileState state = ProfileState.fresh("levels");
		assertEquals(0, SkillRewardPolicy.observe(state, Skill.ATTACK, 0));
		assertEquals(1_266, SkillRewardPolicy.observe(state, Skill.ATTACK, 83));
		assertEquals(1_500, ActivityRewardPolicy.match(
			"Congratulations, you've completed a quest: Cook's Assistant").getAmount());
		assertEquals(5_000, ActivityRewardPolicy.match(
			"Congratulations, you've completed a quest: Dragon Slayer I").getAmount());
		assertEquals(10_000, ActivityRewardPolicy.match(
			"Congratulations, you've completed a quest: A Night at the Theatre").getAmount());
		assertEquals(20_000, ActivityRewardPolicy.match(
			"Congratulations, you've completed a quest: Song of the Elves").getAmount());
		assertEquals(5_000, QuestRewardPolicy.amountForDifficulty("Experienced"));
		assertEquals(18_500, ActivityRewardPolicy.match(
			"Your completed Chambers of Xeric Challenge Mode count is: 1").getAmount());
		assertEquals(2_000, ActivityRewardPolicy.match(
			"You have completed 12 medium Treasure Trails.").getAmount());
		assertEquals(30_000, ActivityRewardPolicy.npcKillAward("TzTok-Jad", 702));
		assertEquals(100_000, ActivityRewardPolicy.npcKillAward("TzKal-Zuk", 1400));
		assertEquals(50_000, ActivityRewardPolicy.npcKillAward("Sol Heredit", 1563));
		assertEquals(10_240, ActivityRewardPolicy.npcKillAward("Phosani's Nightmare", 1024));
		assertEquals(8_900, ActivityRewardPolicy.npcKillAward("Araxxor", 890));
		assertEquals(400, ActivityRewardPolicy.npcKillAward("Araxyte", 146));
		assertTrue(ActivityRewardPolicy.isMajorChallenge("cox"));
		assertTrue(ActivityRewardPolicy.isMajorChallenge("corrupted_gauntlet"));
		assertFalse(ActivityRewardPolicy.isMajorChallenge("pest_control_blue"));
		assertTrue(ActivityRewardPolicy.isQuestOrClue("clue_master"));
	}

	@Test
	public void bossRegistryIsComprehensiveAndDoesNotPromoteOrdinaryMobs()
	{
		assertTrue(BossRegistry.isBoss(-1, "General Graardor"));
		assertTrue(BossRegistry.isBoss(-1, "Great Olm"));
		assertTrue(BossRegistry.isBoss(-1, "Fragment of Seren"));
		assertTrue(BossRegistry.isBoss(240, "Black demon"));
		assertFalse(BossRegistry.isBoss(1432, "Black demon"));
		assertFalse(BossRegistry.isBoss(-1, "Araxyte"));
		assertFalse(BossRegistry.isBoss(-1, "Cave kraken"));
	}

	@Test
	public void levelUpRewardsFollowTheRebalancedMilestones()
	{
		assertEquals(1_250, SkillRewardPolicy.levelUpAward(2));
		assertEquals(1_800, SkillRewardPolicy.levelUpAward(20));
		assertEquals(3_000, SkillRewardPolicy.levelUpAward(40));
		assertEquals(5_000, SkillRewardPolicy.levelUpAward(60));
		assertEquals(10_000, SkillRewardPolicy.levelUpAward(80));
		assertEquals(35_000, SkillRewardPolicy.levelUpAward(99));
		assertEquals(35_000, SkillRewardPolicy.levelUpAward(126));
		for (int level = 2; level < 126; level++)
		{
			assertTrue(SkillRewardPolicy.levelUpAward(level + 1)
				>= SkillRewardPolicy.levelUpAward(level));
		}
	}

	@Test
	public void starterEggUsesReducedCalibrationTarget()
	{
		EggState egg = EggState.starter();
		assertEquals(12_500L, egg.getTargetXp());
		assertFalse(egg.isReady());
		egg.addXp(30_000L);
		assertTrue(egg.isReady());
		assertEquals(12_500L, egg.getHatchXp());
	}

	@Test
	public void existingStarterEggKeepsEarnedXpWhenTargetDrops()
	{
		ProfileState state = new Gson().fromJson("{\"profileKey\":\"legacy\",\"activeEgg\":"
			+ "{\"tier\":\"COMMON\",\"starter\":true,\"targetXp\":16000,\"hatchXp\":13000}}",
			ProfileState.class);
		state.repair();
		assertEquals(12_500L, state.getActiveEgg().getTargetXp());
		assertEquals(12_500L, state.getActiveEgg().getHatchXp());
		assertTrue(state.getActiveEgg().isReady());
	}

	@Test
	public void loginReconciliationAwardsOverallXpDifferenceOnce()
	{
		ProfileState state = ProfileState.fresh("offline-xp");
		assertEquals(0L, state.reconcileOfflineXp(1_000_000L));
		assertEquals(1L, state.reconcileOfflineXp(1_000_006L));
		assertEquals(1L, state.getActiveEgg().getHatchXp());
		assertEquals(0L, state.reconcileOfflineXp(1_000_006L));
		assertEquals(1L, state.reconcileOfflineXp(1_000_010L));
		assertEquals(2L, state.getActiveEgg().getHatchXp());
	}

	@Test
	public void eggRebalancingPreservesEarnedPercentage()
	{
		EggState egg = EggState.starter();
		egg.addXp(6_250L);
		egg.rebalanceTarget(25_000L);
		assertEquals(12_500L, egg.getHatchXp());
		assertEquals(0.5d, egg.getProgress(), 0.00001d);
	}

	@Test
	public void topRarityCapsAtFortyMillionXp()
	{
		HatchReceipt receipt = new HatchReceipt("egg", com.gieligotchi.model.EggTier.MEGA_RARE,
			"olmlet", SpeciesRarity.LEGENDARY, .08, 9, Palette.OBSIDIAN, .0005, .00000444);
		CompanionInstance companion = CompanionInstance.from(receipt);
		assertEquals(40_000_000L, LevelCurve.xpForLevel(companion, 99));
		companion.addXp(40_000_000L);
		assertEquals(99, LevelCurve.levelFor(companion));
	}

	@Test
	public void catalogueContainsAllApprovedSpecies()
	{
		PetCatalogue catalogue = new PetCatalogue(new Gson());
		assertEquals(71, catalogue.all().size());
		assertEquals(SpeciesRarity.LEGENDARY, catalogue.find("olmlet").getRarity());
		assertEquals(SpeciesRarity.LEGENDARY, catalogue.find("tumekens_guardian").getRarity());
	}

	@Test
	public void revealedCompanionCreatesPermanentDiscovery()
	{
		ProfileState state = ProfileState.fresh("test");
		EggState egg = state.getActiveEgg();
		egg.addXp(egg.getTargetXp());
		egg.seal(new HatchReceipt(egg.getInstanceId(), egg.getTier(), "olmlet",
			SpeciesRarity.LEGENDARY, .002, 9, Palette.BASE, .564, .000125));
		assertNotNull(state.revealActiveEgg());
		assertTrue(state.getDiscoveries().containsKey("olmlet"));
		assertEquals(1, state.getHatchHistory().size());
		assertEquals("olmlet", state.getHatchHistory().get(0).getSpeciesId());
		assertNotNull(state.getHatchHistory().get(0).getRevealedAt());
	}

	@Test
	public void legacyProfileRepairsMissingHatchHistory()
	{
		ProfileState state = new Gson().fromJson("{\"profileKey\":\"legacy\"}", ProfileState.class);
		state.repair();
		assertNotNull(state.getHatchHistory());
		assertTrue(state.getHatchHistory().isEmpty());
		assertTrue(state.ownsBackdrop(Backdrop.CLASSIC));
		assertEquals(Backdrop.CLASSIC, state.getEquippedBackdrop());
	}

	@Test
	public void cosmeticBackdropIsPermanentAndCanBeEquipped()
	{
		ProfileState state = ProfileState.fresh("cosmetics");
		state.grantGotchiPoints(500);
		assertTrue(state.purchaseBackdrop(Backdrop.LUMBRIDGE));
		assertEquals(350, state.getGotchiPoints());
		assertTrue(state.ownsBackdrop(Backdrop.LUMBRIDGE));
		assertEquals(Backdrop.LUMBRIDGE, state.getEquippedBackdrop());
		assertTrue(state.equipBackdrop(Backdrop.CLASSIC));
		assertEquals(Backdrop.CLASSIC, state.getEquippedBackdrop());
		assertFalse(state.purchaseBackdrop(Backdrop.LUMBRIDGE));
	}

	@Test
	public void everyShopBackdropHasA128PixelRuntimeAsset() throws Exception
	{
		for (Backdrop backdrop : Backdrop.values())
		{
			if (backdrop == Backdrop.CLASSIC) { continue; }
			String resource = "/com/gieligotchi/images/backdrops/" + backdrop.getAssetId() + ".png";
			java.net.URL url = ProgressionModelTest.class.getResource(resource);
			assertNotNull(resource, url);
			BufferedImage image = ImageIO.read(url);
			assertEquals(resource, 128, image.getWidth());
			assertEquals(resource, 128, image.getHeight());
		}
	}

	@Test
	public void companionCanBeStashedBeforeBuyingAnEgg()
	{
		ProfileState state = ProfileState.fresh("test");
		EggState egg = state.getActiveEgg();
		egg.addXp(egg.getTargetXp());
		egg.seal(new HatchReceipt(egg.getInstanceId(), egg.getTier(), "olmlet",
			SpeciesRarity.LEGENDARY, .002, 9, Palette.BASE, .564, .000125));
		assertNotNull(state.revealActiveEgg());
		assertTrue(state.stashActive());
		assertNull(state.getActiveCompanion());
		assertEquals(1, state.getStashedCompanions().size());
		state.grantGotchiPoints(100);
		assertTrue(state.buyEgg(com.gieligotchi.model.EggTier.COMMON));
		assertNotNull(state.getActiveEgg());
		assertEquals(0, state.getGotchiPoints());
	}

	@Test
	public void legacyCurrencyMigratesToGotchiPoints()
	{
		ProfileState state = new Gson().fromJson("{\"eggshells\":321}", ProfileState.class);
		assertEquals(321, state.getGotchiPoints());
	}

	@Test
	public void companionSaleCombinesRarityPaletteAndLifetimeXp()
	{
		HatchReceipt receipt = new HatchReceipt("egg", com.gieligotchi.model.EggTier.COMMON,
			"chompy_chick", SpeciesRarity.COMMON, .65, 10, Palette.BASE, .564, .03);
		CompanionInstance companion = CompanionInstance.from(receipt);
		assertEquals(100, CompanionValue.saleValue(companion));
		companion.addXp(500_000L);
		assertEquals(200, CompanionValue.saleValue(companion));

		HatchReceipt rareReceipt = new HatchReceipt("egg-2", com.gieligotchi.model.EggTier.MEGA_RARE,
			"olmlet", SpeciesRarity.LEGENDARY, .08, 9, Palette.OBSIDIAN, .0005, .00000444);
		CompanionInstance exceptional = CompanionInstance.from(rareReceipt);
		assertEquals(11_000, CompanionValue.saleValue(exceptional));
		exceptional.addXp(40_000_000L);
		assertEquals(19_000, CompanionValue.saleValue(exceptional));
	}

	@Test
	public void sellingStashedCompanionPaysFloorAndKeepsDiscovery()
	{
		ProfileState state = ProfileState.fresh("sale-test");
		EggState egg = state.getActiveEgg();
		egg.addXp(egg.getTargetXp());
		egg.seal(new HatchReceipt(egg.getInstanceId(), egg.getTier(), "chompy_chick",
			SpeciesRarity.COMMON, .65, 10, Palette.BASE, .564, .03));
		CompanionInstance companion = state.revealActiveEgg();
		assertTrue(state.stashActive());
		assertTrue(state.sellStashedCompanion(companion.getInstanceId(), CompanionValue.saleValue(companion)));
		assertEquals(100, state.getGotchiPoints());
		assertTrue(state.getStashedCompanions().isEmpty());
		assertTrue(state.getDiscoveries().containsKey("chompy_chick"));
	}
}
