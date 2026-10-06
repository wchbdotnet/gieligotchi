package com.gieligotchi.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import javax.inject.Singleton;

@Singleton
public class CompanionEffectController
{
	public enum Effect { HEARTS, CORRECT, CELEBRATE, WISH, LEVEL_UP, MAX_LEVEL }

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
		if (current == null) { return; }
		long elapsed = System.currentTimeMillis() - startedAt;
		long duration = current == Effect.MAX_LEVEL ? 4_600L
			: current == Effect.CELEBRATE ? 2_800L
			: current == Effect.LEVEL_UP ? 2_200L : 2_000L;
		if (elapsed < 0 || elapsed >= duration) { return; }
		double life = elapsed / (double) duration;
		if (current == Effect.MAX_LEVEL)
		{
			renderMaxLevel(graphics, x, y, width, height, scale, life);
			return;
		}
		if (current == Effect.LEVEL_UP)
		{
			renderLevelUp(graphics, x, y, width, height, scale, life);
			return;
		}
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

	private void renderLevelUp(Graphics2D graphics, int x, int y, int width, int height,
		double scale, double life)
	{
		Color[] colours = {new Color(0xFFF0A6), new Color(0xF2C45A), new Color(0xFFFFFF)};
		int bursts = 2;
		int rays = 9;
		Stroke oldStroke = graphics.getStroke();
		for (int burst = 0; burst < bursts; burst++)
		{
			double local = life * 1.45 - burst * 0.17;
			if (local < 0 || local > 1) { continue; }
			int centreX = x + width / 2 + (int) Math.round(Math.sin(burst * 2.4) * width * 0.18);
			int centreY = y + (int) Math.round(height * (0.30 + (burst % 3) * 0.18));
			double radius = (10 + local * 28) * scale;
			int alpha = Math.max(0, Math.min(255, (int) Math.round(245 * (1 - local))));
			graphics.setStroke(new BasicStroke(Math.max(1f, (float) (1.7 * scale))));
			for (int ray = 0; ray < rays; ray++)
			{
				double angle = Math.PI * 2 * ray / rays + burst * 0.61;
				double inner = radius * 0.46;
				Color colour = colours[(burst + ray) % colours.length];
				graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
				graphics.drawLine(centreX + (int) Math.round(Math.cos(angle) * inner),
					centreY + (int) Math.round(Math.sin(angle) * inner),
					centreX + (int) Math.round(Math.cos(angle) * radius),
					centreY + (int) Math.round(Math.sin(angle) * radius));
			}
		}

		int sparkCount = 10;
		for (int i = 0; i < sparkCount; i++)
		{
			double phase = (life * 1.25 + i * 0.087) % 1d;
			int alpha = Math.max(0, (int) Math.round(220 * (1 - phase)));
			int px = x + (int) Math.round(width * (0.08 + ((i * 37) % 85) / 100d));
			int py = y + height - (int) Math.round(phase * height * 0.95);
			Color colour = colours[i % colours.length];
			graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
			int size = Math.max(2, (int) Math.round(3 * scale));
			graphics.fillOval(px, py, size, size);
		}
		graphics.setStroke(oldStroke);
	}

	private void renderMaxLevel(Graphics2D graphics, int x, int y, int width, int height,
		double scale, double life)
	{
		Stroke oldStroke = graphics.getStroke();
		int centreX = x + width / 2;
		int centreY = y + (int) Math.round(height * 0.53);
		double fadeIn = Math.min(1d, life * 8d);
		double fadeOut = Math.min(1d, (1d - life) * 3d);
		int alpha = Math.max(0, Math.min(170, (int) Math.round(170 * fadeIn * fadeOut)));
		double breath = (Math.sin(life * Math.PI * 5) + 1d) / 2d;

		// A quiet sunburst sits behind the companion instead of filling the overlay.
		graphics.setStroke(new BasicStroke(Math.max(1f, (float) (1.25 * scale))));
		for (int ray = 0; ray < 12; ray++)
		{
			double angle = Math.PI * 2 * ray / 12d - Math.PI / 2d;
			double inner = (28 + breath * 3) * scale;
			double outer = (42 + breath * 7 + (ray % 2) * 6) * scale;
			graphics.setColor(new Color(0xFF, 0xE5, 0x91, alpha / 2));
			graphics.drawLine(centreX + (int) Math.round(Math.cos(angle) * inner),
				centreY + (int) Math.round(Math.sin(angle) * inner),
				centreX + (int) Math.round(Math.cos(angle) * outer),
				centreY + (int) Math.round(Math.sin(angle) * outer));
		}

		for (int ring = 0; ring < 2; ring++)
		{
			double phase = (life * 1.15 + ring * 0.48) % 1d;
			int ringAlpha = Math.max(0, (int) Math.round(120 * (1d - phase) * fadeOut));
			int radius = (int) Math.round((28 + phase * 34) * scale);
			graphics.setColor(new Color(0xFF, 0xF2, 0xC1, ringAlpha));
			graphics.drawOval(centreX - radius, centreY - radius, radius * 2, radius * 2);
		}

		// A handful of white-gold motes rise slowly through the halo.
		for (int i = 0; i < 12; i++)
		{
			double phase = (life * 0.9 + i * 0.079) % 1d;
			int moteAlpha = Math.max(0, (int) Math.round(190 * (1d - phase) * fadeOut));
			int px = centreX + (int) Math.round(Math.sin(i * 2.31) * width * 0.26);
			int py = y + height - (int) Math.round(phase * height * 0.88);
			int size = Math.max(2, (int) Math.round((2 + i % 2) * scale));
			graphics.setColor(new Color(0xFF, 0xF5, 0xD6, moteAlpha));
			graphics.fillOval(px, py, size, size);
		}
		graphics.setStroke(oldStroke);
	}
}
