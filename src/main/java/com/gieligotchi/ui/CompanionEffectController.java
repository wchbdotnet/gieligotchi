package com.gieligotchi.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Singleton;

@Singleton
public class CompanionEffectController
{
	public enum Effect { HEARTS, CORRECT, CELEBRATE, WISH }

	private volatile Effect effect;
	private volatile long startedAt;

	public void trigger(Effect effect)
	{
		this.effect = effect;
		this.startedAt = System.currentTimeMillis();
	}

	public void render(Graphics2D graphics, int x, int y, int width, int height, double scale)
	{
		Effect current = effect;
		long elapsed = System.currentTimeMillis() - startedAt;
		long duration = current == Effect.CELEBRATE ? 2_800L : 2_000L;
		if (current == null || elapsed < 0 || elapsed >= duration) { return; }
		double life = elapsed / (double) duration;
		int count = current == Effect.CELEBRATE ? 18 : current == Effect.CORRECT ? 8 : 7;
		for (int i = 0; i < count; i++)
		{
			double phase = (life + i * 0.113) % 1d;
			int alpha = Math.max(0, Math.min(255, (int) Math.round(255 * (1d - phase))));
			double wave = Math.sin(i * 2.17 + life * 5d);
			int px = x + width / 2 + (int) Math.round(wave * width * (0.20 + (i % 3) * 0.05));
			int py = y + height - (int) Math.round(phase * height * 0.92) - (i % 2) * 4;
			if (current == Effect.CELEBRATE)
			{
				Color[] colours = {new Color(0xF2C45A), new Color(0xDB85A9), new Color(0x63B7D5), new Color(0x7CB06B)};
				Color colour = colours[i % colours.length];
				graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
				int size = Math.max(2, (int) Math.round((3 + i % 3) * scale));
				graphics.fillRect(px, py, size, Math.max(2, size / 2));
			}
			else
			{
				String glyph = current == Effect.CORRECT ? "✦" : i % 3 == 0 ? "✦" : "♥";
				Color colour = current == Effect.CORRECT ? new Color(0xF2C45A) : new Color(0xE77989);
				graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
				graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) Math.round((10 + i % 3 * 2) * scale))));
				graphics.drawString(glyph, px, py);
			}
		}
	}
}
