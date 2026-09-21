package com.gieligotchi.ui;

import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import java.awt.image.BufferedImage;

/** A pearl sheen clipped to the shell: it represents odds, never the eventual hatch rarity. */
final class RiftglassGlow
{
	private RiftglassGlow() {}

	static int band(EggState egg)
	{
		if (egg == null || egg.getTier() != EggTier.RIFTGLASS) { return 0; }
		return Math.min(3, Math.max(0, ((int) egg.getSpeciesOdds()[4] - 5) / 5));
	}

	static BufferedImage apply(BufferedImage source, EggState egg, boolean reducedMotion)
	{
		if (source == null || egg == null || egg.getTier() != EggTier.RIFTGLASS) { return source; }

		double strength = Math.max(0d, Math.min(1d, (egg.getSpeciesOdds()[4] - 5d) / 20d));
		double phase = reducedMotion ? 0.48d : (System.currentTimeMillis() % 3_200L) / 3_200d;
		// Travel beyond both edges so the shell has a calm pause between sweeps.
		double centre = -0.24d + phase * 1.48d;
		double width = 0.065d + strength * 0.035d;
		double peak = 0.30d + strength * 0.25d;
		int imageWidth = source.getWidth();
		int imageHeight = source.getHeight();
		BufferedImage result = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < imageHeight; y++)
		{
			for (int x = 0; x < imageWidth; x++)
			{
				int argb = source.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0) { continue; }
				double position = (x * 0.72d + y) / Math.max(1d, imageWidth * 0.72d + imageHeight);
				double distance = Math.abs(position - centre);
				double amount = distance >= width ? 0d : peak * Math.pow(1d - distance / width, 1.7d);
				int red = (argb >> 16) & 0xFF;
				int green = (argb >> 8) & 0xFF;
				int blue = argb & 0xFF;
				// Off-white with nearly equal channels keeps this pearlescent rather than rarity-coded.
				red = blend(red, 252, amount);
				green = blend(green, 252, amount);
				blue = blend(blue, 248, amount);
				result.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		return result;
	}

	private static int blend(int original, int pearl, double amount)
	{
		return Math.min(255, Math.max(0, (int) Math.round(original + (pearl - original) * amount)));
	}
}
