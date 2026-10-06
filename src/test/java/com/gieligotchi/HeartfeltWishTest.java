package com.gieligotchi;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.HeartfeltStep;
import com.gieligotchi.model.HeartfeltWish;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.service.HeartfeltCatalogue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class HeartfeltWishTest
{
	@Test
	public void locationItemVerifierRejectsTheSameItemOutsideItsArea()
	{
		HeartfeltStep step = HeartfeltStep.items("Tin", 2, 438).inArea(10, 20, 30, 40, 0);
		assertFalse(step.recordItem(438, 1, 9, 35, 0));
		assertFalse(step.recordItem(438, 1, 15, 35, 1));
		assertTrue(step.recordItem(438, 1, 15, 35, 0));
		assertTrue(step.recordItem(438, 1, 15, 35, 0));
		assertTrue(step.isComplete());
	}

	@Test
	public void everyGenericVerifierOnlyAcceptsItsOwnEvent()
	{
		HeartfeltStep skill = HeartfeltStep.skill("Craft", "CRAFTING", 100);
		assertFalse(skill.recordSkill("MINING", 100, 0, 0, 0));
		assertTrue(skill.recordSkill("CRAFTING", 100, 0, 0, 0));
		HeartfeltStep npc = HeartfeltStep.kills("Mole", 1, "Giant Mole");
		assertFalse(npc.recordNpc("Zulrah"));
		assertTrue(npc.recordNpc("giant mole"));
		HeartfeltStep activity = HeartfeltStep.activity("Clue", 1, "clue_easy");
		assertFalse(activity.recordActivity("clue_hard"));
		assertTrue(activity.recordActivity("CLUE_EASY"));
	}

	@Test
	public void offeredWishDoesNotProgressUntilAccepted()
	{
		HeartfeltWish wish = HeartfeltCatalogue.force("rimmington_tin", levels("MINING", 50), 900);
		assertFalse(wish.isAccepted());
		assertFalse(wish.recordItem(438, 50, 2970, 3220, 0));
		wish.accept();
		assertTrue(wish.recordItem(438, 50, 2970, 3220, 0));
		assertTrue(wish.isComplete());
	}

	@Test
	public void multiStepWishRequiresEveryDistinctStep()
	{
		HeartfeltWish wish = HeartfeltCatalogue.force("green_dhide_set", levels("CRAFTING", 70), 1_300);
		wish.accept();
		assertTrue(wish.recordItem(1065, 1, 0, 0, 0));
		assertTrue(wish.recordItem(1099, 1, 0, 0, 0));
		assertFalse(wish.isComplete());
		assertTrue(wish.recordItem(1135, 1, 0, 0, 0));
		assertTrue(wish.isComplete());
	}

	@Test
	public void ordinaryAndApexPointCapsStayIsolated()
	{
		Map<String, Integer> max = levels("FIREMAKING", 99);
		HeartfeltWish ordinary = HeartfeltCatalogue.force("wintertodt_unique", max, 2_277);
		HeartfeltWish apex = HeartfeltCatalogue.force("cox_purple", max, 2_277);
		assertEquals(500, ordinary.getRewardPoints());
		assertEquals(1_000, apex.getRewardPoints());
		assertFalse(ordinary.isApex());
		assertTrue(apex.isApex());
	}

	@Test
	public void earlySimpleRewardStaysBelowACommonEggSale()
	{
		HeartfeltWish wish = HeartfeltCatalogue.force("rimmington_tin", levels("MINING", 1), 200);
		assertTrue(wish.getRewardPoints() >= 15);
		assertTrue(wish.getRewardPoints() < 100);
	}

	@Test
	public void aHeartfeltWishAppearsWithinFiveGenerationsAndPassIsFree()
	{
		CompanionInstance companion = companion();
		for (int i = 0; i < 5 && companion.getHeartfeltWish() == null; i++)
		{ companion.considerHeartfeltWish(Collections.emptyMap(), 1, new Random(7)); }
		assertNotNull(companion.getHeartfeltWish());
		assertTrue(companion.passHeartfeltWish());
		assertNull(companion.getHeartfeltWish());
		assertEquals(3, companion.getWishSkips());
	}

	@Test
	public void completionCreatesAHeartfeltMemory()
	{
		CompanionInstance companion = companion();
		HeartfeltWish wish = HeartfeltCatalogue.force("rimmington_tin", levels("MINING", 50), 900);
		wish.completeForDevelopment();
		companion.forceHeartfeltWish(wish);
		assertSame(wish, companion.finishHeartfeltWish());
		assertEquals(2, companion.getAffectionHearts());
		assertEquals(1, companion.getHeartfeltCompletions());
		assertTrue(companion.getMemories().stream().anyMatch(memory -> "Heartfelt wish fulfilled".equals(memory.getTitle())));
	}

	@Test
	public void heartfeltProgressAndCompletionsBelongToOneCompanion()
	{
		CompanionInstance first = companion();
		CompanionInstance second = companion();
		HeartfeltWish wish = HeartfeltCatalogue.force("rimmington_tin", levels("MINING", 50), 900);
		wish.accept();
		first.forceHeartfeltWish(wish);
		first.recordHeartfeltItem(438, 50, 2970, 3220, 0);
		assertTrue(first.getHeartfeltWish().isComplete());
		assertNull(second.getHeartfeltWish());
		assertEquals(0, second.getHeartfeltCompletions());
		first.finishHeartfeltWish();
		assertEquals(1, first.getHeartfeltCompletions());
		assertEquals(0, second.getHeartfeltCompletions());
	}

	@Test
	public void entireCatalogueHasUniqueIdsSerializableProgressAndSafeCaps()
	{
		assertTrue(HeartfeltCatalogue.taskIds().size() >= 35);
		assertEquals(HeartfeltCatalogue.taskIds().size(), new HashSet<>(HeartfeltCatalogue.taskIds()).size());
		Gson gson = new Gson();
		for (String id : HeartfeltCatalogue.taskIds())
		{
			HeartfeltWish original = HeartfeltCatalogue.force(id, levels("CRAFTING", 99), 2_277);
			assertFalse(original.getSteps().isEmpty());
			assertTrue(original.getRewardPoints() <= (original.isApex() ? 1_000 : 500));
			original.advanceForDevelopment();
			HeartfeltWish restored = gson.fromJson(gson.toJson(original), HeartfeltWish.class);
			restored.repair();
			assertEquals(original.getTaskId(), restored.getTaskId());
			assertEquals(original.getProgress(), restored.getProgress());
		}
	}

	private static Map<String, Integer> levels(String skill, int level)
	{
		Map<String, Integer> result = new HashMap<>();
		result.put(skill, level);
		return result;
	}

	private static CompanionInstance companion()
	{
		return CompanionInstance.from(new HatchReceipt("egg", EggTier.COMMON, "pet_kraken",
			SpeciesRarity.COMMON, 65, 10, Palette.BASE, 56.4, .1));
	}
}
