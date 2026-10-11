package com.gieligotchi.ui;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.CompanionPersonality;
import com.gieligotchi.model.PetDefinition;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.service.LevelCurve;
import com.gieligotchi.service.PetCatalogue;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.FontManager;

/** A purely visual home for active and stashed companions. */
final class CompanionSanctuaryPanel extends JPanel
{
	private static final int MIN_WORLD_HEIGHT = 510;
	private static final int PET_SIZE = 46;
	private static final int EDGE = 29;
	private final PetCatalogue catalogue;
	private final Map<String, Resident> residents = new HashMap<>();
	private final Random random = new Random(0x51A7C7L);
	private String selectedId;
	private long ticks;
	private float zoom = 1f;
	private float cameraX;
	private float cameraY;
	private int dragX;
	private int dragY;
	private boolean dragging;

	CompanionSanctuaryPanel(PetCatalogue catalogue)
	{
		this.catalogue = catalogue;
		setOpaque(false);
		setWorldHeight(MIN_WORLD_HEIGHT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Select a companion to see how they are doing");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent event)
			{
				if (!SwingUtilities.isLeftMouseButton(event)) { return; }
				dragging = false;
				if (zoomButtonAt(event.getX(), event.getY()) != 0) { return; }
				dragX = event.getX();
				dragY = event.getY();
			}

			@Override public void mouseMoved(MouseEvent event)
			{
				boolean zoomControl = zoomButtonAt(event.getX(), event.getY()) != 0;
				setCursor(Cursor.getPredefinedCursor(zoomControl ? Cursor.HAND_CURSOR
					: zoom > 1f ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR));
			}

			@Override public void mouseDragged(MouseEvent event)
			{
				if (zoom <= 1f || dragY < 68) { return; }
				int deltaX = event.getX() - dragX;
				int deltaY = event.getY() - dragY;
				if (Math.abs(deltaX) + Math.abs(deltaY) > 2) { dragging = true; }
				cameraX -= deltaX / zoom;
				cameraY -= deltaY / zoom;
				dragX = event.getX();
				dragY = event.getY();
				constrainCamera();
				repaint();
			}

			@Override public void mouseClicked(MouseEvent event)
			{
				if (!SwingUtilities.isLeftMouseButton(event) || dragging) { return; }
				int zoomButton = zoomButtonAt(event.getX(), event.getY());
				if (zoomButton != 0)
				{
					setZoom(zoomButton > 0 ? zoom + .25f : zoom - .25f);
					return;
				}
				float[] world = screenToWorld(event.getX(), event.getY());
				Resident hit = residentAt(world[0], world[1]);
				selectedId = hit == null || hit.id.equals(selectedId) ? null : hit.id;
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setState(ProfileState state)
	{
		List<CompanionInstance> owned = new ArrayList<>();
		String activeId = null;
		if (state != null)
		{
			if (state.getActiveCompanion() != null)
			{
				owned.add(state.getActiveCompanion());
				activeId = state.getActiveCompanion().getInstanceId();
			}
			owned.addAll(state.getStashedCompanions());
		}
		Set<String> present = new HashSet<>();
		boolean layoutDirty = false;
		for (CompanionInstance companion : owned)
		{
			if (companion == null || companion.getInstanceId() == null) { continue; }
			present.add(companion.getInstanceId());
			Resident resident = residents.get(companion.getInstanceId());
			if (resident == null)
			{
				resident = new Resident(companion, random.nextFloat(), random.nextFloat());
				residents.put(companion.getInstanceId(), resident);
				layoutDirty = true;
			}
			resident.companion = companion;
			resident.active = companion.getInstanceId().equals(activeId);
			resident.refreshIdentity(catalogue);
		}
		if (residents.keySet().removeIf(id -> !present.contains(id))) { layoutDirty = true; }
		updateWorldHeight(residents.size());
		if (layoutDirty) { arrangeResidents(); }
		if (selectedId != null && !residents.containsKey(selectedId)) { selectedId = null; }
		repaint();
	}

	void tick()
	{
		if (!isShowing() || residents.isEmpty()) { return; }
		ticks++;
		int width = Math.max(120, getWidth());
		int height = Math.max(220, getHeight());
		List<Resident> all = new ArrayList<>(residents.values());
		for (Resident resident : all)
		{
			if (resident.pauseTicks > 0)
			{
				resident.pauseTicks--;
				if (resident.pauseTicks == 0)
				{
					resident.emote = null;
					resident.activity = "Wandering";
				}
			}
			else
			{
				if (resident.environmentTicks == 0 && resident.mingleTicks == 0 && random.nextInt(430) == 0)
				{
					resident.chooseEnvironmentVisit(width, height);
				}
				if (random.nextInt(85) == 0)
				{
					resident.dx = (random.nextFloat() - .5f) * 1.35f;
					resident.dy = (random.nextFloat() - .5f) * .7f;
				}
				if (random.nextInt(300) == 0)
				{
					resident.pauseTicks = 28 + random.nextInt(38);
					resident.activity = "Napping";
					resident.emote = "Z";
				}
				else if (random.nextInt(260) == 0)
				{
					resident.pauseTicks = 20 + random.nextInt(24);
					resident.activity = "Playing in the grass";
					resident.emote = "♪";
				}
				if (resident.environmentTicks > 0 && resident.mingleTicks == 0)
				{
					resident.environmentTicks--;
					resident.steerToward(resident.environmentX, resident.environmentY);
					resident.activity = "Heading to " + resident.environmentName;
					if (Math.hypot(resident.environmentX - resident.x, resident.environmentY - resident.y) < 22)
					{
						resident.pauseTicks = 28 + random.nextInt(22);
						resident.activity = resident.environmentActivity;
						resident.emote = resident.environmentEmote;
						resident.environmentTicks = 0;
					}
				}
				resident.x += resident.dx;
				resident.y += resident.dy;
				if (resident.pauseTicks == 0 && resident.mingleTicks == 0 && resident.environmentTicks == 0)
				{
					resident.activity = nearPond(resident, width, height) ? "Paddling by the pond"
						: Math.abs(resident.dx) > .75f ? "Exploring" : "Wandering";
				}
			}
			if (resident.mingleTicks > 0)
			{
				resident.mingleTicks--;
				Resident partner = residents.get(resident.partnerId);
				if (partner != null)
				{
					resident.steerToward(partner.x, partner.y);
					if (Math.hypot(partner.x - resident.x, partner.y - resident.y) < 70)
					{
						resident.pauseTicks = Math.max(resident.pauseTicks, 18);
						resident.activity = "Chatting with " + partner.name;
						resident.emote = "♥";
					}
				}
				if (resident.mingleTicks == 0) { resident.partnerId = null; resident.emote = null; }
			}
			if (resident.y < 104 || resident.y > height - 34)
			{
				resident.dy = -resident.dy;
				resident.y = clamp(resident.y, 104, height - 34);
			}
			constrainToWalkable(resident, width, height);
			resident.updateFacing();
		}
		separateResidents(all, width, height);
		if (all.size() > 1 && random.nextInt(105) == 0)
		{
			Resident first = all.get(random.nextInt(all.size()));
			Resident second = all.get(random.nextInt(all.size()));
			if (first != second)
			{
				float midX = (first.x + second.x) / 2f;
				float midY = (first.y + second.y) / 2f;
				first.steerToward(midX, midY);
				second.steerToward(midX, midY);
				first.mingleTicks = second.mingleTicks = 55;
				first.environmentTicks = second.environmentTicks = 0;
				first.partnerId = second.id;
				second.partnerId = first.id;
				first.activity = second.activity = "Going to say hello";
				first.emote = second.emote = "!";
			}
		}
		repaint();
	}

	@Override protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		paintFrame(g);
		Graphics2D scene = (Graphics2D) g.create();
		scene.clipRect(0, 68, getWidth(), Math.max(1, getHeight() - 68));
		applyCamera(scene);
		paintWorld(scene);
		if (!residents.isEmpty())
		{
			List<Resident> ordered = new ArrayList<>(residents.values());
			ordered.sort(Comparator.comparingDouble(resident -> resident.y));
			for (Resident resident : ordered) { paintResident(scene, resident); }
		}
		scene.dispose();
		if (residents.isEmpty()) { paintEmpty(g); }
		for (Resident resident : residents.values()) { paintResidentOverlay(g, resident); }
		Resident selected = residents.get(selectedId);
		if (selected != null)
		{
			float[] screen = worldToScreen(selected.x, selected.y);
			paintProfile(g, selected, Math.round(screen[0]), Math.round(screen[1]));
		}
		paintHeader(g);
		g.dispose();
	}

	private void paintFrame(Graphics2D g)
	{
		int width = getWidth();
		int height = getHeight();
		g.setColor(new Color(0x17251F));
		g.fillRoundRect(0, 0, width, height, 12, 12);
		g.setColor(new Color(0x786A49));
		g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
	}

	private void paintWorld(Graphics2D g)
	{
		int width = getWidth();
		int height = getHeight();
		BufferedImage backdrop = SpriteAssets.sanctuaryBackdrop();
		if (backdrop != null)
		{
			Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(backdrop, 7, 68, Math.max(1, width - 14), Math.max(1, height - 76), null);
			if (interpolation != null) { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation); }
		}
		// Pull detail behind the brighter sprites while retaining the illustrated setting.
		g.setColor(new Color(8, 20, 11, 62));
		g.fillRect(7, 68, Math.max(1, width - 14), Math.max(1, height - 76));
		// The pond occupies the upper-left quarter of the painted scene.
		int pondX = 7 + Math.round((width - 14) * .19f);
		int pondY = 68 + Math.round((height - 76) * .30f);
		int ripple = (int) (ticks % 24) / 4;
		g.setColor(new Color(190, 224, 222, 125));
		g.setStroke(new BasicStroke(1.1f));
		g.drawOval(pondX - 18 - ripple, pondY - 5 - ripple / 2,
			36 + ripple * 2, 12 + ripple);
		paintAmbientLife(g, width, height);
	}

	private void paintHeader(Graphics2D g)
	{
		int width = getWidth();
		g.setColor(new Color(14, 24, 20, 220));
		g.fillRoundRect(0, 0, width, 68, 12, 12);
		g.setColor(new Color(0xD6C177));
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
		g.drawString(clip(g, "COMPANION SANCTUARY", Math.max(40, width - 78)), 12, 24);
		g.setColor(new Color(0xC5CDC7));
		g.setFont(FontManager.getRunescapeSmallFont().deriveFont(13f));
		g.drawString("A living home for every companion", 12, 43);
		g.setColor(new Color(0x9AA69F));
		g.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		g.drawString("Select someone to check in", 12, 59);
		paintZoomButton(g, width - 57, 6, "−", zoom > 1f);
		paintZoomButton(g, width - 28, 6, "+", zoom < 1.75f);
	}

	private void paintZoomButton(Graphics2D g, int x, int y, String label, boolean enabled)
	{
		g.setColor(enabled ? new Color(0x34352F) : new Color(0x252723));
		g.fillRoundRect(x, y, 23, 23, 4, 4);
		g.setColor(enabled ? new Color(0xB6A46C) : new Color(0x5F625A));
		g.drawRoundRect(x, y, 23, 23, 4, 4);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		g.drawString(label, x + ("+".equals(label) ? 6 : 7), y + 17);
	}

	private void applyCamera(Graphics2D g)
	{
		ensureCamera();
		AffineTransform transform = new AffineTransform();
		transform.translate(getWidth() / 2d, (68d + getHeight()) / 2d);
		transform.scale(zoom, zoom);
		transform.translate(-cameraX, -cameraY);
		g.transform(transform);
	}

	private void setZoom(float value)
	{
		zoom = clamp(Math.round(value * 4f) / 4f, 1f, 1.75f);
		ensureCamera();
		constrainCamera();
		setCursor(zoom > 1f ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
			: Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		repaint();
	}

	private void ensureCamera()
	{
		if (cameraX == 0f) { cameraX = getWidth() / 2f; }
		if (cameraY == 0f) { cameraY = (68f + getHeight()) / 2f; }
	}

	private void constrainCamera()
	{
		float halfWidth = getWidth() / (2f * zoom);
		float halfHeight = Math.max(1, getHeight() - 68) / (2f * zoom);
		cameraX = clamp(cameraX, halfWidth, getWidth() - halfWidth);
		cameraY = clamp(cameraY, 68 + halfHeight, getHeight() - halfHeight);
	}

	private int zoomButtonAt(int x, int y)
	{
		// The clickable areas deliberately extend beyond the painted controls so they
		// remain easy to use at high UI scale and after dragging the zoomed scene.
		if (y < 2 || y > 35) { return 0; }
		if (x >= getWidth() - 34 && x <= getWidth() - 1 && zoom < 1.75f) { return 1; }
		if (x >= getWidth() - 63 && x <= getWidth() - 30 && zoom > 1f) { return -1; }
		return 0;
	}

	private float[] screenToWorld(float x, float y)
	{
		ensureCamera();
		return new float[] {
			cameraX + (x - getWidth() / 2f) / zoom,
			cameraY + (y - (68f + getHeight()) / 2f) / zoom
		};
	}

	private float[] worldToScreen(float x, float y)
	{
		ensureCamera();
		return new float[] {
			getWidth() / 2f + (x - cameraX) * zoom,
			(68f + getHeight()) / 2f + (y - cameraY) * zoom
		};
	}

	private void paintAmbientLife(Graphics2D g, int width, int height)
	{
		// Small, slow fireflies and a butterfly make the enclosure feel inhabited.
		for (int i = 0; i < 5; i++)
		{
			int x = 20 + Math.floorMod(i * 47 + (int) (ticks / 3), Math.max(1, width - 40));
			int y = 145 + Math.floorMod(i * 83 + (int) (ticks / 7), Math.max(1, height - 215));
			int alpha = 90 + (int) (70 * (1 + Math.sin((ticks + i * 11) / 7d)) / 2d);
			g.setColor(new Color(244, 218, 102, alpha));
			g.fillOval(x, y, 3, 3);
		}
		int butterflyX = 24 + Math.floorMod((int) (ticks / 2), Math.max(1, width - 48));
		int butterflyY = 180 + (int) (Math.sin(ticks / 9d) * 13);
		g.setColor(new Color(0xD6A4CF));
		g.fillRect(butterflyX - 3, butterflyY, 3, 3);
		g.fillRect(butterflyX + 2, butterflyY, 3, 3);
		g.setColor(new Color(0x493A43));
		g.fillRect(butterflyX + 1, butterflyY + 1, 1, 3);
	}

	private void paintEmpty(Graphics2D g)
	{
		int cardWidth = Math.min(190, getWidth() - 20);
		int cardX = (getWidth() - cardWidth) / 2;
		int cardY = 116;
		g.setColor(new Color(18, 23, 20, 224));
		g.fillRoundRect(cardX, cardY, cardWidth, 85, 9, 9);
		g.setColor(new Color(0x6F795F));
		g.drawRoundRect(cardX, cardY, cardWidth, 85, 9, 9);
		g.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		g.setColor(new Color(0xE0E4DF));
		center(g, "The sanctuary is peaceful", cardY + 22);
		center(g, "for now.", cardY + 38);
		g.setColor(new Color(0xBCC6BE));
		center(g, "Stash a companion and they will", cardY + 61);
		center(g, "settle in here.", cardY + 76);
	}

	private void paintResident(Graphics2D g, Resident resident)
	{
		int x = Math.round(resident.x) - PET_SIZE / 2;
		int bob = resident.pauseTicks > 0 ? 0
			: (int) Math.round(Math.sin((ticks + resident.bobOffset) / 10d));
		int y = Math.round(resident.y) - PET_SIZE / 2 + bob;
		if (resident.active)
		{
			g.setColor(new Color(0xF2C45A));
			g.fillOval(x + 7, y + PET_SIZE - 6, PET_SIZE - 14, 5);
		}
		g.setColor(new Color(0, 0, 0, 65));
		g.fillOval(x + 3, y + PET_SIZE - 8, PET_SIZE - 6, 10);
		if (resident.sprite != null)
		{
			if (resident.facingLeft) { g.drawImage(resident.sprite, x + PET_SIZE, y, -PET_SIZE, PET_SIZE, null); }
			else { g.drawImage(resident.sprite, x, y, null); }
		}
		else
		{
			g.setColor(new Color(0xD8D8D8));
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
			g.drawString("?", x + 16, y + 27);
		}
	}

	private void paintResidentOverlay(Graphics2D g, Resident resident)
	{
		if (resident.emote != null && (resident.mingleTicks == 0 || resident.mingleTicks % 16 < 10))
		{
			float[] screen = worldToScreen(resident.x, resident.y);
			int bubbleX = Math.round(screen[0] + PET_SIZE * zoom / 2f) - 15;
			int bubbleY = Math.round(screen[1] - PET_SIZE * zoom / 2f) - 3;
			g.setColor(new Color(26, 29, 27, 220));
			g.fillRoundRect(bubbleX, bubbleY, 18, 17, 8, 8);
			g.setColor(new Color(0xF4D4A1));
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(12f));
			g.drawString(resident.emote, bubbleX + 5, bubbleY + 12);
		}
	}

	private void setWorldHeight(int height)
	{
		setPreferredSize(new Dimension(210, height));
		setMinimumSize(new Dimension(0, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	private void updateWorldHeight(int residentCount)
	{
		int rows = Math.max(1, (residentCount + 1) / 2);
		int height = Math.max(MIN_WORLD_HEIGHT, 125 + rows * 86);
		if (getPreferredSize().height != height)
		{
			setWorldHeight(height);
			revalidate();
		}
	}

	private void arrangeResidents()
	{
		List<Resident> ordered = new ArrayList<>(residents.values());
		ordered.sort(Comparator.comparing(resident -> resident.id));
		int width = Math.max(190, getWidth());
		int height = getPreferredSize().height;
		int columns = width >= 275 ? 3 : 2;
		int rows = Math.max(1, (ordered.size() + columns - 1) / columns);
		float verticalSpace = Math.max(76f, (height - 155f) / Math.max(1, rows));
		for (int index = 0; index < ordered.size(); index++)
		{
			Resident resident = ordered.get(index);
			int row = index / columns;
			int column = index % columns;
			resident.y = 120 + row * verticalSpace + (column % 2 == 0 ? 7 : -7);
			resident.y = clamp(resident.y, 104, height - 36);
			float minimumX = walkableMinX(resident.y, width, height);
			float maximumX = walkableMaxX(resident.y, width, height);
			float horizontalSpace = (maximumX - minimumX) / Math.max(1, columns - 1);
			resident.x = minimumX + column * horizontalSpace + (row % 2 == 0 ? 3 : -3);
			resident.x = clamp(resident.x, minimumX, maximumX);
		}
	}

	private void separateResidents(List<Resident> all, int width, int height)
	{
		final float minimumDistance = PET_SIZE + 7f;
		for (int first = 0; first < all.size(); first++)
		{
			for (int second = first + 1; second < all.size(); second++)
			{
				Resident a = all.get(first);
				Resident b = all.get(second);
				float dx = b.x - a.x;
				float dy = b.y - a.y;
				float distance = (float) Math.max(.1d, Math.hypot(dx, dy));
				if (distance >= minimumDistance) { continue; }
				float push = (minimumDistance - distance) * .16f;
				float nx = dx / distance;
				float ny = dy / distance;
				a.x -= nx * push;
				a.y -= ny * push;
				b.x += nx * push;
				b.y += ny * push;
			}
		}
		for (Resident resident : all)
		{
			resident.y = clamp(resident.y, 104, height - 34);
			constrainToWalkable(resident, width, height);
		}
	}

	private static void constrainToWalkable(Resident resident, int width, int height)
	{
		float minimumX = walkableMinX(resident.y, width, height);
		float maximumX = walkableMaxX(resident.y, width, height);
		if (resident.x < minimumX || resident.x > maximumX) { resident.dx = -resident.dx; }
		resident.x = clamp(resident.x, minimumX, maximumX);
	}

	private static float walkableMinX(float y, int width, int height)
	{
		float progress = clamp((y - 68f) / Math.max(1f, height - 76f), 0f, 1f);
		// Follow the open lawn/path painted into the backdrop. In particular, the pond
		// consumes the upper-left portion of the scene and must never be walkable.
		float fraction = progress < .17f ? .43f
			: progress < .45f ? .51f
			: progress < .72f ? .25f
			: progress < .91f ? .20f : .29f;
		return Math.max(EDGE, width * fraction);
	}

	private static float walkableMaxX(float y, int width, int height)
	{
		float progress = clamp((y - 68f) / Math.max(1f, height - 76f), 0f, 1f);
		float fraction = progress < .17f ? .78f
			: progress < .45f ? .80f
			: progress < .72f ? .78f
			: progress < .91f ? .69f : .71f;
		return Math.min(width - EDGE, width * fraction);
	}

	private static boolean nearPond(Resident resident, int width, int height)
	{
		return resident.x < width * .58f && resident.y > height * .22f && resident.y < height * .42f;
	}

	private void paintProfile(Graphics2D g, Resident resident, int residentX, int residentY)
	{
		int width = Math.min(196, getWidth() - 16);
		int height = 116;
		int spriteRadius = Math.round(PET_SIZE * zoom / 2f);
		int x = Math.max(8, Math.min(getWidth() - width - 8, residentX - width / 2));
		int y = residentY - spriteRadius - height - 8;
		if (y < 68) { y = Math.min(getHeight() - height - 8, residentY + spriteRadius + 7); }
		g.setColor(new Color(23, 26, 25, 238));
		g.fill(new RoundRectangle2D.Float(x, y, width, height, 9, 9));
		g.setColor(resident.active ? new Color(0xE5BF58) : new Color(0x829085));
		g.draw(new RoundRectangle2D.Float(x, y, width, height, 9, 9));
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
		g.setColor(new Color(0xF1D06C));
		g.drawString(clip(g, resident.name.toUpperCase(Locale.ENGLISH), width - 18), x + 9, y + 19);
		g.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		g.setColor(new Color(0xE1E4E1));
		g.drawString(clip(g, "Level " + LevelCurve.levelFor(resident.companion) + " · "
			+ resident.companion.getRelationshipStage().getDisplayName(), width - 18), x + 9, y + 39);
		CompanionPersonality personality = resident.companion.getPersonality();
		g.drawString(clip(g, personality == null ? "Personality still unfolding"
			: "Personality · " + personality.getDisplayName(), width - 18), x + 9, y + 57);
		g.setColor(new Color(0xBEC6C0));
		g.drawString(clip(g, "Activity · " + resident.activity, width - 18), x + 9, y + 74);
		int happiness = Math.max(0, Math.min(100, resident.companion.getAffectionHearts()));
		g.setColor(new Color(0x3A403B));
		g.fillRoundRect(x + 9, y + 84, width - 18, 10, 6, 6);
		g.setColor(new Color(0xD77B8A));
		g.fillRoundRect(x + 9, y + 84, Math.max(2, (width - 18) * happiness / 100), 10, 6, 6);
		g.setColor(new Color(0xC8CECA));
		g.drawString(clip(g, "Happiness · " + happiness + "%"
			+ (resident.active ? " · Active" : " · Relaxing"), width - 18), x + 9, y + 108);
	}

	private Resident residentAt(float x, float y)
	{
		Resident nearest = null;
		double best = PET_SIZE * PET_SIZE;
		for (Resident resident : residents.values())
		{
			double dx = x - resident.x;
			double dy = y - resident.y;
			double distance = dx * dx + dy * dy;
			if (distance < best) { best = distance; nearest = resident; }
		}
		return nearest;
	}

	private void center(Graphics2D g, String text, int y)
	{
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, Math.max(8, (getWidth() - metrics.stringWidth(text)) / 2), y);
	}

	private String clip(Graphics2D g, String value, int width)
	{
		if (g.getFontMetrics().stringWidth(value) <= width) { return value; }
		String result = value;
		while (result.length() > 1 && g.getFontMetrics().stringWidth(result + "…") > width)
		{ result = result.substring(0, result.length() - 1); }
		return result + "…";
	}

	private static float clamp(float value, float minimum, float maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private final class Resident
	{
		private final String id;
		private CompanionInstance companion;
		private String name;
		private BufferedImage sprite;
		private float x;
		private float y;
		private float dx;
		private float dy;
		private int pauseTicks;
		private int mingleTicks;
		private int environmentTicks;
		private final int bobOffset;
		private String partnerId;
		private String emote;
		private float environmentX;
		private float environmentY;
		private String environmentName;
		private String environmentActivity;
		private String environmentEmote;
		private String activity = "Settling in";
		private boolean active;
		private boolean facingLeft;
		private boolean pendingFacingLeft;
		private int facingIntentTicks;

		private Resident(CompanionInstance companion, float xSeed, float ySeed)
		{
			this.id = companion.getInstanceId();
			this.companion = companion;
			this.x = EDGE + xSeed * 160;
			this.y = 112 + ySeed * (MIN_WORLD_HEIGHT - 166);
			this.dx = (random.nextFloat() - .5f) * 1.1f;
			this.dy = (random.nextFloat() - .5f) * .55f;
			this.facingLeft = this.dx < 0f;
			this.pendingFacingLeft = this.facingLeft;
			this.bobOffset = random.nextInt(24);
		}

		private void refreshIdentity(PetCatalogue catalogue)
		{
			PetDefinition pet = catalogue.find(companion.getSpeciesId());
			String species = pet == null ? companion.getSpeciesId() : pet.getName();
			name = companion.getDisplayName(species);
			BufferedImage source = SpriteAssets.pet(companion.getSpeciesId(), companion.getPalette().getAssetId());
			sprite = source == null ? null : SpriteAssets.resizeNearestOutlinedOpaque(source, PET_SIZE, PET_SIZE);
		}

		private void steerToward(float targetX, float targetY)
		{
			float length = (float) Math.max(.01, Math.hypot(targetX - x, targetY - y));
			dx = (targetX - x) / length * .85f;
			dy = (targetY - y) / length * .5f;
		}

		private void updateFacing()
		{
			// Tiny collision corrections and two residents crossing paths used to mirror a
			// sprite every frame. Require a meaningful, sustained change of direction before
			// committing to a new facing direction.
			if (Math.abs(dx) < .30f) { facingIntentTicks = 0; return; }
			boolean intent = dx < 0f;
			if (intent != pendingFacingLeft)
			{
				pendingFacingLeft = intent;
				facingIntentTicks = 1;
				return;
			}
			if (intent == facingLeft) { facingIntentTicks = 0; return; }
			if (++facingIntentTicks >= 10)
			{
				facingLeft = intent;
				facingIntentTicks = 0;
			}
		}

		private void chooseEnvironmentVisit(int width, int height)
		{
			switch (random.nextInt(3))
			{
				case 0:
					environmentX = Math.max(EDGE, width * .55f);
					environmentY = Math.max(112, height * .32f);
					environmentName = "the pond";
					environmentActivity = "Watching the pond life";
					environmentEmote = "~";
					break;
				case 1:
					environmentX = Math.max(EDGE, width * .48f);
					environmentY = Math.max(112, height * .15f);
					environmentName = "the shelter";
					environmentActivity = "Resting in the shelter";
					environmentEmote = "Z";
					break;
				default:
					environmentX = Math.min(width - EDGE, width * .64f);
					environmentY = Math.min(height - 44, height * .84f);
					environmentName = "the play corner";
					environmentActivity = "Playing in the picnic corner";
					environmentEmote = "♪";
					break;
			}
			environmentTicks = 170;
		}
	}
}
