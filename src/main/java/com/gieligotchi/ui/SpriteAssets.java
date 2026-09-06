package com.gieligotchi.ui;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.Toy;
import com.gieligotchi.service.LevelCurve;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.util.ImageUtil;

public final class SpriteAssets
{
	private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> MAX_LEVEL_COMPANIONS = new ConcurrentHashMap<>();
	private static final Map<Toy, BufferedImage> TOY_OVERRIDES = new ConcurrentHashMap<>();
	private static final Map<BufferedImage, BufferedImage> SHADOWS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<String, Point> PARTYHAT_ANCHORS = partyhatAnchors();
	private static volatile BufferedImage maxLevelPartyhat;

	private SpriteAssets() {}

	public static BufferedImage egg(EggTier tier)
	{
		String id = tier == EggTier.MEGA_RARE ? "mega-rare" : tier.name().toLowerCase();
		return load("egg:" + id, "/com/gieligotchi/images/eggs/egg-" + id + ".png");
	}

	public static BufferedImage companion(CompanionInstance companion)
	{
		String assetKey = companion.getSpeciesId() + ":" + companion.getPalette().getAssetId();
		BufferedImage sprite = load("pet:" + assetKey,
			"/com/gieligotchi/images/pets/" + companion.getSpeciesId() + "/"
				+ companion.getPalette().getAssetId() + ".png");
		BufferedImage partyhat = maxLevelPartyhat;
		if (sprite == null || partyhat == null || LevelCurve.levelFor(companion) < 99) { return sprite; }
		return MAX_LEVEL_COMPANIONS.computeIfAbsent(assetKey,
			ignored -> decorateWithPartyhat(companion.getSpeciesId(), sprite, partyhat));
	}

	public static void setMaxLevelPartyhat(BufferedImage partyhat)
	{
		maxLevelPartyhat = partyhat;
		MAX_LEVEL_COMPANIONS.clear();
	}

	public static BufferedImage eggFrame(EggTier tier, int frame, String name)
	{
		String id = tier == EggTier.MEGA_RARE ? "mega_rare" : tier.name().toLowerCase();
		return load("egg-frame:" + id + ":" + frame,
			String.format("/com/gieligotchi/images/egg-animation/%s/%02d_%s.png", id, frame, name));
	}

	public static BufferedImage pet(String speciesId, String palette)
	{
		return load("pet:" + speciesId + ":" + palette,
			"/com/gieligotchi/images/pets/" + speciesId + "/" + palette + ".png");
	}

	public static BufferedImage backdrop(String backdropId)
	{
		if (backdropId == null || "classic".equals(backdropId)) { return null; }
		return load("backdrop:" + backdropId,
			"/com/gieligotchi/images/backdrops/" + backdropId + ".png");
	}

	public static BufferedImage toy(Toy toy)
	{
		if (toy == null) { return null; }
		return TOY_OVERRIDES.get(toy);
	}

	public static void setToyImage(Toy toy, BufferedImage image)
	{
		if (toy != null && image != null) { TOY_OVERRIDES.put(toy, image); }
	}

	public static BufferedImage composeRunePile(List<? extends BufferedImage> runes)
	{
		BufferedImage pile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		if (runes == null || runes.isEmpty()) { return pile; }
		Graphics2D graphics = pile.createGraphics();
		int[][] positions = {{18, 31}, {47, 25}, {76, 34}, {7, 64}, {38, 58}, {70, 66}};
		int[] sizes = {45, 48, 44, 49, 50, 47};
		for (int index = 0; index < runes.size() && index < positions.length; index++)
		{
			int size = sizes[index];
			drawNearestOpaque(graphics, runes.get(index), positions[index][0], positions[index][1], size, size - 3);
		}
		graphics.dispose();
		return pile;
	}

	public static void drawNearest(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height)
	{
		if (image == null) { return; }
		Object old = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, x, y, width, height, null);
		if (old != null) { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old); }
	}

	public static void drawNearestShadowed(Graphics2D graphics, BufferedImage image,
		int x, int y, int width, int height, int shadowX, int shadowY)
	{
		if (image == null) { return; }
		drawNearest(graphics, shadowFor(image), x + shadowX, y + shadowY, width, height);
		drawNearest(graphics, image, x, y, width, height);
	}

	public static void drawNearestOpaqueShadowed(Graphics2D graphics, BufferedImage image,
		int x, int y, int width, int height, int shadowX, int shadowY)
	{
		if (image == null) { return; }
		drawNearestOpaque(graphics, shadowFor(image), x + shadowX, y + shadowY, width, height);
		drawNearestOpaque(graphics, image, x, y, width, height);
	}

	public static BufferedImage resizeNearestShadowedOpaque(BufferedImage image, int width, int height)
	{
		if (image == null) { return null; }
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		int contentWidth = Math.max(1, width - 3);
		int contentHeight = Math.max(1, height - 4);
		drawNearestOpaque(graphics, shadowFor(image), 2, 3, contentWidth, contentHeight);
		drawNearestOpaque(graphics, image, 0, 0, contentWidth, contentHeight);
		graphics.dispose();
		return scaled;
	}

	private static BufferedImage shadowFor(BufferedImage image)
	{
		BufferedImage cached = SHADOWS.get(image);
		if (cached != null) { return cached; }
		BufferedImage shadow = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int alpha = image.getRGB(x, y) >>> 24;
				shadow.setRGB(x, y, (Math.min(48, alpha / 4) << 24));
			}
		}
		SHADOWS.put(image, shadow);
		return shadow;
	}

	public static BufferedImage resizeNearest(BufferedImage image, int width, int height)
	{
		if (image == null) { return null; }
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		drawNearest(graphics, image, 0, 0, width, height);
		graphics.dispose();
		return scaled;
	}

	public static BufferedImage resizeNearestOpaque(BufferedImage image, int width, int height)
	{
		if (image == null) { return null; }
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		drawNearestOpaque(graphics, image, 0, 0, width, height);
		graphics.dispose();
		return scaled;
	}

	private static BufferedImage decorateWithPartyhat(String speciesId, BufferedImage sprite, BufferedImage partyhat)
	{
		BufferedImage decorated = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = decorated.createGraphics();
		int insetX = Math.max(2, sprite.getWidth() / 20);
		int insetTop = Math.max(5, sprite.getHeight() / 10);
		int scaledWidth = sprite.getWidth() - insetX * 2;
		int scaledHeight = sprite.getHeight() - insetTop - Math.max(2, sprite.getHeight() / 40);
		drawNearest(graphics, sprite, insetX, insetTop, scaledWidth, scaledHeight);

		int minX = sprite.getWidth(), minY = sprite.getHeight(), maxX = -1;
		for (int y = 0; y < sprite.getHeight(); y++)
		{
			for (int x = 0; x < sprite.getWidth(); x++)
			{
				if ((sprite.getRGB(x, y) >>> 24) != 0)
				{
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
				}
			}
		}
		if (maxX >= minX)
		{
			Point sourceAnchor = PARTYHAT_ANCHORS.get(speciesId);
			if (sourceAnchor == null) { sourceAnchor = new Point((minX + maxX) / 2, minY + 18); }
			int anchorX = insetX + (int) Math.round(sourceAnchor.x * scaledWidth / (double) sprite.getWidth());
			int anchorY = insetTop + (int) Math.round(sourceAnchor.y * scaledHeight / (double) sprite.getHeight());
			int petWidth = Math.max(1, (maxX - minX + 1) * scaledWidth / sprite.getWidth());
			// Base the hat on the creature's visible scale, but keep it narrow enough to
			// read as headwear on wide silhouettes such as Kraken and the spider pets.
			int hatWidth = Math.max(18, Math.min(26, petWidth / 4));
			int hatHeight = Math.max(16, (int) Math.round(hatWidth * 0.86d));
			int hatX = Math.max(0, Math.min(sprite.getWidth() - hatWidth, anchorX - hatWidth / 2));
			int hatY = Math.max(0, Math.min(sprite.getHeight() - hatHeight, anchorY - hatHeight + 5));
			// Cache the companion-only shadow before adding the hat. Callers can still
			// shadow the companion normally without creating a second shadow under the hat.
			shadowFor(decorated);
			drawNearestOpaque(graphics, partyhat, hatX, hatY, hatWidth, hatHeight);
		}
		graphics.dispose();
		return decorated;
	}

	private static Map<String, Point> partyhatAnchors()
	{
		Map<String, Point> anchors = new HashMap<>();
		putAnchors(anchors,
			"abyssal_orphan,65,25;abyssal_protector,64,22;aggy,75,26;baby_chinchompa,45,45;baby_mole,45,70;baron,70,36;beaver,42,74;beef,46,55;bloodhound,50,61;bran,68,30;butch,77,35;callisto_cub,45,70;chompy_chick,50,46;dom,64,52;giant_squirrel,49,75;gull,42,53;hellpuppy,43,60;herbi,37,70;heron,50,30;huberte,67,40;ikkle_hydra,63,25;jal_nib_rek,50,62;kalphite_princess,78,40;lil_creator,70,30;lil_zik,64,54;lilviathan,58,58;little_nightmare,64,52;maggot_marquess,45,28;moxi,65,34;mr_mcgroot,45,50;muphin,50,50;nexling,70,25;nid,64,60;noon,61,53;olmlet,59,48;pet_chaos_elemental,64,30");
		putAnchors(anchors,
			"pet_dagannoth_prime,47,48;pet_dagannoth_rex,55,47;pet_dagannoth_supreme,50,50;pet_dark_core,65,55;pet_general_graardor,66,35;pet_kraken,65,54;pet_kreearra,58,50;pet_kril_tsutsaroth,62,36;pet_penance_queen,66,53;pet_smoke_devil,64,35;pet_snakeling,71,36;pet_zilyana,64,33;phoenix,45,50;prince_black_dragon,45,64;quetzin,45,58;rift_guardian,64,29;rock_golem,64,35;rocky,43,60;scorpias_offspring,77,66;scurry,40,58;skotos,64,40;smol_heredit,65,28;smolcano,64,34;soup,47,69;sraracha,63,52;tangleroot,61,36;tiny_tempor,64,65;tumekens_guardian,65,29;tzrek_jad,45,55;venenatis_spiderling,64,58;vetion_jr,65,30;vorki,42,62;wisp,64,35;yami,64,34;youngllef,48,53");
		return anchors;
	}

	private static void putAnchors(Map<String, Point> anchors, String definitions)
	{
		for (String definition : definitions.split(";"))
		{
			String[] parts = definition.split(",");
			anchors.put(parts[0], new Point(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
		}
	}

	/** Draw only the sprite's visible bounds, aspect-fitted inside the destination. */
	public static void drawNearestOpaque(Graphics2D graphics, BufferedImage image,
		int x, int y, int width, int height)
	{
		if (image == null) { return; }
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for (int sourceY = 0; sourceY < image.getHeight(); sourceY++)
		{
			for (int sourceX = 0; sourceX < image.getWidth(); sourceX++)
			{
				if ((image.getRGB(sourceX, sourceY) >>> 24) != 0)
				{
					minX = Math.min(minX, sourceX); minY = Math.min(minY, sourceY);
					maxX = Math.max(maxX, sourceX); maxY = Math.max(maxY, sourceY);
				}
			}
		}
		if (maxX < minX || maxY < minY) { return; }
		int sourceWidth = maxX - minX + 1, sourceHeight = maxY - minY + 1;
		double factor = Math.min(width / (double) sourceWidth, height / (double) sourceHeight);
		int drawWidth = Math.max(1, (int) Math.round(sourceWidth * factor));
		int drawHeight = Math.max(1, (int) Math.round(sourceHeight * factor));
		drawNearestRegion(graphics, image, minX, minY, sourceWidth, sourceHeight,
			x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight);
	}

	public static void drawNearestRegion(Graphics2D graphics, BufferedImage image,
		int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		int x, int y, int width, int height)
	{
		if (image == null) { return; }
		Object old = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, x, y, x + width, y + height,
			sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, null);
		if (old != null) { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old); }
	}

	private static BufferedImage load(String key, String resource)
	{
		return CACHE.computeIfAbsent(key, ignored -> ImageUtil.loadImageResource(SpriteAssets.class, resource));
	}
}
