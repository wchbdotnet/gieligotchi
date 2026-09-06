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
import org.junit.Test;

import static org.junit.Assert.*;

public class JourneyModelTest
{
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
			case ADVENTURE: companion.recordAdventure("Test adventure"); break;
			case PLAY: companion.recordGame(true); break;
			default: throw new AssertionError();
		}
	}
}
