package com.gieligotchi;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.CompanionWish;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.RelationshipStage;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.model.Toy;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class JourneyModelTest
{
	private static final AtomicInteger TEST_REGION = new AtomicInteger(20_000);

	@Test
	public void wishesBuildPermanentRelationshipAndToyMemory()
	{
		CompanionInstance companion = companion();
		for (int i = 0; i < 30; i++)
		{
			complete(companion.getWish(), companion);
			assertTrue(companion.claimWish());
		}
		assertEquals(30, companion.getAffectionHearts());
		assertEquals(RelationshipStage.FRIEND, companion.getRelationshipStage());
		assertNotNull(companion.getPersonality());
		companion.playWithToy(Toy.PLAY_BALL);
		companion.playWithToy(Toy.PLAY_BALL);
		companion.playWithToy(Toy.PLAY_BALL);
		assertEquals(Toy.PLAY_BALL.getAssetId(), companion.getFavoriteToyId());
	}

	@Test
	public void toysArePermanentProfilePurchases()
	{
		ProfileState state = ProfileState.fresh("test");
		state.grantGotchiPoints(100);
		assertTrue(state.purchaseToy(Toy.RUNE_BLOCKS));
		assertTrue(state.ownsToy(Toy.RUNE_BLOCKS));
		assertEquals(Toy.RUNE_BLOCKS, state.getEquippedToy());
		assertFalse(state.purchaseToy(Toy.RUNE_BLOCKS));
	}

	@Test
	public void level99AutomaticallyCreatesLegacy()
	{
		CompanionInstance companion = companion();
		assertFalse(companion.isLegacy());
		companion.recordLevel99();
		assertTrue(companion.isLegacy());
		assertTrue(companion.getLegacyAt() > 0);
		assertTrue(companion.getMemories().stream()
			.anyMatch(memory -> "Legacy companion".equals(memory.getTitle())));
	}

	@Test
	public void questOrClueWishIgnoresOtherMajorChallenges() throws Exception
	{
		CompanionInstance companion = companion();
		CompanionWish wish = new CompanionWish(CompanionWish.Type.ADVENTURE, "Complete a quest or clue", 1);
		setWish(companion, wish);
		companion.recordMajorChallenge("Theatre of Blood");
		assertFalse(wish.isComplete());
		companion.recordQuestOrClue("Cook's Assistant");
		assertTrue(wish.isComplete());
	}

	@Test
	public void explorationWishOnlyAdvancesInANewArea() throws Exception
	{
		CompanionInstance companion = companion();
		CompanionWish wish = new CompanionWish(CompanionWish.Type.EXPLORATION, "Visit a new area", 1);
		setWish(companion, wish);
		companion.recordQuestOrClue("Cook's Assistant");
		assertFalse(wish.isComplete());
		companion.recordRegionVisit(12_345);
		assertTrue(wish.isComplete());
	}

	@Test
	public void majorChallengeWishIsIndependentFromQuestAndExploration() throws Exception
	{
		CompanionInstance companion = companion();
		CompanionWish wish = new CompanionWish(CompanionWish.Type.CHALLENGE,
			"Complete a raid, Gauntlet or Barbarian Assault Wave 10", 1);
		setWish(companion, wish);
		companion.recordQuestOrClue("Cook's Assistant");
		companion.recordRegionVisit(12_345);
		assertFalse(wish.isComplete());
		companion.recordMajorChallenge("Theatre of Blood");
		assertTrue(wish.isComplete());
	}

	@Test
	public void slayerTaskHasItsOwnWishProgress() throws Exception
	{
		CompanionInstance companion = companion();
		CompanionWish wish = new CompanionWish(CompanionWish.Type.SLAYER, "Complete a Slayer task", 1);
		setWish(companion, wish);
		companion.recordNpcKill("Abyssal demon", 124);
		assertFalse(wish.isComplete());
		companion.recordSlayerTask();
		assertTrue(wish.isComplete());
	}

	@Test
	public void wishTargetsAndRewardsScaleWithCompanionLevel()
	{
		assertEquals(80L, CompanionWish.forLevel(CompanionWish.Type.COMBAT, 1).getTarget());
		assertEquals(472L, CompanionWish.forLevel(CompanionWish.Type.COMBAT, 99).getTarget());
		assertEquals(4_000L, CompanionWish.forLevel(CompanionWish.Type.SKILLING, 1).getTarget());
		assertEquals(28_500L, CompanionWish.forLevel(CompanionWish.Type.SKILLING, 99).getTarget());
		assertEquals(1L, CompanionWish.forLevel(CompanionWish.Type.SLAYER, 1).getTarget());
		assertEquals(2L, CompanionWish.forLevel(CompanionWish.Type.SLAYER, 99).getTarget());
		assertEquals(1_500L, CompanionWish.rewardXp(CompanionWish.Type.COMBAT, 1));
		assertEquals(7_500L, CompanionWish.rewardXp(CompanionWish.Type.COMBAT, 99));
		assertEquals(15_000L, CompanionWish.rewardXp(CompanionWish.Type.CHALLENGE, 1));
		assertEquals(75_000L, CompanionWish.rewardXp(CompanionWish.Type.CHALLENGE, 99));
		assertEquals(3_000L, CompanionWish.rewardXp(CompanionWish.Type.SLAYER, 1));
		assertEquals(15_000L, CompanionWish.rewardXp(CompanionWish.Type.SLAYER, 99));
	}

	@Test
	public void wishSkipsStartAtThreeAndRegenerateFromBondingXp()
	{
		CompanionInstance companion = companion();
		assertEquals(3, companion.getWishSkips());
		assertTrue(companion.rerollWish());
		assertTrue(companion.rerollWish());
		assertTrue(companion.rerollWish());
		assertFalse(companion.rerollWish());
		companion.addXp(4_999L);
		assertEquals(0, companion.getWishSkips());
		assertEquals(4_999L, companion.getWishSkipXpRemainder());
		companion.addXp(1L);
		assertEquals(1, companion.getWishSkips());
		assertEquals(0L, companion.getWishSkipXpRemainder());
		companion.addXp(20_000L);
		assertEquals(3, companion.getWishSkips());
		assertEquals(0L, companion.getWishSkipXpRemainder());
	}

	private static CompanionInstance companion()
	{
		return CompanionInstance.from(new HatchReceipt("egg", EggTier.COMMON, "soup",
			SpeciesRarity.COMMON, 65, 10, Palette.BASE, 56.4, 0.1));
	}

	private static void complete(CompanionWish wish, CompanionInstance companion)
	{
		switch (wish.getType())
		{
			case COMBAT: companion.recordNpcKill("Test foe", (int) wish.getTarget()); break;
			case SKILLING: companion.recordSkill("WOODCUTTING", wish.getTarget()); break;
			case SLAYER:
				for (int i = 0; i < wish.getTarget(); i++) { companion.recordSlayerTask(); }
				break;
			case ADVENTURE: companion.recordQuestOrClue("Test quest"); break;
			case CHALLENGE: companion.recordMajorChallenge("Test raid"); break;
			case EXPLORATION: companion.recordRegionVisit(TEST_REGION.incrementAndGet()); break;
			case PLAY: companion.recordGame(true); break;
			default: throw new AssertionError();
		}
	}

	private static void setWish(CompanionInstance companion, CompanionWish wish) throws Exception
	{
		Field field = CompanionInstance.class.getDeclaredField("wish");
		field.setAccessible(true);
		field.set(companion, wish);
	}
}
