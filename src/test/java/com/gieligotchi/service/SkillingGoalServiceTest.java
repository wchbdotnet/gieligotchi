package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.SpeciesRarity;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkillingGoalServiceTest
{
	@Test
	public void offlineSkillXpProgressesEggGoalOnceWithoutDoubleCountingBaseXp() throws Exception
	{
		Path directory = Files.createTempDirectory("gieligotchi-skilling-goal-test");
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			ProfileStore store = new ProfileStore(new Gson(), executor, directory);
			ProfileState initial = ProfileState.fresh("skiller");
			initial.updateOverallXpBaseline(10_000);
			initial.getSkillBaselines().put(Skill.COOKING.name(), 10_000);
			initial.getSkillLevelBaselines().put(Skill.COOKING.name(), SkillRewardPolicy.levelForXp(10_000));
			assertTrue(initial.getActiveEgg().startSkillingGoal(Skill.COOKING, 5_000));
			store.save("skiller", initial);
			executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

			GieligotchiStateService service = new GieligotchiStateService(store, new HatchService(null));
			CountDownLatch loaded = new CountDownLatch(1);
			service.addListener(() -> { if (service.getState() != null) { loaded.countDown(); } });
			service.load("skiller");
			assertTrue(loaded.await(5, TimeUnit.SECONDS));
			assertEquals(1_200, service.reconcileLoginXp(Collections.singletonMap(Skill.COOKING, 16_000), 16_000));
			assertNull(service.getState().getActiveEgg().getSkillingGoal());
			assertEquals(2_700, service.getState().getActiveEgg().getHatchXp());
			assertEquals(0, service.reconcileLoginXp(Collections.singletonMap(Skill.COOKING, 16_000), 16_000));
			assertEquals(2_700, service.getState().getActiveEgg().getHatchXp());
			assertEquals(0, service.claimEggSkillingGoal());
		}
		finally { executor.shutdownNow(); }
	}

	@Test
	public void offlineXpAfterHatchCompletesCarriedGoalForSameTier() throws Exception
	{
		Path directory = Files.createTempDirectory("gieligotchi-post-hatch-goal-test");
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			ProfileStore store = new ProfileStore(new Gson(), executor, directory);
			ProfileState initial = ProfileState.fresh("post-hatch");
			initial.updateOverallXpBaseline(10_000);
			initial.getSkillBaselines().put(Skill.COOKING.name(), 10_000);
			assertTrue(initial.startEggSkillingGoal(Skill.COOKING, 5_000));
			initial.recordIncubationSkill(Skill.COOKING, 3_000);
			EggState egg = initial.getActiveEgg();
			egg.addXp(egg.getTargetXp());
			egg.seal(new HatchReceipt(egg.getInstanceId(), egg.getTier(), "soup",
				SpeciesRarity.COMMON, 1, 1, Palette.BASE, 1, 1));
			assertNotNull(initial.revealActiveEgg());
			store.save("post-hatch", initial);
			executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

			GieligotchiStateService service = new GieligotchiStateService(store, new HatchService(null));
			CountDownLatch loaded = new CountDownLatch(1);
			service.addListener(() -> { if (service.getState() != null) { loaded.countDown(); } });
			service.load("post-hatch");
			assertTrue(loaded.await(5, TimeUnit.SECONDS));
			assertEquals(400, service.reconcileLoginXp(Collections.singletonMap(Skill.COOKING, 12_000), 12_000));
			assertNull(service.getState().getCarriedIncubationGoal(EggTier.COMMON));
			assertEquals(1_500, service.getState().getIncubationCredit(EggTier.COMMON));
			assertEquals(0, service.reconcileLoginXp(Collections.singletonMap(Skill.COOKING, 12_000), 12_000));
			assertEquals(1_500, service.getState().getIncubationCredit(EggTier.COMMON));
		}
		finally { executor.shutdownNow(); }
	}
}
