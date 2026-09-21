package com.gieligotchi.ui;

import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.*;

public class RiftglassVisualTest
{
	@Test
	public void wobbleSurvivesOpaqueBoundCropping()
	{
		BufferedImage centre = render(SpriteAssets.eggFrame(EggTier.RIFTGLASS, 0, "idle_0"));
		BufferedImage left = render(SpriteAssets.eggFrame(EggTier.RIFTGLASS, 1, "wobble_left"));
		BufferedImage right = render(SpriteAssets.eggFrame(EggTier.RIFTGLASS, 3, "wobble_right"));
		assertTrue(differentPixels(centre, left) > 100);
		assertTrue(differentPixels(centre, right) > 100);
		assertTrue(differentPixels(left, right) > 100);
		BufferedImage early = render(SpriteAssets.riftglassIdleFrame(1, 0d));
		BufferedImage nearlyReady = render(SpriteAssets.riftglassIdleFrame(1, 1d));
		assertTrue(differentPixels(centre, early) < differentPixels(centre, nearlyReady));
	}

	@Test
	public void legendaryChanceChangesNeutralShimmerRatherThanRingColour()
	{
		Gson gson = new Gson();
		EggState faint = gson.fromJson("{\"tier\":\"RIFTGLASS\",\"instanceId\":\"same\",\"attunement\":0}", EggState.class);
		EggState brilliant = gson.fromJson("{\"tier\":\"RIFTGLASS\",\"instanceId\":\"same\",\"attunement\":20}", EggState.class);
		assertEquals(0, RiftglassGlow.band(faint));
		assertEquals(3, RiftglassGlow.band(brilliant));
		BufferedImage source = SpriteAssets.eggFrame(EggTier.RIFTGLASS, 0, "idle_0");
		BufferedImage faintImage = RiftglassGlow.apply(source, faint, true);
		BufferedImage brilliantImage = RiftglassGlow.apply(source, brilliant, true);
		assertTrue(differentPixels(faintImage, brilliantImage) > 30);
		assertTrue(differentPixels(source, faintImage) > 100);
		for (int y = 0; y < brilliantImage.getHeight(); y++)
		{
			for (int x = 0; x < brilliantImage.getWidth(); x++)
			{
				assertEquals(source.getRGB(x, y) >>> 24, brilliantImage.getRGB(x, y) >>> 24);
				int colour = brilliantImage.getRGB(x, y);
				if ((colour >>> 24) == 0) { continue; }
			}
		}
	}

	private static BufferedImage render(BufferedImage source)
	{
		BufferedImage target = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		SpriteAssets.drawNearestOpaque(graphics, source, 4, 4, 88, 88);
		graphics.dispose();
		return target;
	}

	private static int differentPixels(BufferedImage first, BufferedImage second)
	{
		int changed = 0;
		for (int y = 0; y < first.getHeight(); y++)
		{
			for (int x = 0; x < first.getWidth(); x++)
			{
				if (first.getRGB(x, y) != second.getRGB(x, y)) { changed++; }
			}
		}
		return changed;
	}
}
