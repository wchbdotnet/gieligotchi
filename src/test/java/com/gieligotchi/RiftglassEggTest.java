package com.gieligotchi;

import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.service.HatchService;
import com.gieligotchi.service.PetCatalogue;
import com.gieligotchi.ui.SpriteAssets;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.*;

public class RiftglassEggTest
{
	@Test
	public void purchaseSetsPriceThresholdAndPermanentOdds()
	{
		assertEquals(1_500L, EggTier.RIFTGLASS.getPrice());
		assertEquals(500_000L, EggTier.RIFTGLASS.getHatchXp());
		ProfileState profile = ProfileState.fresh("riftglass");
		assertTrue(profile.stashActive());
		profile.grantGotchiPoints(1_500);
		assertTrue(profile.buyEgg(EggTier.RIFTGLASS));
		assertEquals(0L, profile.getGotchiPoints());
		EggState egg = profile.getActiveEgg();
		assertEquals(500_000L, egg.getTargetXp());
		double[] odds = egg.getSpeciesOdds();
		assertEquals(0d, odds[0], 0d);
		assertEquals(0d, odds[1], 0d);
		assertTrue(odds[2] >= 51d && odds[2] <= 81d);
		assertTrue(odds[3] >= 14d && odds[3] <= 24d);
		assertTrue(odds[4] >= 5d && odds[4] <= 25d);
		assertEquals(100d, odds[2] + odds[3] + odds[4], 0d);
		EggState restored = new Gson().fromJson(new Gson().toJson(egg), EggState.class);
		assertArrayEquals(odds, restored.getSpeciesOdds(), 0d);
	}

	@Test
	public void hatchUsesThatEggsOwnOdds()
	{
		HatchService service = new HatchService(new PetCatalogue(new Gson()));
		for (int i = 0; i < 100; i++)
		{
			EggState egg = EggState.purchased(EggTier.RIFTGLASS);
			HatchReceipt receipt = service.roll(egg);
			assertNotEquals(SpeciesRarity.COMMON, receipt.getSpeciesRarity());
			assertNotEquals(SpeciesRarity.UNCOMMON, receipt.getSpeciesRarity());
			assertEquals(egg.getSpeciesOdds()[receipt.getSpeciesRarity().ordinal()] / 100d,
				receipt.getSpeciesTierChance(), 0d);
		}
	}

	@Test
	public void dedicatedHatchFramesArePresentAndChangeAsTheEggCracks()
	{
		String[] names = {"idle_0", "wobble_left", "idle_1", "wobble_right", "idle_2",
			"crack_1", "crack_2", "crack_3", "split_open"};
		BufferedImage[] frames = new BufferedImage[names.length];
		for (int i = 0; i < names.length; i++)
		{
			frames[i] = SpriteAssets.eggFrame(EggTier.RIFTGLASS, i, names[i]);
			assertNotNull(frames[i]);
			assertEquals(96, frames[i].getWidth());
			assertEquals(96, frames[i].getHeight());
		}
		assertTrue(different(frames[0], frames[1]));
		for (int i = 5; i < 8; i++) { assertTrue(different(frames[i], frames[i + 1])); }
	}

	@Test
	public void everyEggTierHasDistinctCustomCrackingFrames()
	{
		String[] names = {"crack_1", "crack_2", "crack_3", "split_open"};
		for (EggTier tier : EggTier.values())
		{
			BufferedImage previous = null;
			for (int i = 0; i < names.length; i++)
			{
				BufferedImage frame = SpriteAssets.eggFrame(tier, i + 5, names[i]);
				assertNotNull(tier.name(), frame);
				if (previous != null) { assertTrue(tier.name(), different(previous, frame)); }
				previous = frame;
			}
		}
	}

	@Test
	public void crackingFramesUseTheFullAnimationWindowWithoutInvisiblePadding()
	{
		String[] names = {"crack_1", "crack_2", "crack_3", "split_open"};
		for (EggTier tier : EggTier.values())
		{
			for (int i = 0; i < names.length; i++)
			{
				BufferedImage frame = SpriteAssets.eggFrame(tier, i + 5, names[i]);
				int[] bounds = visibleBounds(frame);
				assertTrue(tier + " " + names[i] + " is too narrow", bounds[0] >= frame.getWidth() * 3 / 4);
				assertTrue(tier + " " + names[i] + " is too short", bounds[1] >= frame.getHeight() * 3 / 4);
			}
		}
	}

	private static int[] visibleBounds(BufferedImage image)
	{
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) >= 16)
				{
					minX = Math.min(minX, x); minY = Math.min(minY, y);
					maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
				}
			}
		}
		return new int[] {maxX - minX + 1, maxY - minY + 1};
	}

	private static boolean different(BufferedImage first, BufferedImage second)
	{
		for (int y = 0; y < first.getHeight(); y++)
		{
			for (int x = 0; x < first.getWidth(); x++)
			{
				if (first.getRGB(x, y) != second.getRGB(x, y)) { return true; }
			}
		}
		return false;
	}
}
