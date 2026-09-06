package com.gieligotchi;

import com.google.inject.Provides;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.Toy;
import com.gieligotchi.service.ActivityRewardPolicy;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.ui.GieligotchiOverlay;
import com.gieligotchi.ui.GieligotchiPanel;
import com.gieligotchi.ui.HatchAnimationController;
import com.gieligotchi.ui.SpriteAssets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Gieligotchi",
	description = "Raise, hatch and collect cosmetic companions through ordinary RuneScape play",
	tags = {"tamagotchi", "pet", "companion", "collection", "cosmetic"}
)
public class GieligotchiPlugin extends Plugin implements MouseListener
{
	@Inject private Client client;
	@Inject private GieligotchiConfig config;
	@Inject private GieligotchiStateService stateService;
	@Inject private GieligotchiPanel panel;
	@Inject private GieligotchiOverlay overlay;
	@Inject private HatchAnimationController hatchAnimation;
	@Inject private OverlayManager overlayManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private MouseManager mouseManager;
	@Inject private Notifier notifier;
	@Inject private ItemManager itemManager;
	private NavigationButton navigationButton;
	private String loadedProfileKey;
	private boolean welcomeOpening;
	private boolean skillBaselinesSynchronized;
	private int lastRegionId = -1;
	private int lastSlayerCount = -1;
	private final Map<Integer, Integer> engagedNpcTicks = new HashMap<>();
	private final Map<String, Long> recentActivityAwards = new HashMap<>();
	private final Runnable profileListener = this::onProfileChanged;
	private Point overlayDragOffset;
	private boolean overlayDragged;

	@Provides
	GieligotchiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GieligotchiConfig.class);
	}

	@Override
	protected void startUp()
	{
		AsyncBufferedImage partyhat = itemManager.getImage(ItemID.RED_PARTYHAT);
		partyhat.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			SpriteAssets.setMaxLevelPartyhat(partyhat);
			panel.refresh();
		}));
		loadToyAssets();
		BufferedImage icon = SpriteAssets.egg(com.gieligotchi.model.EggTier.COMMON);
		navigationButton = NavigationButton.builder()
			.tooltip("Gieligotchi")
			.icon(SpriteAssets.resizeNearestShadowedOpaque(icon, 32, 32))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlay.syncMovement();
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(this);
		stateService.addListener(profileListener);
		loadCurrentProfile();
		log.info("Gieligotchi started");
	}

	private void loadToyAssets()
	{
		loadToyAsset(Toy.PLAY_BALL, ItemID.GNOMEBALL);
		loadToyAsset(Toy.DRAGON_PLUSH, ItemID.JAD_PLUSH);
		loadToyAsset(Toy.FEATHER_WAND, ItemID.HAND_FAN);
		int[] runeIds = {
			ItemID.AIR_RUNE, ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.FIRE_RUNE,
			ItemID.NATURE_RUNE, ItemID.CHAOS_RUNE
		};
		List<AsyncBufferedImage> runes = new ArrayList<>(runeIds.length);
		AtomicInteger waiting = new AtomicInteger(runeIds.length);
		for (int runeId : runeIds)
		{
			AsyncBufferedImage rune = itemManager.getImage(runeId);
			runes.add(rune);
			rune.onLoaded(() ->
			{
				if (waiting.decrementAndGet() == 0)
				{
					SwingUtilities.invokeLater(() ->
					{
						SpriteAssets.setToyImage(Toy.RUNE_BLOCKS, SpriteAssets.composeRunePile(runes));
						panel.refresh();
					});
				}
			});
		}
	}

	private void loadToyAsset(Toy toy, int itemId)
	{
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			SpriteAssets.setToyImage(toy, image);
			panel.refresh();
		}));
	}

	@Override
	protected void shutDown()
	{
		stateService.backup();
		mouseManager.unregisterMouseListener(this);
		stateService.removeListener(profileListener);
		overlayManager.remove(overlay);
		if (navigationButton != null) { clientToolbar.removeNavigation(navigationButton); }
		panel.shutDown();
		loadedProfileKey = null;
		overlayDragOffset = null;
		overlayDragged = false;
		engagedNpcTicks.clear();
		recentActivityAwards.clear();
		log.info("Gieligotchi stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// LOGGED_IN can arrive before the local player/account hash is ready.
		// Retry cheaply until one profile has actually been selected.
		if (loadedProfileKey == null) { loadCurrentProfile(); }
		if (!skillBaselinesSynchronized && stateService.getState() != null)
		{
			Map<Skill, Integer> currentXp = new EnumMap<>(Skill.class);
			for (Skill skill : Skill.values())
			{
				if (skill != Skill.OVERALL) { currentXp.put(skill, client.getSkillExperience(skill)); }
			}
			stateService.synchronizeSkillBaselines(currentXp);
			skillBaselinesSynchronized = true;
		}
		int currentTick = client.getTickCount();
		engagedNpcTicks.entrySet().removeIf(entry -> currentTick - entry.getValue() > 12);
		recentActivityAwards.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 10_000L);
		if (client.getLocalPlayer() != null)
		{
			int regionId = client.getLocalPlayer().getWorldLocation().getRegionID();
			if (lastRegionId > 0 && regionId != lastRegionId) { stateService.recordRegionVisit(regionId); }
			lastRegionId = regionId;
		}
		int slayerCount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (lastSlayerCount > 0 && slayerCount == 0)
		{
			if (stateService.awardSlayerTask() > 0) { notifyIfReady(); }
		}
		lastSlayerCount = slayerCount;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN) { loadCurrentProfile(); }
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			stateService.backup();
			loadedProfileKey = null;
			welcomeOpening = false;
			skillBaselinesSynchronized = false;
			lastRegionId = -1;
			lastSlayerCount = -1;
			engagedNpcTicks.clear();
			recentActivityAwards.clear();
			hatchAnimation.reset();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		long award = stateService.observeSkill(event.getSkill(), event.getXp());
		if (award > 0) { notifyIfReady(); }
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() == client.getLocalPlayer() && event.getTarget() instanceof NPC)
		{
			engagedNpcTicks.put(((NPC) event.getTarget()).getIndex(), client.getTickCount());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		if (actor instanceof NPC && hitsplat != null && hitsplat.isMine())
		{
			engagedNpcTicks.put(((NPC) actor).getIndex(), client.getTickCount());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!(event.getActor() instanceof NPC)) { return; }
		NPC npc = (NPC) event.getActor();
		Integer engagedAt = engagedNpcTicks.remove(npc.getIndex());
		if (engagedAt == null || client.getTickCount() - engagedAt > 12) { return; }
		long award = stateService.awardNpcKill(npc.getName(), npc.getCombatLevel());
		if (award > 0) { notifyIfReady(); }
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event == null || (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM)) { return; }
		ActivityRewardPolicy.Reward reward = ActivityRewardPolicy.match(Text.removeTags(event.getMessage()));
		if (reward == null) { return; }
		long now = System.currentTimeMillis();
		Long previous = recentActivityAwards.get(reward.getId());
		if (previous != null && now - previous < 2_500L) { return; }
		recentActivityAwards.put(reward.getId(), now);
		long award = stateService.awardActivity(reward.getId(), reward.getLabel(), reward.getAmount());
		if (award > 0)
		{
			log.debug("{} awarded {} Bonding XP", reward.getLabel(), award);
			notifyIfReady();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GieligotchiConfig.GROUP.equals(event.getGroup()))
		{
			overlay.syncMovement();
			if (!config.unlockOverlay())
			{
				overlayDragOffset = null;
				overlayDragged = false;
			}
		}
	}

	private void loadCurrentProfile()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { return; }
		long accountHash = client.getAccountHash();
		String name = client.getLocalPlayer().getName();
		String key = accountHash != -1L && accountHash != 0L
			? "account-" + Long.toUnsignedString(accountHash)
			: "character-" + (name == null ? "unknown" : name.trim().toLowerCase(Locale.ENGLISH));
		if (!key.equals(loadedProfileKey))
		{
			loadedProfileKey = key;
			skillBaselinesSynchronized = false;
			stateService.load(key);
		}
	}

	private void notifyIfReady()
	{
		ProfileState state = stateService.getState();
		EggState egg = state == null ? null : state.getActiveEgg();
		if (egg != null && egg.isReady() && !egg.isReadyNotificationSent())
		{
			notifier.notify("Your Gieligotchi egg is ready to hatch!");
			stateService.markReadyNotificationSent();
		}
	}

	private void onProfileChanged()
	{
		ProfileState state = stateService.getState();
		if (state == null || state.isWelcomeSeen() || welcomeOpening) { return; }
		welcomeOpening = true;
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navigationButton);
			panel.showFirstRunWelcome();
		});
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		if (event.getButton() != MouseEvent.BUTTON1 || !config.showOverlay()) { return event; }
		Rectangle bounds = overlay.getBounds();
		if (bounds != null && bounds.contains(event.getPoint()))
		{
			if (hatchAnimation.isCeremonyActive()) { return null; }
			ProfileState state = stateService.getState();
			EggState egg = state == null ? null : state.getActiveEgg();
			if (egg != null && egg.isReady()) { hatchAnimation.beginHatch(); }
			else if (egg != null && !config.unlockOverlay()) { SwingUtilities.invokeLater(panel::inspectActive); }
			else { return event; }
			return null;
		}
		return event;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() != MouseEvent.BUTTON1 || !config.showOverlay() || !config.unlockOverlay()) { return event; }
		Rectangle bounds = overlay.getBounds();
		if (bounds == null || !bounds.contains(event.getPoint())) { return event; }
		overlayDragOffset = new Point(event.getX() - bounds.x, event.getY() - bounds.y);
		overlayDragged = false;
		return null;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (overlayDragOffset == null) { return event; }
		overlayDragOffset = null;
		if (overlayDragged) { overlayManager.saveOverlay(overlay); }
		overlayDragged = false;
		return null;
	}
	@Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
	@Override public MouseEvent mouseExited(MouseEvent event) { overlay.setHovered(false); return event; }
	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (overlayDragOffset == null || !config.unlockOverlay())
		{
			updateOverlayHover(event);
			return event;
		}
		Rectangle bounds = overlay.getBounds();
		if (bounds == null) { return event; }
		int maxX = Math.max(0, client.getCanvas().getWidth() - bounds.width);
		int maxY = Math.max(0, client.getCanvas().getHeight() - bounds.height);
		int x = Math.max(0, Math.min(maxX, event.getX() - overlayDragOffset.x));
		int y = Math.max(0, Math.min(maxY, event.getY() - overlayDragOffset.y));
		Point location = new Point(x, y);
		overlay.setPreferredPosition(null);
		overlay.setPreferredLocation(location);
		overlay.setBounds(new Rectangle(location, bounds.getSize()));
		overlayDragged = true;
		overlay.setHovered(true);
		return null;
	}
	@Override public MouseEvent mouseMoved(MouseEvent event) { updateOverlayHover(event); return event; }

	private void updateOverlayHover(MouseEvent event)
	{
		Rectangle bounds = overlay.getBounds();
		overlay.setHovered(bounds != null && bounds.contains(event.getPoint()));
	}
}
