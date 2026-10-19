package com.gieligotchi;

import com.google.inject.Provides;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SecretEggType;
import com.gieligotchi.model.Toy;
import com.gieligotchi.service.ActivityRewardPolicy;
import com.gieligotchi.service.ApexRewardPolicy;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.ItemAcquisitionGuard;
import com.gieligotchi.service.WorldProgressionPolicy;
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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
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
	private static final int NPC_ENGAGEMENT_TIMEOUT_TICKS = 30;
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
	@Inject private ConfigManager configManager;
	@Inject private ChatMessageManager chatMessageManager;
	private NavigationButton navigationButton;
	private String loadedProfileKey;
	private boolean welcomeOpening;
	private boolean skillBaselinesSynchronized;
	private boolean resetSkillBaselinesAfterTransition;
	private long saveReplacementVersion;
	private int lastRegionId = -1;
	private int lastSlayerCount = -1;
	private final Map<Integer, Integer> engagedNpcTicks = new HashMap<>();
	private final Map<String, Long> recentActivityAwards = new HashMap<>();
	private final Map<Integer, Integer> inventorySnapshot = new HashMap<>();
	private final ItemAcquisitionGuard itemAcquisitionGuard = new ItemAcquisitionGuard();
	private boolean inventorySnapshotReady;
	private boolean raidRewardSeen;
	private boolean lastLoadingIntoInstance;
	private boolean tradeOpen;
	private int ignoreTradeGainsUntilTick = -1;
	private long lastPetEggAwardAt;
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
		panel.startUp();
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
		stateService.syncNow(null);
		stateService.backup();
		stateService.unload();
		mouseManager.unregisterMouseListener(this);
		stateService.removeListener(profileListener);
		overlayManager.remove(overlay);
		if (navigationButton != null) { clientToolbar.removeNavigation(navigationButton); }
		panel.shutDown();
		loadedProfileKey = null;
		resetSkillBaselinesAfterTransition = false;
		overlayDragOffset = null;
		overlayDragged = false;
		engagedNpcTicks.clear();
		recentActivityAwards.clear();
		inventorySnapshot.clear();
		inventorySnapshotReady = false;
		itemAcquisitionGuard.clear();
		tradeOpen = false;
		ignoreTradeGainsUntilTick = -1;
		raidRewardSeen = false;
		lastPetEggAwardAt = 0L;
		log.info("Gieligotchi stopped");
	}

	@Subscribe
	public void onConfigSync(net.runelite.client.events.ConfigSync event)
	{
		stateService.syncNow(null);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// LOGGED_IN can arrive before the local player/account hash is ready.
		// Retry cheaply until one profile has actually been selected.
		if (loadedProfileKey == null) { loadCurrentProfile(); }
		if (loadedProfileKey != null && client.getTickCount() % 50 == 0)
		{ stateService.syncNow(configManager.getRSProfileKey()); }
		if (saveReplacementVersion != stateService.getReplacementVersion())
		{
			saveReplacementVersion = stateService.getReplacementVersion();
			skillBaselinesSynchronized = false;
		}
		if (!skillBaselinesSynchronized && stateService.getState() != null
			&& client.getGameState() == GameState.LOGGED_IN)
		{
			Map<Skill, Integer> currentXp = new EnumMap<>(Skill.class);
			for (Skill skill : Skill.values())
			{
				if (skill != Skill.OVERALL) { currentXp.put(skill, client.getSkillExperience(skill)); }
			}
			boolean resetOnly = resetSkillBaselinesAfterTransition || !isProgressionAllowed();
			long reconciledXp = 0;
			if (resetOnly) { stateService.synchronizeSkillBaselines(currentXp, client.getOverallExperience()); }
			else { reconciledXp = stateService.reconcileLoginXp(currentXp, client.getOverallExperience()); }
			skillBaselinesSynchronized = true;
			resetSkillBaselinesAfterTransition = false;
			if (reconciledXp > 0)
			{
				queueSystemMessage("Welcome back! You gained " + format(reconciledXp)
					+ " Bonding XP from progress made while you were away.");
				notifyIfReady();
			}
		}
		if (!isProgressionAllowed())
		{
			lastRegionId = -1;
			lastSlayerCount = -1;
			engagedNpcTicks.clear();
			recentActivityAwards.clear();
			return;
		}
		int currentTick = client.getTickCount();
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getInteracting() instanceof NPC)
		{
			NPC target = (NPC) client.getLocalPlayer().getInteracting();
			engagedNpcTicks.put(target.getIndex(), currentTick);
		}
		engagedNpcTicks.entrySet().removeIf(entry -> currentTick - entry.getValue() > NPC_ENGAGEMENT_TIMEOUT_TICKS);
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
		notifySecretDiscovery();
		notifyPetReward();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			boolean inInstancedRegion = client.isInInstancedRegion();
			if (inInstancedRegion != lastLoadingIntoInstance)
			{
				lastLoadingIntoInstance = inInstancedRegion;
				raidRewardSeen = false;
			}
		}
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.CONNECTION_LOST)
		{
			skillBaselinesSynchronized = false;
			resetSkillBaselinesAfterTransition = true;
			lastRegionId = -1;
			lastSlayerCount = -1;
			engagedNpcTicks.clear();
			recentActivityAwards.clear();
			inventorySnapshot.clear();
			inventorySnapshotReady = false;
			itemAcquisitionGuard.clear();
			raidRewardSeen = false;
		}
		else if (event.getGameState() == GameState.LOGGED_IN) { loadCurrentProfile(); }
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			stateService.syncNow(null);
			stateService.backup();
			stateService.unload();
			loadedProfileKey = null;
			welcomeOpening = false;
			skillBaselinesSynchronized = false;
			resetSkillBaselinesAfterTransition = false;
			lastRegionId = -1;
			lastSlayerCount = -1;
			engagedNpcTicks.clear();
			recentActivityAwards.clear();
			inventorySnapshot.clear();
			inventorySnapshotReady = false;
			itemAcquisitionGuard.clear();
			tradeOpen = false;
			ignoreTradeGainsUntilTick = -1;
			raidRewardSeen = false;
			lastLoadingIntoInstance = false;
			hatchAnimation.reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event == null || !isProgressionAllowed()) { return; }
		if (event.getGroupId() == InterfaceID.TRADEMAIN || event.getGroupId() == InterfaceID.TRADECONFIRM)
		{
			tradeOpen = true;
			return;
		}
		if (raidRewardSeen) { return; }
		String rewardSource;
		InventoryID inventoryId;
		switch (event.getGroupId())
		{
			case InterfaceID.RAIDS_REWARDS:
				rewardSource = ApexRewardPolicy.CHAMBERS;
				inventoryId = InventoryID.CHAMBERS_OF_XERIC_CHEST;
				break;
			case InterfaceID.TOB_CHESTS:
				rewardSource = ApexRewardPolicy.THEATRE;
				inventoryId = InventoryID.THEATRE_OF_BLOOD_CHEST;
				break;
			case InterfaceID.TOA_CHESTS:
				rewardSource = ApexRewardPolicy.TOMBS;
				inventoryId = InventoryID.TOA_REWARD_CHEST;
				break;
			default:
				return;
		}
		raidRewardSeen = true;
		ItemContainer rewards = client.getItemContainer(inventoryId);
		if (rewards == null) { return; }
		for (Item item : rewards.getItems())
		{
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
			{
				stateService.recordApexReward(rewardSource, item.getId(), item.getQuantity());
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event == null || event.getGroupId() != InterfaceID.TRADEMAIN
			&& event.getGroupId() != InterfaceID.TRADECONFIRM) { return; }
		tradeOpen = false;
		ignoreTradeGainsUntilTick = client.getTickCount() + 3;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!skillBaselinesSynchronized || !isProgressionAllowed()) { return; }
		net.runelite.api.coords.WorldPoint location = client.getLocalPlayer() == null ? null
			: client.getLocalPlayer().getWorldLocation();
		long award = stateService.observeSkill(event.getSkill(), event.getXp(), client.getOverallExperience(),
			location == null ? -1 : location.getX(), location == null ? -1 : location.getY(),
			location == null ? -1 : location.getPlane());
		if (award > 0) { notifyIfReady(); }
		notifySecretDiscovery();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event == null || event.getItemContainer() == null
			|| event.getContainerId() != InventoryID.INVENTORY.getId()) { return; }
		Map<Integer, Integer> current = new HashMap<>();
		for (Item item : event.getItemContainer().getItems())
		{
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
			{ current.merge(item.getId(), item.getQuantity(), Integer::sum); }
		}
		if (!isProgressionAllowed())
		{
			inventorySnapshot.clear();
			inventorySnapshot.putAll(current);
			inventorySnapshotReady = true;
			return;
		}
		if (!inventorySnapshotReady)
		{
			inventorySnapshot.putAll(current);
			inventorySnapshotReady = true;
			return;
		}
		if (tradeOpen || client.getTickCount() <= ignoreTradeGainsUntilTick)
		{
			inventorySnapshot.clear();
			inventorySnapshot.putAll(current);
			return;
		}
		// Moving an existing item out of the bank is not a fresh acquisition.
		net.runelite.api.widgets.Widget bankItems = client.getWidget(12, 13);
		if (bankItems != null && !bankItems.isHidden())
		{
			inventorySnapshot.clear();
			inventorySnapshot.putAll(current);
			return;
		}
		net.runelite.api.coords.WorldPoint location = client.getLocalPlayer() == null ? null
			: client.getLocalPlayer().getWorldLocation();
		for (Map.Entry<Integer, Integer> entry : current.entrySet())
		{
			int gained = entry.getValue() - inventorySnapshot.getOrDefault(entry.getKey(), 0);
			gained = itemAcquisitionGuard.freshQuantity(entry.getKey(), gained, client.getTickCount());
			if (gained > 0)
			{
				stateService.recordHeartfeltItem(entry.getKey(), gained,
					location == null ? -1 : location.getX(), location == null ? -1 : location.getY(),
					location == null ? -1 : location.getPlane());
			}
		}
		inventorySnapshot.clear();
		inventorySnapshot.putAll(current);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event == null || client.getLocalPlayer() == null || !isProgressionAllowed()) { return; }
		String option = Text.removeTags(event.getMenuOption());
		if ("Drop".equalsIgnoreCase(option) && event.getItemId() >= 0)
		{
			net.runelite.api.coords.WorldPoint location = client.getLocalPlayer().getWorldLocation();
			int quantity = event.getWidget() == null ? 1 : Math.max(1, event.getWidget().getItemQuantity());
			itemAcquisitionGuard.recordDrop(event.getItemId(), quantity, location.getX(), location.getY(),
				location.getPlane(), client.getTickCount());
			return;
		}
		if ("Remove".equalsIgnoreCase(option) && event.getItemId() >= 0
			&& event.getWidgetId() >>> 16 == InterfaceID.WORNITEMS)
		{
			int quantity = event.getWidget() == null ? 1 : Math.max(1, event.getWidget().getItemQuantity());
			itemAcquisitionGuard.recordTransfer(event.getItemId(), quantity, client.getTickCount());
			return;
		}
		if ("Take".equalsIgnoreCase(option) || isGroundItemInteraction(event.getMenuAction()))
		{
			net.runelite.api.coords.WorldPoint location = net.runelite.api.coords.WorldPoint.fromScene(client,
				event.getParam0(), event.getParam1(), client.getPlane());
			if (location != null)
			{
				itemAcquisitionGuard.recordTake(event.getId(), location.getX(), location.getY(),
					location.getPlane(), client.getTickCount());
			}
		}
	}

	private static boolean isGroundItemInteraction(MenuAction action)
	{
		return action == MenuAction.GROUND_ITEM_FIRST_OPTION
			|| action == MenuAction.GROUND_ITEM_SECOND_OPTION
			|| action == MenuAction.GROUND_ITEM_THIRD_OPTION
			|| action == MenuAction.GROUND_ITEM_FOURTH_OPTION
			|| action == MenuAction.GROUND_ITEM_FIFTH_OPTION
			|| action == MenuAction.ITEM_USE_ON_GROUND_ITEM
			|| action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!isProgressionAllowed()) { return; }
		if (event.getSource() == client.getLocalPlayer() && event.getTarget() instanceof NPC)
		{
			engagedNpcTicks.put(((NPC) event.getTarget()).getIndex(), client.getTickCount());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isProgressionAllowed()) { return; }
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
		if (!isProgressionAllowed() || !(event.getActor() instanceof NPC)) { return; }
		NPC npc = (NPC) event.getActor();
		Integer engagedAt = engagedNpcTicks.remove(npc.getIndex());
		if (engagedAt == null || client.getTickCount() - engagedAt > NPC_ENGAGEMENT_TIMEOUT_TICKS) { return; }
		int nearbyPlayers = nearbyPlayersAt(npc, 20);
		long award = stateService.awardNpcKill(npc.getId(), npc.getName(), npc.getCombatLevel(), nearbyPlayers);
		if (nearbyPlayers > 3)
		{
			log.debug("Mass scaling {} kill across {} nearby players to {} Bonding XP",
				npc.getName(), nearbyPlayers, award);
		}
		if (award > 0) { notifyIfReady(); }
		notifySecretDiscovery();
	}

	private int nearbyPlayersAt(NPC npc, int radius)
	{
		if (npc == null || npc.getWorldLocation() == null) { return 1; }
		Player localPlayer = client.getLocalPlayer();
		int count = localPlayer == null ? 0 : 1;
		for (Player player : client.getPlayers())
		{
			if (player != null && player != localPlayer && player.getWorldLocation() != null
				&& player.getWorldLocation().getPlane() == npc.getWorldLocation().getPlane()
				&& player.getWorldLocation().distanceTo(npc.getWorldLocation()) <= radius)
			{
				count++;
			}
		}
		return Math.max(1, count);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event == null || (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM) || !isProgressionAllowed()) { return; }
		String message = Text.removeTags(event.getMessage());
		if (isPetAcquisitionMessage(message))
		{
			long now = System.currentTimeMillis();
			if (now - lastPetEggAwardAt > 5_000L)
			{
				lastPetEggAwardAt = now;
				stateService.grantPetRewardEgg();
				notifyPetReward();
			}
		}
		stateService.recordSkillingActivity(com.gieligotchi.model.SkillingActivity.match(message));
		ActivityRewardPolicy.Reward reward = ActivityRewardPolicy.match(message);
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
		notifySecretDiscovery();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GieligotchiConfig.GROUP.equals(event.getGroup()))
		{
			if ("resetAccount".equals(event.getKey()) && config.resetAccount())
			{
				SwingUtilities.invokeLater(this::confirmAccountReset);
				return;
			}
			overlay.syncMovement();
			if (!config.unlockOverlay())
			{
				overlayDragOffset = null;
				overlayDragged = false;
			}
			if ("textScale".equals(event.getKey())) { panel.refresh(); }
		}
	}

	private void confirmAccountReset()
	{
		int choice = JOptionPane.showConfirmDialog(panel,
			"This will permanently erase every Gieligotchi egg, companion, collection entry,\n"
				+ "purchase, memory and point for the current account.\n\nStart over from a new starter egg?",
			"Reset Gieligotchi account", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		welcomeOpening = false;
		if (choice == JOptionPane.YES_OPTION && stateService.resetProfile())
		{
			hatchAnimation.reset();
			skillBaselinesSynchronized = false;
			resetSkillBaselinesAfterTransition = false;
			lastRegionId = -1;
			lastSlayerCount = -1;
			queueSystemMessage("Your Gieligotchi account has been reset. A new starter egg is waiting for you.");
		}
		configManager.setConfiguration(GieligotchiConfig.GROUP, "resetAccount", false);
	}

	private void queueSystemMessage(String message)
	{
		String plain = "[Gieligotchi] " + message;
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.value(plain)
			.runeLiteFormattedMessage("<col=ffcc66>[Gieligotchi]</col> " + message)
			.build());
	}

	private static String format(long amount)
	{
		return String.format(Locale.UK, "%,d", amount);
	}

	private boolean isProgressionAllowed()
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& WorldProgressionPolicy.allows(client.getWorldType());
	}

	private void loadCurrentProfile()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { return; }
		long accountHash = client.getAccountHash();
		// Wait for the stable character namespace before loading or syncing any save.
		if (accountHash == -1L || accountHash == 0L || configManager.getRSProfileKey() == null) { return; }
		if (configManager.getRSProfiles().stream().noneMatch(profile -> profile.getAccountHash() == accountHash
			&& profile.getKey().equals(configManager.getRSProfileKey()))) { return; }
		String name = client.getLocalPlayer().getName();
		String key = accountHash != -1L && accountHash != 0L
			? "account-" + Long.toUnsignedString(accountHash)
			: "character-" + (name == null ? "unknown" : name.trim().toLowerCase(Locale.ENGLISH));
		if (!key.equals(loadedProfileKey))
		{
			loadedProfileKey = key;
			skillBaselinesSynchronized = false;
			stateService.load(key, configManager.getRSProfileKey());
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

	private void notifySecretDiscovery()
	{
		SecretEggType type = stateService.consumeSecretDiscovery();
		if (type == null) { return; }
		String message = "Your companion discovered a secret " + type.getDisplayName()
			+ " egg! It is waiting in the Secret Egg Tray.";
		queueSystemMessage(message);
		notifier.notify(message);
	}

	private void notifyPetReward()
	{
		com.gieligotchi.model.EggTier tier = stateService.consumePetReward();
		if (tier == null) { return; }
		String notice = "Your new Old School pet brought a " + tier.getDisplayName()
			+ " egg! It has been added to the Stash Tray.";
		queueSystemMessage(notice);
		notifier.notify(notice);
	}

	private static boolean isPetAcquisitionMessage(String message)
	{
		if (message == null) { return false; }
		String plain = message.toLowerCase(Locale.ENGLISH);
		return plain.contains("funny feeling like you're being followed")
			|| plain.contains("funny feeling you would have been followed")
			|| plain.contains("something weird sneaking into your backpack");
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
			if (hatchAnimation.isCeremonyActive())
			{
				event.consume();
				return event;
			}
			ProfileState state = stateService.getState();
			EggState egg = state == null ? null : state.getActiveEgg();
			if (egg != null && egg.isReady()) { hatchAnimation.beginHatch(); }
			else if (egg != null && !config.unlockOverlay()) { SwingUtilities.invokeLater(panel::inspectActive); }
			else { return event; }
			event.consume();
			return event;
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
		event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (overlayDragOffset == null) { return event; }
		overlayDragOffset = null;
		if (overlayDragged) { overlayManager.saveOverlay(overlay); }
		overlayDragged = false;
		event.consume();
		return event;
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
		event.consume();
		return event;
	}
	@Override public MouseEvent mouseMoved(MouseEvent event) { updateOverlayHover(event); return event; }

	private void updateOverlayHover(MouseEvent event)
	{
		Rectangle bounds = overlay.getBounds();
		overlay.setHovered(bounds != null && bounds.contains(event.getPoint()));
	}
}
