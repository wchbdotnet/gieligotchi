package com.gieligotchi;

import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SecretEggType;
import com.gieligotchi.service.HatchService;
import com.gieligotchi.service.PetCatalogue;
import com.gieligotchi.ui.SpriteAssets;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.*;

public class SecretEggTest
{
	@Test
	public void secretEggsStaySeparateAndShareTheSingleActiveSlot()
	{
		ProfileState profile = ProfileState.fresh("secret-eggs");
		profile.grantSecretEgg(SecretEggType.RAIDS);
		profile.grantRegularEgg(EggTier.COMMON);
		assertEquals(1, profile.getSecretEggs().size());
		assertEquals(1, profile.getStashedEggs().size());
		assertFalse(profile.activateSecretEgg(0)); // Starter egg is active.
		assertTrue(profile.stashActive());
		assertTrue(profile.activateSecretEgg(0));
		assertEquals(SecretEggType.RAIDS, profile.getActiveEgg().getSecretType());
		assertFalse(profile.activateEgg(0));
		assertTrue(profile.stashActive());
		assertEquals(1, profile.getSecretEggs().size());
	}

	@Test
	public void deleteAllSecretEggsRemovesStoredAndActiveSecretEggsOnly()
	{
		ProfileState profile = ProfileState.fresh("delete-secret-eggs");
		assertTrue(profile.stashActive());
		profile.grantSecretEgg(SecretEggType.SKILLING);
		profile.grantSecretEgg(SecretEggType.SKILLING);
		assertTrue(profile.activateSecretEgg(0));

		assertEquals(2, profile.deleteAllSecretEggs());
		assertNull(profile.getActiveEgg());
		assertTrue(profile.getSecretEggs().isEmpty());
		assertEquals(1, profile.getStashedEggs().size());
		assertEquals(EggTier.COMMON, profile.getStashedEggs().get(0).getTier());
	}

	@Test
	public void everySecretCategoryHatchesOnlyItsOwnPool()
	{
		HatchService hatchService = new HatchService(new PetCatalogue(new Gson()));
		for (SecretEggType type : SecretEggType.values())
		{
			assertFalse(type.getSpeciesIds().isEmpty());
			for (int roll = 0; roll < 100; roll++)
			{
				HatchReceipt receipt = hatchService.roll(EggState.secret(type));
				assertEquals(type, receipt.getSecretEggType());
				assertTrue(receipt.getSpeciesId(), type.allows(receipt.getSpeciesId()));
				assertTrue(receipt.getCombinedChance() > 0d);
			}
		}
	}

	@Test
	public void saveRoundTripRetainsSecretTypeAndTarget()
	{
		Gson gson = new Gson();
		ProfileState profile = ProfileState.fresh("round-trip");
		profile.grantSecretEgg(SecretEggType.BOSSING);
		ProfileState restored = gson.fromJson(gson.toJson(profile), ProfileState.class);
		restored.repair();
		EggState egg = restored.getSecretEggs().get(0);
		assertEquals(SecretEggType.BOSSING, egg.getSecretType());
		assertEquals(SecretEggType.HATCH_XP, egg.getTargetXp());
	}

	@Test
	public void allSecretEggArtworkIsPresentAndDistinct()
	{
		BufferedImage previous = null;
		for (SecretEggType type : SecretEggType.values())
		{
			BufferedImage image = SpriteAssets.secretEgg(type);
			assertNotNull(type.name(), image);
			assertEquals(64, image.getWidth());
			assertEquals(64, image.getHeight());
			if (previous != null) { assertFalse(type.name(), samePixels(previous, image)); }
			previous = image;
		}
	}

	@Test
	public void everySecretEggKeepsItsOwnArtworkThroughoutHatching()
	{
		String[] names = {"idle_0", "wobble_left", "idle_1", "wobble_right", "idle_2",
			"crack_1", "crack_2", "crack_3", "split_open"};
		for (SecretEggType type : SecretEggType.values())
		{
			BufferedImage base = SpriteAssets.secretEgg(type);
			for (int frame = 0; frame < names.length; frame++)
			{
				BufferedImage image = SpriteAssets.secretEggFrame(type, frame, names[frame]);
				assertNotNull(type + " " + names[frame], image);
				assertEquals(64, image.getWidth());
				assertEquals(64, image.getHeight());
				if (frame >= 5) { assertFalse(type + " lacks " + names[frame], samePixels(base, image)); }
			}
		}
	}

	private static boolean samePixels(BufferedImage first, BufferedImage second)
	{
		if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) { return false; }
		for (int y = 0; y < first.getHeight(); y++)
		{
			for (int x = 0; x < first.getWidth(); x++)
			{
				if (first.getRGB(x, y) != second.getRGB(x, y)) { return false; }
			}
		}
		return true;
	}
}
