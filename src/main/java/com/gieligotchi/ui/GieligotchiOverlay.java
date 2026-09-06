package com.gieligotchi.ui;

import com.gieligotchi.GieligotchiConfig;
import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Toy;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.LevelCurve;
import com.gieligotchi.service.PetCatalogue;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

@Singleton
public class GieligotchiOverlay extends Overlay
{
	private final GieligotchiStateService stateService;
	private final GieligotchiConfig config;
	private final HatchAnimationController hatchAnimation;
	private final PetCatalogue catalogue;
	private final CompanionEffectController effects;
	private volatile boolean hovered;

	@Inject
	public GieligotchiOverlay(GieligotchiStateService stateService, GieligotchiConfig config,
		HatchAnimationController hatchAnimation, PetCatalogue catalogue, CompanionEffectController effects)
	{
		this.stateService = stateService;
		this.config = config;
		this.hatchAnimation = hatchAnimation;
		this.catalogue = catalogue;
		this.effects = effects;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.MED);
		setMovable(false);
	}

	public void syncMovement() { setMovable(config.unlockOverlay()); }
	public void setHovered(boolean hovered) { this.hovered = hovered; }

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay()) { return null; }
		ProfileState state = stateService.getState();
		if (state == null) { return null; }
		// The former 75% presentation is now the canonical 100% size.
		double scale = config.overlayScale() / 100d * 0.75d;
		if (hatchAnimation.isCeremonyActive()) { return renderCeremony(graphics, state, scale); }
		boolean revealProgress = hovered;
		int visualWidth = Math.max(80, (int) Math.round(132 * scale));
		EggState egg = state.getActiveEgg();
		CompanionInstance companion = state.getActiveCompanion();
		BufferedImage sprite = egg != null ? hatchAnimation.eggImage(egg, config.reducedMotion())
			: companion != null ? SpriteAssets.companion(companion) : null;
		int artSize = Math.min(visualWidth - 12, (int) Math.round(118 * scale));
		int barHeight = Math.max(8, (int) Math.round(10 * scale));
		int barY = 5 + artSize + Math.max(2, (int) Math.round(3 * scale));
		int baseHeight = barY + barHeight + Math.max(5, (int) Math.round(5 * scale));
		int infoWidth = hovered && companion != null ? Math.max(150, (int) Math.round(190 * scale)) : 0;
		int width = visualWidth + infoWidth;
		int height = baseHeight;
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(18, 20, 18, 220));
		graphics.fill(new RoundRectangle2D.Float(0, 0, width, height, 14, 14));
		graphics.setColor(companion != null && companion.isLegacy() ? new Color(242, 196, 90, 245)
			: companion != null && companion.getAffectionHearts() >= 60 ? new Color(219, 133, 169, 240)
			: new Color(118, 106, 72, 230));
		graphics.draw(new RoundRectangle2D.Float(1, 1, width - 3, height - 3, 14, 14));
		BufferedImage backdrop = config.showBackdropInOverlay()
			? SpriteAssets.backdrop(state.getEquippedBackdrop().getAssetId()) : null;
		if (backdrop != null)
		{
			int backdropX = (visualWidth - artSize) / 2;
			Shape oldClip = graphics.getClip();
			graphics.clip(new RoundRectangle2D.Float(backdropX, 5, artSize, artSize, 8, 8));
			SpriteAssets.drawNearest(graphics, backdrop, backdropX, 5, artSize, artSize);
			graphics.setColor(new Color(0, 0, 0, 22));
			graphics.fillRect(backdropX, 5, artSize, artSize);
			graphics.setClip(oldClip);
		}

		if (sprite == null) { return new Dimension(width, height); }
		boolean playing = companion != null && System.currentTimeMillis() - companion.getLastToyPlayedAt() < 4_000L;
		double idleSpeed = companion != null && companion.getPersonality() == com.gieligotchi.model.CompanionPersonality.PLAYFUL ? 260d : 360d;
		int motion = egg != null || config.reducedMotion() ? 0 : (int) Math.round(Math.sin(System.currentTimeMillis() / (playing ? 120d : idleSpeed)) * (playing ? 3 : 1) * scale);
		boolean legacyCeremony = companion != null && companion.isLegacy()
			&& System.currentTimeMillis() - companion.getLegacyAt() < 7_000L;
		if (legacyCeremony)
		{
			double pulse = (Math.sin(System.currentTimeMillis() / 100d) + 1d) / 2d;
			for (int ring = 3; ring >= 0; ring--)
			{
				int radius = (int) Math.round((25 + ring * 10 + pulse * 5) * scale);
				graphics.setColor(new Color(242, 196, 90, 14 + (3 - ring) * 12));
				graphics.fillOval(visualWidth / 2 - radius, 5 + artSize / 2 - radius, radius * 2, radius * 2);
			}
		}
		if (egg != null)
		{
			int eggSize = Math.max(1, (int) Math.round(artSize * 0.60d));
			int eggX = (visualWidth - eggSize) / 2;
			int eggY = 5 + (artSize - eggSize) / 2;
			SpriteAssets.drawNearestOpaqueShadowed(graphics, sprite, eggX, eggY, eggSize, eggSize,
				Math.max(1, (int) Math.round(2 * scale)), Math.max(1, (int) Math.round(2 * scale)));
		}
		else { SpriteAssets.drawNearestShadowed(graphics, sprite, (visualWidth - artSize) / 2,
			5 + motion, artSize, artSize, Math.max(1, (int) Math.round(2 * scale)), Math.max(1, (int) Math.round(2 * scale))); }
		Toy toy = companion != null && config.showToyInOverlay() ? state.getEquippedToy() : null;
		if (toy != null)
		{
			int toySize = Math.max(18, (int) Math.round(34 * scale));
			SpriteAssets.drawNearestOpaqueShadowed(graphics, SpriteAssets.toy(toy), visualWidth - toySize - 7,
				5 + artSize - toySize, toySize, toySize, 1, 2);
		}
		if (companion != null) { effects.render(graphics, 4, 5, visualWidth - 8, artSize, scale); }

		double progress;
		String label;
		if (egg != null)
		{
			progress = egg.getProgress();
			label = egg.isReady() ? "READY" : Math.round(progress * 100) + "%";
		}
		else
		{
			int level = LevelCurve.levelFor(companion);
			long floor = LevelCurve.xpForLevel(companion, level);
			long ceiling = level >= 99 ? floor : LevelCurve.xpForLevel(companion, level + 1);
			progress = level >= 99 ? 1d : (companion.getLifetimeXp() - floor) / (double) Math.max(1, ceiling - floor);
			label = "LVL " + level + " · " + Math.round(progress * 100) + "%";
		}
		int barX = 8;
		int barWidth = visualWidth - 16;
		graphics.setColor(new Color(45, 48, 42, 235));
		graphics.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);
		graphics.setColor(egg != null && egg.isReady() ? new Color(244, 183, 54) : new Color(116, 176, 102));
		graphics.fillRoundRect(barX, barY, (int) Math.round(barWidth * progress), barHeight, 6, 6);
		if (revealProgress)
		{
			graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(7, (int) Math.round(8 * scale))));
			FontMetrics metrics = graphics.getFontMetrics();
			int labelX = barX + Math.max(0, (barWidth - metrics.stringWidth(label)) / 2);
			int labelY = barY + (barHeight - metrics.getHeight()) / 2 + metrics.getAscent();
			graphics.setColor(new Color(0, 0, 0, 210));
			graphics.drawString(label, labelX + 1, labelY + 1);
			graphics.setColor(new Color(0xFFF4D2));
			graphics.drawString(label, labelX, labelY);
		}
		if (hovered && companion != null)
		{
			graphics.setColor(new Color(91, 82, 61, 210));
			graphics.drawLine(visualWidth, 7, visualWidth, height - 8);
			int textX = visualWidth + 8;
			int textY = Math.max(17, (int) Math.round(18 * scale));
			int fontSize = Math.max(8, (int) Math.round(9 * scale));
			graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, fontSize));
			graphics.setColor(new Color(0xF2C45A));
			com.gieligotchi.model.PetDefinition pet = catalogue.find(companion.getSpeciesId());
			String speciesName = pet == null ? "Companion" : pet.getName();
			graphics.drawString(companion.getDisplayName(speciesName).toUpperCase(Locale.ENGLISH), textX, textY);
			graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(7, fontSize - 1)));
			graphics.setColor(new Color(0xE2E2E2));
			String personality = companion.getPersonality() == null ? "Undiscovered" : companion.getPersonality().getDisplayName();
			graphics.drawString("♥ " + companion.getAffectionHearts() + " " + companion.getRelationshipStage().getDisplayName()
				+ " · " + personality, textX, textY + fontSize + 3);
			com.gieligotchi.model.CompanionWish wish = companion.getWish();
			if (wish != null)
			{
				String wishLine = "Wish " + wish.getProgress() + "/" + wish.getTarget()
					+ (wish.isComplete() ? " · READY" : "");
				graphics.drawString(wishLine, textX, textY + (fontSize + 3) * 2);
			}
			Toy equipped = state.getEquippedToy();
			graphics.setColor(new Color(0xBEB28C));
			graphics.drawString(equipped == null ? "No toy equipped" : equipped.getDisplayName(),
				textX, textY + (fontSize + 3) * 3);
		}
		return new Dimension(width, height);
	}

	private Dimension renderCeremony(Graphics2D graphics, ProfileState state, double scale)
	{
		int width = Math.max(210, (int) Math.round(270 * scale));
		int height = Math.max(225, (int) Math.round(290 * scale));
		boolean reveal = hatchAnimation.isRevealing();
		EggState activeEgg = state.getActiveEgg();
		HatchReceipt receipt = reveal ? hatchAnimation.getLastReceipt() : null;
		Color borderColour = receipt == null ? new Color(0x756A52) : RarityColours.species(receipt.getSpeciesRarity());
		Color glowColour = receipt == null ? new Color(0xC8B989) : RarityColours.palette(receipt.getPalette());
		Composite oldComposite = graphics.getComposite();
		if (reveal) { graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hatchAnimation.getRevealOpacity())); }
		double pulse = (Math.sin(System.currentTimeMillis() / 115d) + 1d) / 2d;
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(10, 11, 10, 238));
		graphics.fillRoundRect(0, 0, width, height, 22, 22);
		graphics.setStroke(new BasicStroke(3f));
		graphics.setColor(new Color(borderColour.getRed(), borderColour.getGreen(), borderColour.getBlue(), reveal ? 245 : 185));
		graphics.drawRoundRect(2, 2, width - 5, height - 5, 22, 22);

		int centreX = width / 2;
		int centreY = height / 2 + 3;
		for (int ring = 5; ring >= 0; ring--)
		{
			int radius = 40 + ring * 13 + (int) Math.round(pulse * 7);
			int alpha = reveal ? 18 + (5 - ring) * 12 : 6 + (5 - ring) * 5;
			graphics.setColor(new Color(glowColour.getRed(), glowColour.getGreen(), glowColour.getBlue(), Math.min(120, alpha)));
			graphics.fillOval(centreX - radius, centreY - radius, radius * 2, radius * 2);
		}

		BufferedImage image;
		if (reveal)
		{
			CompanionInstance companion = hatchAnimation.getLastCompanion();
			image = companion == null ? null : SpriteAssets.companion(companion);
		}
		else
		{
			image = activeEgg == null ? null : hatchAnimation.eggImage(activeEgg, config.reducedMotion());
		}
		int artSize = reveal ? Math.min(width - 42, 168) : Math.min(width - 42, 172);
		if (image != null)
		{
			if (reveal) { SpriteAssets.drawNearestShadowed(graphics, image, centreX - artSize / 2,
				centreY - artSize / 2 - 6, artSize, artSize, 2, 3); }
			else { SpriteAssets.drawNearestOpaqueShadowed(graphics, image, centreX - artSize / 2,
				centreY - artSize / 2 - 6, artSize, artSize, 2, 3); }
		}

		graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
		graphics.setColor(new Color(0xF2C45A));
		com.gieligotchi.model.PetDefinition pet = receipt == null ? null : catalogue.find(receipt.getSpeciesId());
		String title = reveal && pet != null ? pet.getName().toUpperCase(Locale.ENGLISH) : reveal ? "A NEW COMPANION!" : "HATCHING...";
		FontMetrics titleMetrics = graphics.getFontMetrics();
		graphics.drawString(title, (width - titleMetrics.stringWidth(title)) / 2, 25);
		if (reveal && receipt != null)
		{
			graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
			graphics.setColor(Color.WHITE);
			String detail = receipt.getSpeciesRarity().getDisplayName().toUpperCase(Locale.ENGLISH)
				+ " · " + receipt.getPalette().getDisplayName().toUpperCase(Locale.ENGLISH);
			FontMetrics detailMetrics = graphics.getFontMetrics();
			graphics.drawString(detail, Math.max(7, (width - detailMetrics.stringWidth(detail)) / 2), height - 24);
			String chance = "EXACT HATCH: " + String.format(Locale.UK, "%.6f%%", receipt.getCombinedChance() * 100d);
			graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
			FontMetrics chanceMetrics = graphics.getFontMetrics();
			graphics.drawString(chance, Math.max(7, (width - chanceMetrics.stringWidth(chance)) / 2), height - 10);
		}
		graphics.setComposite(oldComposite);
		return new Dimension(width, height);
	}
}
