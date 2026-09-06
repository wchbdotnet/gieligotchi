package com.gieligotchi.ui;

import com.gieligotchi.GieligotchiConfig;
import com.gieligotchi.model.Backdrop;
import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.CompanionWish;
import com.gieligotchi.model.MemoryEntry;
import com.gieligotchi.model.RelationshipStage;
import com.gieligotchi.model.Discovery;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.PetDefinition;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.SpeciesRarity;
import com.gieligotchi.model.Toy;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.CompanionValue;
import com.gieligotchi.service.LevelCurve;
import com.gieligotchi.service.PetCatalogue;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@Singleton
public class GieligotchiPanel extends PluginPanel
{
	private static final Color SHELL = new Color(0xBC965B);
	private static final Color SHELL_DARK = new Color(0x57442E);
	private static final Color LCD = new Color(0xA9B779);
	private static final Color LCD_DARK = new Color(0x27301F);
	private static final int PAGE_GUTTER = 12;
	private static final int HATCHES_PER_PAGE = 10;
	private static final int STASHED_COMPANIONS_PER_PAGE = 5;
	private static final int COLLECTION_PER_PAGE = 15;
	private static final int BACKDROPS_PER_PAGE = 5;
	private static final long TOY_PLAY_COOLDOWN_MILLIS = 5_000L;
	private static final String DISCORD_INVITE = "https://discord.gg/dP9WN62QQE";
	private static final String HOME = "home";
	private final GieligotchiStateService stateService;
	private final PetCatalogue catalogue;
	private final GieligotchiConfig config;
	private final HatchAnimationController hatchAnimation;
	private final CompanionEffectController effects;
	private final CardLayout pages = new CardLayout();
	private final JPanel pageHost = new JPanel(pages);
	private final JPanel home = new WidthTrackingPanel();
	private final JPanel collection = new WidthTrackingPanel();
	private final JPanel shop = new WidthTrackingPanel();
	private final JPanel guide = new WidthTrackingPanel();
	private final JPanel rules = new WidthTrackingPanel();
	private final JPanel rarities = new WidthTrackingPanel();
	private final BootPanel boot = new BootPanel();
	private final JLabel currency = new JLabel();
	private final TamagotchiDisplay display = new TamagotchiDisplay();
	private final Runnable stateListener = this::refresh;
	private final Timer animationTimer;
	private final Map<String, JButton> navigationTabs = new HashMap<>();
	private boolean introPlayed;
	private boolean collectionHistoryVisible;
	private int hatchHistoryPage;
	private int stashCompanionPage;
	private int collectionPage;
	private int backdropShopPage;
	private int guidePage;
	private int rulesPage;
	private int careMemoryPage;
	private boolean shopShowsToys = true;
	private boolean activeCompanionCardMinimized;
	private String raritySection = "eggs";
	private String activePage = HOME;
	private String collectionPetId;
	private Palette collectionPreviewPalette;
	private String journeyMode;
	private int handheldHoverButton = -1;
	private int handheldPressedButton = -1;
	private final Random gameRandom = new Random();
	private int gameCard;
	private int gameRound;
	private int gameScore;
	private String gameMessage = "Press Start for three quick rounds";

	@Inject
	public GieligotchiPanel(GieligotchiStateService stateService, PetCatalogue catalogue,
		GieligotchiConfig config, HatchAnimationController hatchAnimation, CompanionEffectController effects)
	{
		super(false);
		this.stateService = stateService;
		this.catalogue = catalogue;
		this.config = config;
		this.hatchAnimation = hatchAnimation;
		this.effects = effects;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(buildHeader(), BorderLayout.NORTH);
		pageHost.setOpaque(false);
		pageHost.add(boot, "boot");
		pageHost.add(wrap(home), HOME);
		pageHost.add(wrap(collection), "collection");
		pageHost.add(wrap(shop), "shop");
		pageHost.add(wrap(guide), "guide");
		pageHost.add(wrap(rules), "rules");
		pageHost.add(wrap(rarities), "rarities");
		add(pageHost, BorderLayout.CENTER);
		add(buildTabs(), BorderLayout.SOUTH);
		showPage(HOME);
		display.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		display.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mousePressed(java.awt.event.MouseEvent event)
			{
				handheldPressedButton = display.buttonAt(event.getX(), event.getY());
				display.repaint();
			}

			@Override public void mouseReleased(java.awt.event.MouseEvent event)
			{
				handheldPressedButton = -1;
				display.repaint();
			}

			@Override public void mouseExited(java.awt.event.MouseEvent event)
			{
				handheldHoverButton = -1;
				handheldPressedButton = -1;
				display.setToolTipText(null);
				display.repaint();
			}

			@Override public void mouseClicked(java.awt.event.MouseEvent event)
			{
				if (!SwingUtilities.isLeftMouseButton(event)) { return; }
				ProfileState state = stateService.getState();
				int button = display.buttonAt(event.getX(), event.getY());
				if (state != null && state.getActiveCompanion() != null && button >= 0)
				{
					String selected = button == 0 ? "care" : button == 1 ? "play" : "items";
					journeyMode = selected.equals(journeyMode) ? null : selected;
					refresh();
				}
				else { inspectActive(); }
			}
		});
		display.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override public void mouseMoved(java.awt.event.MouseEvent event)
			{
				handheldHoverButton = display.buttonAt(event.getX(), event.getY());
				display.setCursor(handheldHoverButton >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				display.setToolTipText(handheldHoverButton == 0 ? "A · Care and memories"
					: handheldHoverButton == 1 ? "B · Play"
					: handheldHoverButton == 2 ? "C · Toy box" : null);
				display.repaint();
			}
		});
		animationTimer = new Timer(100, event -> display.repaint());
		animationTimer.start();
		stateService.addListener(stateListener);
		refresh();
	}

	public void shutDown()
	{
		animationTimer.stop();
		stateService.removeListener(stateListener);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(new Color(0x161718));
		header.setBorder(new EmptyBorder(10, 10, 8, 10));
		JLabel title = new JLabel("GIELIGOTCHI");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
		title.setForeground(new Color(0xF2C45A));
		JLabel subtitle = new JLabel("A tiny life in Gielinor");
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setForeground(new Color(0xB5B5B5));
		title.setAlignmentX(LEFT_ALIGNMENT);
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.WEST);
		JLabel discord = new JLabel(new ImageIcon(ImageUtil.loadImageResource(GieligotchiPanel.class,
			"/com/gieligotchi/images/discord_icon.png")));
		discord.setBorder(BorderFactory.createEmptyBorder());
		discord.setOpaque(false);
		discord.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		discord.setToolTipText("Join the Gieligotchi Discord");
		Dimension discordSize = new Dimension(18, 14);
		discord.setPreferredSize(discordSize);
		discord.setMinimumSize(discordSize);
		discord.setMaximumSize(discordSize);
		discord.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mouseClicked(java.awt.event.MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event)) { LinkBrowser.browse(DISCORD_INVITE); }
			}
		});
		titleRow.add(discord, BorderLayout.EAST);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		header.add(titleRow);
		JPanel meta = new JPanel(new BorderLayout());
		meta.setOpaque(false);
		meta.add(subtitle, BorderLayout.WEST);
		JPanel balance = new JPanel();
		balance.setOpaque(false);
		balance.setLayout(new BoxLayout(balance, BoxLayout.Y_AXIS));
		currency.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		currency.setForeground(new Color(0xE8D8A0));
		currency.setHorizontalAlignment(SwingConstants.CENTER);
		currency.setAlignmentX(CENTER_ALIGNMENT);
		JLabel pointsLabel = new JLabel("Gotchi Points");
		pointsLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
		pointsLabel.setForeground(new Color(0xBEB28C));
		pointsLabel.setAlignmentX(CENTER_ALIGNMENT);
		balance.add(currency);
		balance.add(pointsLabel);
		meta.add(balance, BorderLayout.EAST);
		meta.setAlignmentX(LEFT_ALIGNMENT);
		meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		header.add(meta);
		return header;
	}

	private JPanel buildTabs()
	{
		JPanel tabs = new JPanel();
		tabs.setLayout(new BoxLayout(tabs, BoxLayout.Y_AXIS));
		tabs.setBorder(new EmptyBorder(6, PAGE_GUTTER, 8, PAGE_GUTTER));
		tabs.setBackground(new Color(0x171819));
		JPanel primary = new JPanel(new GridLayout(1, 3, 3, 0));
		primary.setOpaque(false);
		primary.add(tab("Home", HOME));
		primary.add(tab("Collection", "collection"));
		primary.add(tab("Shop", "shop"));
		JPanel reference = new JPanel(new GridLayout(1, 3, 3, 0));
		reference.setOpaque(false);
		reference.add(tab("Guide", "guide"));
		reference.add(tab("Rules", "rules"));
		reference.add(tab("Rarities", "rarities"));
		tabs.add(primary);
		tabs.add(Box.createVerticalStrut(3));
		tabs.add(reference);
		return tabs;
	}

	private JButton tab(String label, String page)
	{
		JButton button = styledButton(label, 12f);
		button.setMargin(new Insets(2, 1, 2, 1));
		button.setPreferredSize(new Dimension(0, 28));
		button.putClientProperty("gieligotchi.page", page);
		navigationTabs.put(page, button);
		button.addActionListener(event -> showPage(page));
		return button;
	}

	private void showPage(String page)
	{
		activePage = page;
		pages.show(pageHost, page);
		for (JButton button : navigationTabs.values()) { updateButtonVisual(button); }
	}

	private JButton styledButton(String label)
	{
		return styledButton(label, 12f);
	}

	private JButton styledButton(String label, float fontSize)
	{
		JButton button = new JButton(label);
		button.setFocusable(false);
		button.setFont(FontManager.getRunescapeSmallFont().deriveFont(Math.max(12f, fontSize)));
		button.setForeground(new Color(0xE8E1D2));
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setMargin(new Insets(2, 4, 2, 4));
		button.setRolloverEnabled(true);
		button.getModel().addChangeListener(event -> updateButtonVisual(button));
		updateButtonVisual(button);
		return button;
	}

	private void updateButtonVisual(JButton button)
	{
		boolean selected = button.getClientProperty("gieligotchi.page") != null
			&& button.getClientProperty("gieligotchi.page").equals(activePage)
			|| Boolean.TRUE.equals(button.getClientProperty("gieligotchi.selected"));
		boolean pressed = button.getModel().isPressed();
		boolean hovered = button.getModel().isRollover();
		Color background = selected ? new Color(0x665126)
			: pressed ? new Color(0x51452F) : hovered ? new Color(0x403A2D) : new Color(0x292A28);
		Color border = selected ? new Color(0xE0B34E)
			: hovered ? new Color(0xA3874A) : new Color(0x5F594C);
		button.setBackground(background);
		button.setForeground(selected ? new Color(0xFFE09A) : new Color(0xE8E1D2));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(border), new EmptyBorder(4, 6, 4, 6)));
	}

	private JScrollPane wrap(JPanel content)
	{
		content.setOpaque(false);
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	public void showFirstRunWelcome()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::showFirstRunWelcome);
			return;
		}
		if (!introPlayed)
		{
			introPlayed = true;
			showPage("boot");
			boot.play(() -> showPage(HOME));
		}
		else { showPage(HOME); }
		refresh();
	}

	public void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
		ProfileState state = stateService.getState();
		if (state != null && state.getActiveEgg() != null)
		{
			journeyMode = null;
			handheldHoverButton = -1;
			handheldPressedButton = -1;
		}
		currency.setText(state == null ? "" : format(state.getGotchiPoints()));
		display.setCursor(state != null && state.getActiveEgg() != null
			? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
		display.setJourneyButtonsEnabled(state != null && state.getActiveCompanion() != null);
		home.removeAll();
		home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
		home.setBorder(new EmptyBorder(12, PAGE_GUTTER, 14, PAGE_GUTTER));
		display.setAlignmentX(CENTER_ALIGNMENT);
		home.add(display);
		home.add(Box.createVerticalStrut(8));
		JLabel hint = new JLabel(activeHint(state), SwingConstants.CENTER);
		hint.setAlignmentX(CENTER_ALIGNMENT);
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(new Color(0xD8D8D8));
		home.add(hint);
		if (state != null && !state.isWelcomeSeen())
		{
			home.add(Box.createVerticalStrut(12));
			home.add(buildWelcomeCard());
		}
		if (state != null && state.getActiveCompanion() != null)
		{
			home.add(Box.createVerticalStrut(12));
			home.add(buildActiveCompanionCard(state.getActiveCompanion()));
			if (journeyMode != null)
			{
				home.add(Box.createVerticalStrut(7));
				home.add(buildJourneyPanel(state));
			}
		}
		home.add(Box.createVerticalStrut(10));
		home.add(buildTray(state));
		if (state != null && state.getActiveEgg() == null && state.getActiveCompanion() == null)
		{
			home.add(Box.createVerticalStrut(10));
			home.add(buildEggShop(state));
		}
		home.revalidate();
		home.repaint();
		rebuildCollection(state);
		rebuildShop(state);
		rebuildGuide();
		rebuildRules();
		rebuildRarities();
		display.repaint();
	}

	private JPanel buildWelcomeCard()
	{
		JPanel card = new JPanel(new BorderLayout(0, 8));
		card.setBackground(new Color(0x242728));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x8D713F)), new EmptyBorder(12, 12, 12, 12)));
		JLabel heading = new JLabel("YOUR FIRST EGG");
		heading.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		heading.setForeground(new Color(0xF2C45A));
		JTextArea explanation = new JTextArea(
			"This Common egg is free, tuned for roughly 30 minutes of fresh-account play, and cannot be sold.\n\n"
				+ "Keep it active while you play. Eligible skill XP adds Bonding XP. At 100%, left-click the egg to hatch it.");
		explanation.setEditable(false);
		explanation.setOpaque(false);
		explanation.setLineWrap(true);
		explanation.setWrapStyleWord(true);
		explanation.setFont(FontManager.getRunescapeSmallFont().deriveFont(15f));
		explanation.setForeground(new Color(0xE0E0E0));
		explanation.setBorder(BorderFactory.createEmptyBorder());
		explanation.setMinimumSize(new Dimension(0, 0));
		JButton dismiss = styledButton("Got it — start raising", 13f);
		dismiss.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		dismiss.addActionListener(event -> stateService.markWelcomeSeen());
		card.add(heading, BorderLayout.NORTH);
		card.add(explanation, BorderLayout.CENTER);
		card.add(dismiss, BorderLayout.SOUTH);
		card.setAlignmentX(CENTER_ALIGNMENT);
		card.setPreferredSize(new Dimension(190, 238));
		card.setMinimumSize(new Dimension(0, 238));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 238));
		return card;
	}

	private JPanel buildActiveCompanionCard(CompanionInstance companion)
	{
		PetDefinition pet = catalogue.find(companion.getSpeciesId());
		String name = companion.getDisplayName(pet == null ? "Companion" : pet.getName());
		int level = LevelCurve.levelFor(companion);
		long floor = LevelCurve.xpForLevel(companion, level);
		long ceiling = level >= 99 ? floor : LevelCurve.xpForLevel(companion, level + 1);
		long levelXp = level >= 99 ? 0 : Math.max(0, companion.getLifetimeXp() - floor);
		long nextXp = level >= 99 ? 0 : Math.max(1, ceiling - floor);
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(new Color(0x202224));
		Color border = companion.isLegacy() ? new Color(0xF2C45A)
			: companion.getAffectionHearts() >= 60 ? new Color(0xDB85A9)
			: pet == null ? new Color(0x49443A) : RarityColours.species(pet.getRarity());
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(border), new EmptyBorder(8, 9, 8, 9)));
		JLabel title = new JLabel(name.toUpperCase(Locale.ENGLISH));
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
		title.setForeground(new Color(0xF2C45A));
		JButton minimize = styledButton(activeCompanionCardMinimized ? "+" : "−", 12f);
		minimize.setToolTipText(activeCompanionCardMinimized ? "Show companion details" : "Minimise companion details");
		minimize.setMargin(new Insets(0, 0, 0, 0));
		minimize.setPreferredSize(new Dimension(24, 20));
		minimize.addActionListener(event ->
		{
			activeCompanionCardMinimized = !activeCompanionCardMinimized;
			refresh();
		});
		JPanel cardHeader = new JPanel(new BorderLayout(5, 0));
		cardHeader.setOpaque(false);
		cardHeader.add(title, BorderLayout.WEST);
		cardHeader.add(minimize, BorderLayout.EAST);
		cardHeader.setAlignmentX(LEFT_ALIGNMENT);
		cardHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		String rarity = pet == null ? "" : pet.getRarity().getDisplayName() + " · ";
		JLabel identity = new JLabel(rarity + companion.getPalette().getDisplayName());
		identity.setFont(FontManager.getRunescapeSmallFont().deriveFont(13f));
		identity.setForeground(RarityColours.palette(companion.getPalette()));
		identity.setAlignmentX(LEFT_ALIGNMENT);
		JLabel progress = new JLabel(level >= 99 ? "Level 99 · Max level · " + format(companion.getLifetimeXp()) + " XP"
			: "Level " + level + " · " + format(levelXp) + " / " + format(nextXp) + " XP this level");
		progress.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		progress.setForeground(new Color(0xD8D8D8));
		progress.setAlignmentX(LEFT_ALIGNMENT);
		String personality = companion.getPersonality() == null ? "Personality undiscovered" : companion.getPersonality().getDisplayName();
		JLabel bond = new JLabel("♥ " + companion.getAffectionHearts() + " · "
			+ companion.getRelationshipStage().getDisplayName() + "  |  " + personality);
		bond.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		bond.setForeground(new Color(0xE4A3A0));
		bond.setAlignmentX(LEFT_ALIGNMENT);
		JLabel value = new JLabel(format(companion.getLifetimeXp()) + " lifetime XP  ·  "
			+ format(CompanionValue.saleValue(companion)) + " GPts value");
		value.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		value.setForeground(new Color(0xBEB28C));
		value.setAlignmentX(LEFT_ALIGNMENT);
		card.add(cardHeader);
		if (!activeCompanionCardMinimized)
		{
			card.add(identity);
		}
		card.add(progress);
		if (!activeCompanionCardMinimized)
		{
			card.add(bond);
			card.add(value);
		}
		card.setAlignmentX(CENTER_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, activeCompanionCardMinimized ? 54 : 100));
		return card;
	}

	private JPanel buildJourneyPanel(ProfileState state)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(new Color(0x202224));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x6A5A39)), new EmptyBorder(8, 8, 8, 8)));
		String section = "care".equals(journeyMode) ? "CARE & MEMORIES"
			: "play".equals(journeyMode) ? "PLAY" : "TOY BOX";
		JLabel title = new JLabel(section, SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		title.setForeground(new Color(0xD7B867));
		panel.add(title, BorderLayout.NORTH);
		int availableWidth = Math.max(190, home.getWidth() - home.getInsets().left - home.getInsets().right);
		int contentWidth = Math.max(160, availableWidth - 18);
		JPanel content = "play".equals(journeyMode) ? buildGamePanel()
			: "items".equals(journeyMode) ? buildItemsPanel(state)
			: buildCarePanel(state.getActiveCompanion(), contentWidth);
		panel.add(content, BorderLayout.CENTER);
		Dimension preferred = panel.getPreferredSize();
		int minimumHeight = "items".equals(journeyMode) ? 76 : 0;
		Dimension fixed = new Dimension(availableWidth, Math.max(preferred.height, minimumHeight));
		panel.setPreferredSize(fixed);
		panel.setMinimumSize(fixed);
		panel.setMaximumSize(fixed);
		panel.setAlignmentX(CENTER_ALIGNMENT);
		return panel;
	}

	private JPanel buildCarePanel(CompanionInstance companion, int contentWidth)
	{
		JPanel care = new JPanel();
		care.setOpaque(false);
		care.setLayout(new BoxLayout(care, BoxLayout.Y_AXIS));
		care.setAlignmentX(CENTER_ALIGNMENT);
		care.setMinimumSize(new Dimension(0, 0));
		care.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		RelationshipStage stage = companion.getRelationshipStage();
		JProgressBar relationship = new JProgressBar(0, 100);
		relationship.setValue(companion.getAffectionHearts());
		relationship.setStringPainted(true);
		relationship.setString("♥ " + companion.getAffectionHearts() + " hearts · " + stage.getDisplayName());
		relationship.setFont(FontManager.getRunescapeBoldFont().deriveFont(12f));
		relationship.setForeground(new Color(0xC97078));
		relationship.setMinimumSize(new Dimension(0, 21));
		relationship.setPreferredSize(new Dimension(190, 21));
		relationship.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
		relationship.setAlignmentX(CENTER_ALIGNMENT);
		care.add(relationship);
		care.add(Box.createVerticalStrut(5));
		String personality = companion.getPersonality() == null
			? "Personality locked · " + Math.max(0, 15 - companion.getAffectionHearts()) + " hearts to reveal"
			: companion.getPersonality().getDisplayName() + " · " + companion.getPersonality().getDescription();
		JTextArea personalityLabel = fittedParagraph(personality, 12f, contentWidth, 18);
		personalityLabel.setForeground(companion.getPersonality() == null ? new Color(0xA8A8A8) : new Color(0xD9D0BA));
		personalityLabel.setAlignmentX(CENTER_ALIGNMENT);
		care.add(personalityLabel);
		care.add(Box.createVerticalStrut(9));
		CompanionWish wish = companion.getWish();
		JLabel wishTitle = new JLabel("CURRENT WISH");
		wishTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		wishTitle.setForeground(new Color(0xD7B867));
		wishTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		wishTitle.setAlignmentX(CENTER_ALIGNMENT);
		care.add(wishTitle);
		JTextArea wishText = fittedParagraph(wish == null ? "A new wish is forming…" : wish.getLabel(),
			12f, contentWidth, 18);
		wishText.setAlignmentX(CENTER_ALIGNMENT);
		care.add(wishText);
		if (wish != null)
		{
			JProgressBar progress = new JProgressBar(0, 1000);
			progress.setValue((int) Math.round(Math.min(1d, wish.getProgress() / (double) wish.getTarget()) * 1000));
			progress.setStringPainted(true);
			progress.setString(format(wish.getProgress()) + " / " + format(wish.getTarget()));
			progress.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
			progress.setForeground(new Color(0x7CB06B));
			progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
			progress.setAlignmentX(CENTER_ALIGNMENT);
			care.add(progress);
			care.add(Box.createVerticalStrut(4));
			JPanel wishActions = new JPanel(new GridLayout(1, 2, 4, 0));
			wishActions.setOpaque(false);
			long rewardXp = CompanionWish.rewardXp(wish.getType(), LevelCurve.levelFor(companion));
			JButton claim = styledButton(wish.isComplete() ? "Claim · " + format(rewardXp) + " XP" : "Claim at 100%", 10f);
			claim.setEnabled(wish.isComplete());
			claim.setToolTipText("Awards " + format(rewardXp) + " Bonding XP, 1 heart and 3 Gotchi Points");
			claim.addActionListener(event ->
			{
				effects.trigger(CompanionEffectController.Effect.WISH);
				stateService.claimWish();
			});
			JButton reroll = styledButton("Skip wish (" + companion.getWishSkips() + ")", 10f);
			reroll.setEnabled(companion.getWishSkips() > 0);
			reroll.setToolTipText(companion.getWishSkips() >= 3 ? "3 / 3 skips available"
				: format(5_000L - companion.getWishSkipXpRemainder()) + " Bonding XP until the next skip");
			reroll.addActionListener(event -> stateService.rerollWish());
			wishActions.add(claim); wishActions.add(reroll);
			wishActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
			wishActions.setAlignmentX(CENTER_ALIGNMENT);
			care.add(wishActions);
		}
		if (companion.getAffectionHearts() >= 5)
		{
			care.add(Box.createVerticalStrut(7));
			JPanel naming = new JPanel(new BorderLayout(4, 0));
			naming.setOpaque(false);
			JTextField name = new JTextField(companion.getCustomName() == null ? "" : companion.getCustomName());
			name.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
			JButton save = styledButton("Name", 10f);
			save.addActionListener(event -> stateService.renameActiveCompanion(name.getText()));
			naming.add(name, BorderLayout.CENTER); naming.add(save, BorderLayout.EAST);
			naming.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
			naming.setAlignmentX(CENTER_ALIGNMENT);
			care.add(naming);
		}
		care.add(Box.createVerticalStrut(9));
		JLabel memoryTitle = new JLabel(companion.isLegacy() ? "MEMORY BOOK · LEGACY" : "MEMORY BOOK");
		memoryTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		memoryTitle.setForeground(companion.isLegacy() ? new Color(0xF2C45A) : new Color(0xD7B867));
		memoryTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		memoryTitle.setAlignmentX(CENTER_ALIGNMENT);
		care.add(memoryTitle);
		if (companion.getFavoriteSkill() != null)
		{
			JLabel favourite = new JLabel("Favourite skill: " + companion.getFavoriteSkill());
			favourite.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
			favourite.setForeground(new Color(0xBEB28C));
			favourite.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
			favourite.setAlignmentX(CENTER_ALIGNMENT);
			care.add(favourite);
		}
		int memoryCount = companion.getMemories().size();
		if (memoryCount == 0)
		{
			JTextArea emptyMemories = fittedParagraph(
				"Your shared milestones will be recorded here as you play.", 11f, contentWidth, 18);
			emptyMemories.setForeground(new Color(0x999999));
			emptyMemories.setAlignmentX(CENTER_ALIGNMENT);
			care.add(emptyMemories);
		}
		int memoryPageCount = Math.max(1, (memoryCount + 1) / 2);
		careMemoryPage = Math.max(0, Math.min(careMemoryPage, memoryPageCount - 1));
		int newestMemory = memoryCount - 1 - careMemoryPage * 2;
		int oldestMemory = Math.max(0, newestMemory - 1);
		for (int i = newestMemory; i >= oldestMemory && i >= 0; i--)
		{
			MemoryEntry memory = companion.getMemories().get(i);
			JTextArea line = fittedParagraph("• " + memory.getTitle() + " — " + memory.getDetail(),
				11f, contentWidth, 18);
			line.setAlignmentX(CENTER_ALIGNMENT);
			line.setToolTipText(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(new Date(memory.getCreatedAt())));
			care.add(line);
		}
		if (memoryPageCount > 1)
		{
			care.add(Box.createVerticalStrut(3));
			JPanel memoryControls = pageControls("Memories " + (careMemoryPage + 1) + " / " + memoryPageCount,
				careMemoryPage > 0, careMemoryPage + 1 < memoryPageCount,
				() -> { careMemoryPage--; refresh(); }, () -> { careMemoryPage++; refresh(); });
			memoryControls.setAlignmentX(CENTER_ALIGNMENT);
			care.add(memoryControls);
		}
		return care;
	}

	private JPanel buildGamePanel()
	{
		JPanel game = new JPanel();
		game.setOpaque(false);
		game.setLayout(new BoxLayout(game, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("HIGHER OR LOWER");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		title.setForeground(new Color(0xD7B867));
		title.setAlignmentX(CENTER_ALIGNMENT);
		game.add(title);
		JLabel card = new JLabel(gameCard == 0 ? "?" : Integer.toString(gameCard), SwingConstants.CENTER);
		card.setFont(FontManager.getRunescapeBoldFont().deriveFont(34f));
		card.setForeground(new Color(0xE8E1D2));
		card.setAlignmentX(CENTER_ALIGNMENT);
		game.add(card);
		JLabel message = new JLabel(gameMessage, SwingConstants.CENTER);
		message.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		message.setForeground(new Color(0xC8C8C8));
		message.setAlignmentX(CENTER_ALIGNMENT);
		game.add(message);
		game.add(Box.createVerticalStrut(5));
		if (gameCard == 0 || gameRound >= 3)
		{
			JButton start = styledButton("Start three rounds", 11f);
			start.setAlignmentX(CENTER_ALIGNMENT);
			start.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			start.addActionListener(event -> startGame());
			game.add(start);
		}
		else
		{
			JPanel guesses = new JPanel(new GridLayout(1, 2, 5, 0));
			guesses.setOpaque(false);
			JButton lower = styledButton("A · Lower", 11f);
			JButton higher = styledButton("C · Higher", 11f);
			lower.addActionListener(event -> guess(false));
			higher.addActionListener(event -> guess(true));
			guesses.add(lower); guesses.add(higher);
			guesses.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			game.add(guesses);
		}
		JLabel rounds = new JLabel("Round " + Math.min(3, gameRound + 1) + " / 3 · Score " + gameScore);
		rounds.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
		rounds.setForeground(new Color(0xBEB28C));
		rounds.setAlignmentX(CENTER_ALIGNMENT);
		game.add(Box.createVerticalStrut(4)); game.add(rounds);
		return game;
	}

	private void startGame()
	{
		gameCard = 1 + gameRandom.nextInt(9);
		gameRound = 0;
		gameScore = 0;
		gameMessage = "Will the next card be higher or lower?";
		refresh();
	}

	private void guess(boolean higher)
	{
		int next = 1 + gameRandom.nextInt(9);
		boolean correct = higher ? next > gameCard : next < gameCard;
		if (next == gameCard) { correct = true; }
		if (correct) { gameScore++; }
		if (correct) { effects.trigger(CompanionEffectController.Effect.CORRECT); }
		gameRound++;
		gameMessage = "It was " + next + " — " + (correct ? "correct!" : "not this time");
		gameCard = next;
		if (gameRound >= 3)
		{
			boolean won = gameScore >= 2;
			if (won) { effects.trigger(CompanionEffectController.Effect.CELEBRATE); }
			stateService.recordGame(won);
			gameMessage = won ? "You won — Play wishes progress!" : "Close one — try again";
		}
		refresh();
	}

	private JPanel buildItemsPanel(ProfileState state)
	{
		JPanel items = new JPanel();
		items.setOpaque(false);
		items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
		Toy equipped = state.getEquippedToy();
		JLabel equippedLabel = new JLabel(equipped == null ? "No toy equipped"
			: "Equipped · " + equipped.getDisplayName(), SwingConstants.CENTER);
		equippedLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		equippedLabel.setForeground(new Color(0xC9A95F));
		equippedLabel.setAlignmentX(CENTER_ALIGNMENT);
		items.add(equippedLabel);
		items.add(Box.createVerticalStrut(5));
		if (state.getOwnedToyIds().isEmpty())
		{
			JLabel empty = new JLabel("Visit the Cosmetic Shop", SwingConstants.CENTER);
			empty.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
			empty.setForeground(new Color(0xE2E2E2));
			empty.setAlignmentX(CENTER_ALIGNMENT);
			empty.setMinimumSize(new Dimension(0, 20));
			empty.setPreferredSize(new Dimension(190, 20));
			empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
			items.add(empty);
		}
		JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5));
		grid.setOpaque(false);
		for (String id : state.getOwnedToyIds())
		{
			Toy toy = Toy.fromId(id);
			if (toy == null) { continue; }
			BufferedImage image = SpriteAssets.toy(toy);
			JButton play = styledButton(toyShortName(toy), 10f);
			play.setIcon(image == null ? null : new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(image, 34, 34)));
			play.setVerticalTextPosition(SwingConstants.BOTTOM);
			play.setHorizontalTextPosition(SwingConstants.CENTER);
			play.setToolTipText((equipped == toy ? "Play with " : "Equip and play with ") + toy.getDisplayName());
			play.putClientProperty("gieligotchi.selected", equipped == toy);
			updateButtonVisual(play);
			CompanionInstance activeCompanion = state.getActiveCompanion();
			long cooldownRemaining = activeCompanion == null ? 0L
				: Math.max(0L, TOY_PLAY_COOLDOWN_MILLIS
					- (System.currentTimeMillis() - activeCompanion.getLastToyPlayedAt()));
			play.setEnabled(activeCompanion != null && cooldownRemaining == 0L);
			if (cooldownRemaining > 0L)
			{
				play.setToolTipText("Ready again in " + ((cooldownRemaining + 999L) / 1_000L) + " seconds");
			}
			play.addActionListener(event ->
			{
				if (stateService.playWithToy(toy))
				{
					effects.trigger(CompanionEffectController.Effect.HEARTS);
					Timer cooldownRefresh = new Timer((int) TOY_PLAY_COOLDOWN_MILLIS + 50, refreshEvent -> refresh());
					cooldownRefresh.setRepeats(false);
					cooldownRefresh.start();
				}
			});
			play.setPreferredSize(new Dimension(90, 58));
			grid.add(play);
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			Math.max(1, (state.getOwnedToyIds().size() + 1) / 2) * 63));
		items.add(grid);
		return items;
	}

	private String toyShortName(Toy toy)
	{
		switch (toy)
		{
			case PLAY_BALL: return "Gnomeball";
			case RUNE_BLOCKS: return "Rune pile";
			case FEATHER_WAND: return "Hand fan";
			default: return "Jad plush";
		}
	}

	private JPanel buildTray(ProfileState state)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 8));
		panel.setOpaque(true);
		panel.setBackground(new Color(0x202224));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x49443A)), new EmptyBorder(8, 8, 8, 8)));
		JLabel title = new JLabel("STASH TRAY");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(new Color(0xD7B867));
		int eggs = state == null ? 0 : state.getStashedEggs().size();
		int companions = state == null ? 0 : state.getStashedCompanions().size();
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		heading.add(title, BorderLayout.WEST);
		JLabel contents = new JLabel("Eggs " + eggs + "  •  Pets " + companions);
		contents.setForeground(new Color(0xC4C4C4));
		contents.setFont(FontManager.getRunescapeSmallFont());
		heading.add(contents, BorderLayout.EAST);
		panel.add(heading, BorderLayout.NORTH);

		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		actions.setOpaque(false);
		if (state != null && (state.getActiveEgg() != null || state.getActiveCompanion() != null))
		{
			String type = state.getActiveEgg() != null ? "egg" : "companion";
			JButton stash = styledButton("Stash active " + type);
			stash.setAlignmentX(CENTER_ALIGNMENT);
			stash.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			stash.addActionListener(event -> stateService.stashActive());
			actions.add(stash);
		}
		if (state != null)
		{
			int eggCount = state.getStashedEggs().size();
			int companionCount = state.getStashedCompanions().size();
			int totalEntries = eggCount + companionCount;
			int pageCount = Math.max(1, (totalEntries + STASHED_COMPANIONS_PER_PAGE - 1) / STASHED_COMPANIONS_PER_PAGE);
			stashCompanionPage = Math.max(0, Math.min(stashCompanionPage, pageCount - 1));
			int firstEntry = stashCompanionPage * STASHED_COMPANIONS_PER_PAGE;
			int entryLimit = Math.min(totalEntries, firstEntry + STASHED_COMPANIONS_PER_PAGE);
			for (int entry = firstEntry; entry < entryLimit; entry++)
			{
				if (actions.getComponentCount() > 0) { actions.add(Box.createVerticalStrut(5)); }
				if (entry < eggCount)
				{
					final int eggIndex = entry;
					EggState egg = state.getStashedEggs().get(eggIndex);
					JButton activate = styledButton("Incubate " + egg.getTier().getDisplayName() + " egg");
					activate.setEnabled(state.getActiveEgg() == null && state.getActiveCompanion() == null);
					activate.setAlignmentX(CENTER_ALIGNMENT);
					activate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
					activate.addActionListener(event -> stateService.activateEgg(eggIndex));
					actions.add(activate);
				}
				else
				{
					final int companionIndex = entry - eggCount;
					CompanionInstance companion = state.getStashedCompanions().get(companionIndex);
					actions.add(buildStashedCompanionRow(companion, companionIndex,
						state.getActiveEgg() == null && state.getActiveCompanion() == null));
				}
			}
			if (pageCount > 1)
			{
				if (actions.getComponentCount() > 0) { actions.add(Box.createVerticalStrut(6)); }
				actions.add(buildStashPagination(pageCount));
			}
		}
		if (actions.getComponentCount() == 0)
		{
			JLabel empty = new JLabel("Nothing waiting in the tray");
			empty.setForeground(new Color(0x999999));
			empty.setFont(FontManager.getRunescapeSmallFont());
			actions.add(empty);
		}
		panel.add(actions, BorderLayout.CENTER);
		panel.setAlignmentX(CENTER_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46 + actions.getPreferredSize().height));
		return panel;
	}

	private JPanel buildStashPagination(int pageCount)
	{
		JPanel pagination = new JPanel(new BorderLayout(5, 0));
		pagination.setOpaque(false);
		JButton previous = styledButton("‹", 14f);
		previous.setToolTipText("Previous five stash items");
		previous.setEnabled(stashCompanionPage > 0);
		previous.addActionListener(event ->
		{
			stashCompanionPage--;
			refresh();
		});
		JLabel page = new JLabel("Stash " + (stashCompanionPage + 1) + " / " + pageCount, SwingConstants.CENTER);
		page.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		page.setForeground(new Color(0xD8D8D8));
		JButton next = styledButton("›", 14f);
		next.setToolTipText("Next five stash items");
		next.setEnabled(stashCompanionPage + 1 < pageCount);
		next.addActionListener(event ->
		{
			stashCompanionPage++;
			refresh();
		});
		pagination.add(previous, BorderLayout.WEST);
		pagination.add(page, BorderLayout.CENTER);
		pagination.add(next, BorderLayout.EAST);
		pagination.setAlignmentX(CENTER_ALIGNMENT);
		pagination.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return pagination;
	}

	private JPanel buildStashedCompanionRow(CompanionInstance companion, int index, boolean slotFree)
	{
		PetDefinition pet = catalogue.find(companion.getSpeciesId());
		String name = pet == null ? "Companion" : pet.getName();
		long value = CompanionValue.saleValue(companion);
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setBackground(new Color(0x191B1C));
		row.setBorder(new EmptyBorder(5, 5, 5, 5));
		BufferedImage sprite = SpriteAssets.companion(companion);
		JLabel thumbnail = new JLabel(sprite == null ? null : new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(sprite, 42, 42)));
		thumbnail.setPreferredSize(new Dimension(42, 42));
		row.add(thumbnail, BorderLayout.WEST);
		JPanel words = new JPanel();
		words.setOpaque(false);
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		JLabel petName = new JLabel(name);
		petName.setFont(FontManager.getRunescapeSmallFont().deriveFont(13f));
		petName.setForeground(new Color(0xE6E6E6));
		JLabel meta = new JLabel("Lvl " + LevelCurve.levelFor(companion) + " · " + value + " GPts");
		meta.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		meta.setForeground(new Color(0xC9A95F));
		words.add(petName);
		words.add(meta);
		row.add(words, BorderLayout.CENTER);
		JPanel controls = new JPanel(new GridLayout(2, 1, 0, 3));
		controls.setOpaque(false);
		JButton activate = styledButton("Out", 10f);
		activate.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		activate.setEnabled(slotFree);
		activate.setToolTipText(slotFree ? "Make this the active companion" : "Clear the active slot first");
		activate.addActionListener(event -> stateService.activateCompanion(index));
		JButton sell = styledButton("Sell", 10f);
		sell.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		sell.setToolTipText("Sell permanently for " + value + " Gotchi Points");
		final boolean[] saleArmed = {false};
		sell.addActionListener(event ->
		{
			if (!saleArmed[0])
			{
				saleArmed[0] = true;
				sell.setText("Sure?");
				sell.setToolTipText("Click again to sell permanently; its Collection discovery remains");
				return;
			}
			stateService.sellStashedCompanion(companion.getInstanceId());
		});
		controls.add(activate);
		controls.add(sell);
		row.add(controls, BorderLayout.EAST);
		row.setAlignmentX(CENTER_ALIGNMENT);
		row.setPreferredSize(new Dimension(190, 54));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		return row;
	}

	private JPanel buildEggShop(ProfileState state)
	{
		JPanel shop = new JPanel(new BorderLayout(0, 7));
		shop.setBackground(new Color(0x202224));
		shop.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x49443A)), new EmptyBorder(8, 8, 8, 8)));
		JLabel title = new JLabel("EGG SHOP");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(new Color(0xD7B867));
		shop.add(title, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 5));
		buttons.setOpaque(false);
		boolean slotFree = state.getActiveEgg() == null && state.getActiveCompanion() == null;
		for (EggTier tier : EggTier.values())
		{
			JButton buy = styledButton(tier.getDisplayName() + " — " + tier.getPrice() + " GPts", 11f);
			buy.setEnabled(slotFree && state.getGotchiPoints() >= tier.getPrice());
			buy.setToolTipText(slotFree ? "Buy and activate this egg" : "Stash the active egg or companion first");
			buy.addActionListener(event -> stateService.buyEgg(tier));
			buttons.add(buy);
		}
		shop.add(buttons, BorderLayout.CENTER);
		shop.setAlignmentX(CENTER_ALIGNMENT);
		shop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
		return shop;
	}

	private void rebuildCollection(ProfileState state)
	{
		collection.removeAll();
		collection.setLayout(new BorderLayout(0, 8));
		collection.setBorder(new EmptyBorder(12, PAGE_GUTTER, 12, PAGE_GUTTER));
		int species = state == null ? 0 : state.getDiscoveries().size();
		int variants = state == null ? 0 : state.getDiscoveries().values().stream().mapToInt(d -> d.getPalettes().size()).sum();
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		JLabel progress = new JLabel("Species " + species + " / " + catalogue.all().size() + "    Variants " + variants + " / " + (catalogue.all().size() * Palette.values().length));
		progress.setForeground(new Color(0xE0C267));
		progress.setFont(FontManager.getRunescapeSmallFont());
		progress.setAlignmentX(LEFT_ALIGNMENT);
		header.add(progress);
		header.add(Box.createVerticalStrut(7));
		JPanel modes = new JPanel(new GridLayout(1, 2, 4, 0));
		modes.setOpaque(false);
		JButton companions = styledButton("Companions", 11f);
		companions.setEnabled(collectionHistoryVisible || collectionPetId != null);
		companions.addActionListener(event ->
		{
			collectionHistoryVisible = false;
			collectionPetId = null;
			collectionPreviewPalette = null;
			rebuildCollection(stateService.getState());
		});
		JButton history = styledButton("Hatch History", 11f);
		history.setEnabled(!collectionHistoryVisible);
		history.addActionListener(event ->
		{
			collectionHistoryVisible = true;
			hatchHistoryPage = 0;
			collectionPetId = null;
			collectionPreviewPalette = null;
			rebuildCollection(stateService.getState());
		});
		modes.add(companions);
		modes.add(history);
		modes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		modes.setAlignmentX(LEFT_ALIGNMENT);
		header.add(modes);
		collection.add(header, BorderLayout.NORTH);

		if (collectionHistoryVisible) { collection.add(buildHatchHistory(state), BorderLayout.CENTER); }
		else if (collectionPetId != null)
		{
			PetDefinition selected = catalogue.find(collectionPetId);
			if (selected == null) { collectionPetId = null; collection.add(buildCompanionGrid(state), BorderLayout.CENTER); }
			else { collection.add(buildPetCollectionDetail(selected, state), BorderLayout.CENTER); }
		}
		else { collection.add(buildCompanionGrid(state), BorderLayout.CENTER); }
		collection.revalidate();
		collection.repaint();
	}

	private JPanel buildCompanionGrid(ProfileState state)
	{
		JPanel result = new JPanel(new BorderLayout(0, 7));
		result.setOpaque(false);
		JPanel grid = new JPanel(new GridLayout(0, 3, 4, 4));
		grid.setOpaque(false);
		int total = catalogue.all().size();
		int pageCount = Math.max(1, (total + COLLECTION_PER_PAGE - 1) / COLLECTION_PER_PAGE);
		collectionPage = Math.max(0, Math.min(collectionPage, pageCount - 1));
		int first = collectionPage * COLLECTION_PER_PAGE;
		int limit = Math.min(total, first + COLLECTION_PER_PAGE);
		for (int i = first; i < limit; i++) { grid.add(collectionTile(catalogue.all().get(i), state)); }
		result.add(grid, BorderLayout.CENTER);
		result.add(pageControls("Page " + (collectionPage + 1) + " / " + pageCount,
			collectionPage > 0, collectionPage + 1 < pageCount,
			() -> { collectionPage--; rebuildCollection(stateService.getState()); },
			() -> { collectionPage++; rebuildCollection(stateService.getState()); }), BorderLayout.SOUTH);
		return result;
	}

	private JPanel buildHatchHistory(ProfileState state)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setOpaque(false);
		section.setAlignmentX(LEFT_ALIGNMENT);
		JLabel heading = new JLabel("HATCH HISTORY");
		heading.setForeground(new Color(0xD7B867));
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setAlignmentX(LEFT_ALIGNMENT);
		section.add(heading);
		section.add(Box.createVerticalStrut(6));
		if (state == null || state.getHatchHistory().isEmpty())
		{
			JLabel empty = new JLabel("Future hatches will be recorded here");
			empty.setForeground(new Color(0x999999));
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setAlignmentX(LEFT_ALIGNMENT);
			section.add(empty);
			return section;
		}
		int total = state.getHatchHistory().size();
		int pageCount = Math.max(1, (total + HATCHES_PER_PAGE - 1) / HATCHES_PER_PAGE);
		hatchHistoryPage = Math.max(0, Math.min(hatchHistoryPage, pageCount - 1));
		int newestIndex = total - 1 - hatchHistoryPage * HATCHES_PER_PAGE;
		int oldestIndex = Math.max(0, newestIndex - HATCHES_PER_PAGE + 1);
		for (int i = newestIndex; i >= oldestIndex; i--)
		{
			HatchReceipt receipt = state.getHatchHistory().get(i);
			PetDefinition pet = catalogue.find(receipt.getSpeciesId());
			JPanel row = new JPanel(new BorderLayout(7, 0));
			row.setBackground(new Color(0x202224));
			row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(RarityColours.species(receipt.getSpeciesRarity())),
				new EmptyBorder(5, 5, 5, 5)));
			BufferedImage sprite = SpriteAssets.pet(receipt.getSpeciesId(), receipt.getPalette().getAssetId());
			JLabel icon = new JLabel(sprite == null ? null : new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(sprite, 42, 42)));
			icon.setPreferredSize(new Dimension(42, 42));
			row.add(icon, BorderLayout.WEST);
			JPanel copy = new JPanel();
			copy.setOpaque(false);
			copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
			JLabel name = new JLabel(pet == null ? receipt.getSpeciesId() : pet.getName());
			name.setForeground(new Color(0xEEEEEE));
			name.setFont(FontManager.getRunescapeSmallFont().deriveFont(13f));
			JLabel rarity = new JLabel(receipt.getSpeciesRarity().getDisplayName() + " · "
				+ receipt.getPalette().getDisplayName() + " (" + receipt.getPalette().getChancePercent() + "%)");
			rarity.setForeground(RarityColours.palette(receipt.getPalette()));
			rarity.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
			JLabel date = new JLabel(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(
				new Date(receipt.getRevealedAt() == null ? receipt.getRolledAt() : receipt.getRevealedAt())));
			date.setForeground(new Color(0xAFAFAF));
			date.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
			JLabel chance = new JLabel("Exact hatch " + String.format(Locale.UK, "%.6f%%", receipt.getCombinedChance() * 100d));
			chance.setForeground(new Color(0xAFAFAF));
			chance.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
			copy.add(name);
			copy.add(rarity);
			copy.add(date);
			copy.add(chance);
			row.add(copy, BorderLayout.CENTER);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
			section.add(row);
			if (i > oldestIndex) { section.add(Box.createVerticalStrut(4)); }
		}
		section.add(Box.createVerticalStrut(8));
		JPanel pagination = new JPanel(new BorderLayout(6, 0));
		pagination.setOpaque(false);
		JButton newer = styledButton("‹ Newer", 12f);
		newer.setEnabled(hatchHistoryPage > 0);
		newer.addActionListener(event ->
		{
			hatchHistoryPage--;
			rebuildCollection(stateService.getState());
		});
		JLabel page = new JLabel("Page " + (hatchHistoryPage + 1) + " of " + pageCount, SwingConstants.CENTER);
		page.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		page.setForeground(new Color(0xD8D8D8));
		JButton older = styledButton("Older ›", 12f);
		older.setEnabled(hatchHistoryPage + 1 < pageCount);
		older.addActionListener(event ->
		{
			hatchHistoryPage++;
			rebuildCollection(stateService.getState());
		});
		pagination.add(newer, BorderLayout.WEST);
		pagination.add(page, BorderLayout.CENTER);
		pagination.add(older, BorderLayout.EAST);
		pagination.setAlignmentX(LEFT_ALIGNMENT);
		pagination.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		section.add(pagination);
		return section;
	}

	private JPanel buildPetCollectionDetail(PetDefinition pet, ProfileState state)
	{
		Discovery discovery = state == null ? null : state.getDiscoveries().get(pet.getId());
		JPanel detail = new JPanel();
		detail.setOpaque(false);
		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		JButton back = styledButton("‹ All companions", 11f);
		back.setAlignmentX(LEFT_ALIGNMENT);
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		back.addActionListener(event ->
		{
			collectionPetId = null;
			collectionPreviewPalette = null;
			rebuildCollection(stateService.getState());
		});
		detail.add(back);
		detail.add(Box.createVerticalStrut(7));

		Palette preview = collectionPreviewPalette;
		if (preview == null || discovery == null || !discovery.getPalettes().contains(preview))
		{
			preview = discovery == null ? null : discovery.getPalettes().stream().max(Enum::compareTo).orElse(Palette.BASE);
		}
		JPanel hero = new JPanel(new BorderLayout(8, 0));
		hero.setBackground(new Color(0x202224));
		hero.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(RarityColours.species(pet.getRarity())), new EmptyBorder(7, 7, 7, 7)));
		BufferedImage previewImage = preview == null ? null : SpriteAssets.pet(pet.getId(), preview.getAssetId());
		JLabel portrait = new JLabel(previewImage == null ? "?" : "",
			previewImage == null ? null : new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(previewImage, 82, 82)), SwingConstants.CENTER);
		portrait.setFont(FontManager.getRunescapeBoldFont().deriveFont(34f));
		portrait.setPreferredSize(new Dimension(82, 82));
		hero.add(portrait, BorderLayout.WEST);
		JPanel identity = new JPanel();
		identity.setOpaque(false);
		identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
		JLabel name = new JLabel(pet.getName().toUpperCase(Locale.ENGLISH));
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
		name.setForeground(new Color(0xF2C45A));
		JLabel rarity = new JLabel(pet.getRarity().getDisplayName() + " species");
		rarity.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		rarity.setForeground(RarityColours.species(pet.getRarity()));
		JLabel obtained = new JLabel(discovery == null ? "Not discovered" : discovery.getPalettes().size() + " / 11 colours");
		obtained.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		obtained.setForeground(new Color(0xC8C8C8));
		JLabel selectedPalette = new JLabel(preview == null ? "" : preview.getDisplayName());
		selectedPalette.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		selectedPalette.setForeground(preview == null ? new Color(0x999999) : RarityColours.palette(preview));
		identity.add(Box.createVerticalGlue());
		identity.add(name);
		identity.add(rarity);
		identity.add(obtained);
		identity.add(selectedPalette);
		identity.add(Box.createVerticalGlue());
		hero.add(identity, BorderLayout.CENTER);
		hero.setAlignmentX(LEFT_ALIGNMENT);
		hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		detail.add(hero);
		detail.add(Box.createVerticalStrut(9));
		JLabel variantsHeading = new JLabel("COLOUR VARIANTS");
		variantsHeading.setFont(FontManager.getRunescapeBoldFont());
		variantsHeading.setForeground(new Color(0xD7B867));
		variantsHeading.setAlignmentX(LEFT_ALIGNMENT);
		detail.add(variantsHeading);
		detail.add(Box.createVerticalStrut(5));
		JPanel variants = new JPanel(new GridLayout(0, 3, 4, 4));
		variants.setOpaque(false);
		for (Palette palette : Palette.values())
		{
			boolean unlocked = discovery != null && discovery.getPalettes().contains(palette);
			JButton tile = styledButton(unlocked ? palette.getDisplayName() : "?", 10f);
			tile.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
			tile.setVerticalTextPosition(SwingConstants.BOTTOM);
			tile.setHorizontalTextPosition(SwingConstants.CENTER);
			tile.setToolTipText(palette.getDisplayName() + " · " + palette.getChancePercent() + "% per hatch");
			if (unlocked)
			{
				BufferedImage variant = SpriteAssets.pet(pet.getId(), palette.getAssetId());
				if (variant != null) { tile.setIcon(new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(variant, 56, 56))); }
				tile.setForeground(RarityColours.palette(palette));
				tile.addActionListener(event ->
				{
					collectionPreviewPalette = palette;
					rebuildCollection(stateService.getState());
				});
			}
			else { tile.setEnabled(false); tile.setFont(FontManager.getRunescapeBoldFont().deriveFont(22f)); }
			tile.setPreferredSize(new Dimension(66, 78));
			variants.add(tile);
		}
		variants.setAlignmentX(LEFT_ALIGNMENT);
		detail.add(variants);
		return detail;
	}

	private void rebuildShop(ProfileState state)
	{
		shop.removeAll();
		shop.setLayout(new BoxLayout(shop, BoxLayout.Y_AXIS));
		shop.setBorder(new EmptyBorder(12, PAGE_GUTTER, 14, PAGE_GUTTER));
		JLabel heading = new JLabel("COSMETIC SHOP");
		heading.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		heading.setForeground(new Color(0xF2C45A));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		shop.add(heading);
		shop.add(Box.createVerticalStrut(4));
		JTextArea intro = paragraph("Unlock permanent backdrops and toys with Gotchi Points. Toys add companion reactions and memories, never gameplay power.", 14f);
		intro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
		shop.add(intro);
		shop.add(Box.createVerticalStrut(8));
		JTextArea disclosure = paragraph("ARTWORK NOTE\nCompanion and backdrop artwork was created specifically for Gieligotchi with AI-assisted tools. We welcome collaboration with Old School RuneScape artists.", 12f);
		disclosure.setBackground(new Color(0x202224));
		disclosure.setOpaque(true);
		disclosure.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x6A5A39)), new EmptyBorder(7, 7, 7, 7)));
		disclosure.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
		shop.add(disclosure);
		shop.add(Box.createVerticalStrut(9));
		JPanel categories = new JPanel(new GridLayout(1, 2, 4, 0));
		categories.setOpaque(false);
		JButton toyTab = styledButton("Toys", 12f);
		JButton backdropTab = styledButton("Backdrops", 12f);
		toyTab.setEnabled(!shopShowsToys);
		backdropTab.setEnabled(shopShowsToys);
		toyTab.addActionListener(event -> { shopShowsToys = true; rebuildShop(stateService.getState()); });
		backdropTab.addActionListener(event -> { shopShowsToys = false; backdropShopPage = 0; rebuildShop(stateService.getState()); });
		categories.add(toyTab); categories.add(backdropTab);
		categories.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
		categories.setAlignmentX(LEFT_ALIGNMENT);
		shop.add(categories);
		shop.add(Box.createVerticalStrut(8));
		if (shopShowsToys)
		{
			sectionLabel(shop, "TOY SHOP");
			for (Toy toy : Toy.values())
			{
				shop.add(buildToyShopCard(toy, state));
				shop.add(Box.createVerticalStrut(6));
			}
		}
		else
		{
			sectionLabel(shop, "BACKDROPS");
			Backdrop[] backdrops = Backdrop.values();
			int pageCount = Math.max(1, (backdrops.length + BACKDROPS_PER_PAGE - 1) / BACKDROPS_PER_PAGE);
			backdropShopPage = Math.max(0, Math.min(backdropShopPage, pageCount - 1));
			int first = backdropShopPage * BACKDROPS_PER_PAGE;
			for (int i = first; i < Math.min(backdrops.length, first + BACKDROPS_PER_PAGE); i++)
			{
				shop.add(buildBackdropShopCard(backdrops[i], state));
				shop.add(Box.createVerticalStrut(6));
			}
			shop.add(pageControls("Scenes " + (backdropShopPage + 1) + " / " + pageCount,
				backdropShopPage > 0, backdropShopPage + 1 < pageCount,
				() -> { backdropShopPage--; rebuildShop(stateService.getState()); },
				() -> { backdropShopPage++; rebuildShop(stateService.getState()); }));
		}
		shop.revalidate();
		shop.repaint();
	}

	private JPanel buildToyShopCard(Toy toy, ProfileState state)
	{
		boolean owned = state != null && state.ownsToy(toy);
		boolean equipped = state != null && state.getEquippedToy() == toy;
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(new Color(0x202224));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(equipped ? new Color(0xD7B867) : new Color(0x49443A)),
			new EmptyBorder(6, 6, 6, 6)));
		BufferedImage image = SpriteAssets.toy(toy);
		JLabel preview = new JLabel(image == null ? null : new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(image, 62, 62)));
		preview.setHorizontalAlignment(SwingConstants.CENTER);
		preview.setPreferredSize(new Dimension(68, 68));
		card.add(preview, BorderLayout.WEST);
		JPanel info = new JPanel();
		info.setOpaque(false);
		info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
		JLabel name = new JLabel(toy.getDisplayName());
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		name.setForeground(new Color(0xEEEEEE));
		JLabel price = new JLabel(format(toy.getPrice()) + " Gotchi Points");
		price.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		price.setForeground(new Color(0xC9A95F));
		JButton action = styledButton(equipped ? "Equipped" : owned ? "Equip" : "Unlock", 11f);
		action.setEnabled(state != null && !equipped && (owned || state.getGotchiPoints() >= toy.getPrice()));
		action.addActionListener(event -> { if (owned) { stateService.equipToy(toy); } else { stateService.purchaseToy(toy); } });
		info.add(name); info.add(price); info.add(Box.createVerticalGlue()); info.add(action);
		card.add(info, BorderLayout.CENTER);
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
		return card;
	}

	private JPanel buildBackdropShopCard(Backdrop backdrop, ProfileState state)
	{
		boolean owned = state != null && state.ownsBackdrop(backdrop);
		boolean equipped = state != null && state.getEquippedBackdrop() == backdrop;
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(new Color(0x202224));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(equipped ? new Color(0xD7B867) : new Color(0x49443A)),
			new EmptyBorder(6, 6, 6, 6)));
		BufferedImage image = SpriteAssets.backdrop(backdrop.getAssetId());
		JLabel preview = new JLabel();
		preview.setOpaque(true);
		preview.setBackground(LCD);
		preview.setHorizontalAlignment(SwingConstants.CENTER);
		preview.setPreferredSize(new Dimension(76, 76));
		if (image != null) { preview.setIcon(new ImageIcon(SpriteAssets.resizeNearest(image, 76, 76))); }
		card.add(preview, BorderLayout.WEST);
		JPanel info = new JPanel();
		info.setOpaque(false);
		info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
		JLabel name = new JLabel(backdrop.getDisplayName());
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		name.setForeground(new Color(0xEEEEEE));
		JLabel price = new JLabel(backdrop == Backdrop.CLASSIC ? "Included" : format(backdrop.getPrice()) + " Gotchi Points");
		price.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		price.setForeground(new Color(0xC9A95F));
		JButton action = styledButton(equipped ? "Equipped" : owned ? "Equip" : "Unlock", 11f);
		action.setEnabled(state != null && !equipped && (owned || state.getGotchiPoints() >= backdrop.getPrice()));
		action.setToolTipText(!owned && state != null && state.getGotchiPoints() < backdrop.getPrice()
			? "You need " + format(backdrop.getPrice() - state.getGotchiPoints()) + " more Gotchi Points" : null);
		action.addActionListener(event ->
		{
			if (owned) { stateService.equipBackdrop(backdrop); }
			else { stateService.purchaseBackdrop(backdrop); }
		});
		info.add(name);
		info.add(price);
		info.add(Box.createVerticalGlue());
		info.add(action);
		card.add(info, BorderLayout.CENTER);
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		return card;
	}

	private void rebuildRarities()
	{
		rarities.removeAll();
		rarities.setLayout(new BoxLayout(rarities, BoxLayout.Y_AXIS));
		rarities.setBorder(new EmptyBorder(12, PAGE_GUTTER, 14, PAGE_GUTTER));
		JLabel heading = new JLabel("RARITIES & ODDS");
		heading.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		heading.setForeground(new Color(0xF2C45A));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		rarities.add(heading);
		rarities.add(Box.createVerticalStrut(5));
		JTextArea intro = paragraph("Every hatch makes two independent rolls: the egg chooses a species rarity, then every pet rolls the same colour table. A rarer egg never changes colour odds.", 14f);
		intro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
		rarities.add(intro);
		rarities.add(Box.createVerticalStrut(10));
		JPanel categories = new JPanel(new GridLayout(1, 3, 4, 0));
		categories.setOpaque(false);
		for (String section : new String[]{"eggs", "species", "colours"})
		{
			JButton tab = styledButton(section.substring(0, 1).toUpperCase(Locale.ENGLISH) + section.substring(1), 11f);
			tab.setEnabled(!section.equals(raritySection));
			tab.addActionListener(event -> { raritySection = section; rebuildRarities(); });
			categories.add(tab);
		}
		categories.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
		categories.setAlignmentX(LEFT_ALIGNMENT);
		rarities.add(categories);
		rarities.add(Box.createVerticalStrut(9));
		if ("eggs".equals(raritySection))
		{
			sectionLabel(rarities, "EGG SPECIES ODDS");
			for (EggTier tier : EggTier.values()) { rarities.add(buildEggOddsCard(tier)); rarities.add(Box.createVerticalStrut(5)); }
		}
		else if ("species".equals(raritySection))
		{
			sectionLabel(rarities, "SPECIES RARITY");
			for (SpeciesRarity rarity : SpeciesRarity.values())
			{
				int count = catalogue.byRarity().get(rarity).size();
				rarities.add(rarityRow(RarityColours.species(rarity), rarity.getDisplayName(), count + " pets · sale base " + format(rarity.getSaleBase()) + " GPts"));
				rarities.add(Box.createVerticalStrut(3));
			}
		}
		else
		{
			sectionLabel(rarities, "COLOUR ROLL");
			JLabel variantChance = new JLabel("Any non-Base colour: 43.6%  •  about 1 in 2.3");
			variantChance.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
			variantChance.setForeground(new Color(0xDDDDDD));
			variantChance.setAlignmentX(LEFT_ALIGNMENT);
			rarities.add(variantChance);
			rarities.add(Box.createVerticalStrut(6));
			for (Palette palette : Palette.values())
			{
				double oneIn = 100d / palette.getChancePercent();
				rarities.add(rarityRow(RarityColours.palette(palette), palette.getDisplayName(),
					palette.getChancePercent() + "% · about 1 in " + (oneIn >= 100 ? format(Math.round(oneIn)) : String.format(Locale.UK, "%.1f", oneIn))));
				rarities.add(Box.createVerticalStrut(3));
			}
		}
		rarities.revalidate();
		rarities.repaint();
	}

	private void rebuildGuide()
	{
		guide.removeAll();
		guide.setLayout(new BoxLayout(guide, BoxLayout.Y_AXIS));
		guide.setBorder(new EmptyBorder(12, PAGE_GUTTER, 14, PAGE_GUTTER));
		pageHeading(guide, "HOW TO PLAY",
			"Raise companions through ordinary Old School play. Progress naturally, explore at your own pace and discover the rarer details along the way.");
		String[][] entries = {
			{"1 · START WITH AN EGG", "Choose an egg from the shop and incubate it from the Stash Tray. Your active egg is the one that receives Bonding XP."},
			{"2 · PLAY OLD SCHOOL", "Train skills, fight NPCs, complete quests and take on larger challenges. Every style of play can help your active egg or companion grow."},
			{"3 · WATCH IT HATCH", "The overlay and handheld show progress. When an egg is ready, interact with it to reveal its species and colour."},
			{"4 · RAISE YOUR COMPANION", "Keep earning Bonding XP after hatching to increase its level. Meaningful milestones and tougher adventures tend to feel more rewarding."},
			{"5 · CARE, PLAY & ITEMS", "Use A for care and memories, B for a quick game and C for the Toy Box. Wishes grow with your companion and reward Bonding XP, affection, personality and memories. You begin with three wish skips, which replenish through play up to the same cap."},
			{"6 · BUILD A COLLECTION", "Stash companions, collect colours, unlock scenes and review Hatch History. Selling is permanent, but the discovery remains recorded."},
			{"7 · MAKE IT YOURS", "Rename close companions, choose a backdrop and equip a favourite toy. Overlay display options are available in the plugin settings."},
			{"8 · LONG-TERM GOALS", "High-level and deeply bonded companions gain special recognition. Some rewards, memories and presentation details are best discovered through play."}
		};
		addPagedCards(guide, entries, guidePage, page -> { guidePage = page; rebuildGuide(); });
		guide.revalidate();
		guide.repaint();
	}

	private void rebuildRules()
	{
		rules.removeAll();
		rules.setLayout(new BoxLayout(rules, BoxLayout.Y_AXIS));
		rules.setBorder(new EmptyBorder(12, PAGE_GUTTER, 14, PAGE_GUTTER));
		pageHeading(rules, "RULES & EXPECTATIONS",
			"The essentials are explained here; exact rates, rare outcomes and a few surprises are intentionally left for discovery.");
		String[][] entries = {
			{"ONE ACTIVE JOURNEY", "Only the active egg or companion receives Bonding XP. Stashed companions are safe, but do not progress."},
			{"WHAT COUNTS", "Skill training, NPC defeats, quests and recognised challenges can contribute. Rewards vary by activity and favour meaningful play."},
			{"FAIR PROGRESS", "Repeated messages or duplicate events may be ignored. RuneLite also synchronises skill totals on login so old XP is not awarded again."},
			{"EGGS & HATCHES", "Egg tiers influence the kinds of outcomes you may discover. A ready egg must be hatched before that active journey can continue."},
			{"COSMETICS, NOT POWER", "Colours, toys, backdrops, affection and personality personalise your companion. They do not change Old School combat or skilling."},
			{"STASH & COLLECTION", "Stash space is limited and paginated. Collection entries and hatch records remain permanent discoveries even if a companion later leaves."},
			{"SELLING IS FINAL", "Selling returns Gotchi Points but permanently removes that companion. The interface asks for confirmation; this cannot be undone."},
			{"LOCAL PROFILE", "Progress is stored for the active RuneScape profile on this client. Back up RuneLite settings before moving machines or clearing local data."},
			{"PLAY YOUR WAY", "There is no required training method. Early journeys should move steadily, while prestigious eggs and max-level companions are longer-term goals."},
			{"KEEP SOME MYSTERY", "Exact reward formulas and the rarest combinations are not listed. The Rarities page offers broad guidance without spoiling every outcome."}
		};
		addPagedCards(rules, entries, rulesPage, page -> { rulesPage = page; rebuildRules(); });
		rules.revalidate();
		rules.repaint();
	}

	private void pageHeading(JPanel page, String heading, String introCopy)
	{
		JLabel title = new JLabel(heading);
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		title.setForeground(new Color(0xF2C45A));
		title.setAlignmentX(LEFT_ALIGNMENT);
		page.add(title);
		page.add(Box.createVerticalStrut(4));
		JTextArea intro = paragraph(introCopy, 14f);
		intro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
		page.add(intro);
		page.add(Box.createVerticalStrut(9));
	}

	private void addPagedCards(JPanel parent, String[][] entries, int requestedPage,
		java.util.function.IntConsumer changePage)
	{
		int perPage = 3;
		int pageCount = Math.max(1, (entries.length + perPage - 1) / perPage);
		int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
		for (int i = page * perPage; i < Math.min(entries.length, (page + 1) * perPage); i++)
		{
			parent.add(infoCard(entries[i][0], entries[i][1]));
			parent.add(Box.createVerticalStrut(6));
		}
		final int current = page;
		parent.add(pageControls("Page " + (page + 1) + " / " + pageCount,
			page > 0, page + 1 < pageCount,
			() -> changePage.accept(current - 1), () -> changePage.accept(current + 1)));
	}

	private JPanel pageControls(String label, boolean previousEnabled, boolean nextEnabled,
		Runnable previousAction, Runnable nextAction)
	{
		JPanel controls = new JPanel(new BorderLayout(6, 0));
		controls.setOpaque(false);
		JButton previous = styledButton("‹", 14f);
		previous.setEnabled(previousEnabled);
		previous.addActionListener(event -> previousAction.run());
		JLabel page = new JLabel(label, SwingConstants.CENTER);
		page.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
		page.setForeground(new Color(0xD8D8D8));
		JButton next = styledButton("›", 14f);
		next.setEnabled(nextEnabled);
		next.addActionListener(event -> nextAction.run());
		controls.add(previous, BorderLayout.WEST);
		controls.add(page, BorderLayout.CENTER);
		controls.add(next, BorderLayout.EAST);
		controls.setAlignmentX(LEFT_ALIGNMENT);
		controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return controls;
	}

	private JPanel infoCard(String heading, String copy)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(new Color(0x202224));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x5A513F)), new EmptyBorder(8, 8, 8, 8)));
		JLabel title = new JLabel(heading);
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
		title.setForeground(new Color(0xD7B867));
		JTextArea body = paragraph(copy, 13f);
		body.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
		card.add(title);
		card.add(Box.createVerticalStrut(3));
		card.add(body);
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
		return card;
	}

	private JPanel buildEggOddsCard(EggTier tier)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(new Color(0x202224));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x49443A)), new EmptyBorder(7, 7, 7, 7)));
		JLabel name = new JLabel(tier.getDisplayName().toUpperCase(Locale.ENGLISH) + " EGG");
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
		name.setForeground(new Color(0xF2C45A));
		JLabel meta = new JLabel(format(tier.getHatchXp()) + " Bonding XP · " + format(tier.getPrice()) + " GPts");
		meta.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		meta.setForeground(new Color(0xBFBFBF));
		double[] odds = tier.getSpeciesOdds();
		JLabel oddsLine = new JLabel("C " + odds[0] + "%  U " + odds[1] + "%  R " + odds[2] + "%");
		oddsLine.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		oddsLine.setForeground(new Color(0xDDDDDD));
		JLabel highLine = new JLabel("Epic " + odds[3] + "%  Legendary " + odds[4] + "%");
		highLine.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		highLine.setForeground(new Color(0xDDDDDD));
		card.add(name); card.add(meta); card.add(oddsLine); card.add(highLine);
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
		return card;
	}

	private JPanel rarityRow(Color colour, String name, String details)
	{
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setBackground(new Color(0x202224));
		JLabel swatch = new JLabel("◆", SwingConstants.CENTER);
		swatch.setForeground(colour);
		swatch.setPreferredSize(new Dimension(20, 27));
		JLabel copy = new JLabel(name + "  ·  " + details);
		copy.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		copy.setForeground(new Color(0xDDDDDD));
		row.add(swatch, BorderLayout.WEST);
		row.add(copy, BorderLayout.CENTER);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return row;
	}

	private void sectionLabel(JPanel parent, String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(new Color(0xD7B867));
		label.setAlignmentX(LEFT_ALIGNMENT);
		parent.add(label);
		parent.add(Box.createVerticalStrut(5));
	}

	private JTextArea paragraph(String copy, float size)
	{
		JTextArea text = new JTextArea(copy);
		text.setEditable(false);
		text.setOpaque(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(FontManager.getRunescapeSmallFont().deriveFont(size));
		text.setForeground(new Color(0xDDDDDD));
		text.setBorder(BorderFactory.createEmptyBorder());
		text.setAlignmentX(LEFT_ALIGNMENT);
		text.setMinimumSize(new Dimension(0, 0));
		return text;
	}

	private JTextArea fittedParagraph(String copy, float size, int width, int minimumHeight)
	{
		JTextArea text = paragraph(copy, size);
		text.setSize(new Dimension(width, Short.MAX_VALUE));
		int height = Math.max(minimumHeight, text.getPreferredSize().height);
		text.setMinimumSize(new Dimension(0, height));
		text.setPreferredSize(new Dimension(width, height));
		text.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return text;
	}

	private JButton collectionTile(PetDefinition pet, ProfileState state)
	{
		Discovery discovery = state == null ? null : state.getDiscoveries().get(pet.getId());
		JButton tile = styledButton(discovery == null ? "?" : "", 11f);
		tile.setToolTipText(pet.getName() + " — " + pet.getRarity().getDisplayName());
		tile.setPreferredSize(new Dimension(68, 74));
		tile.setFont(FontManager.getRunescapeBoldFont().deriveFont(22f));
		if (discovery != null)
		{
			Palette rarest = discovery.getPalettes().stream().max(Enum::compareTo).orElse(Palette.BASE);
			BufferedImage image = SpriteAssets.pet(pet.getId(), rarest.getAssetId());
			if (image != null) { tile.setIcon(new ImageIcon(SpriteAssets.resizeNearestShadowedOpaque(image, 58, 58))); }
		}
		tile.addActionListener(event ->
		{
			collectionHistoryVisible = false;
			collectionPetId = pet.getId();
			collectionPreviewPalette = null;
			rebuildCollection(stateService.getState());
		});
		return tile;
	}

	private String activeHint(ProfileState state)
	{
		if (state == null) { return "Log in to load your local Gieligotchi profile"; }
		if (state.getActiveEgg() != null)
		{
			EggState egg = state.getActiveEgg();
			return egg.isReady() ? "Left-click to hatch"
				: format(egg.getHatchXp()) + " / " + format(egg.getTargetXp()) + " Bonding XP · "
					+ Math.round(egg.getProgress() * 100) + "%";
		}
		if (state.getActiveCompanion() != null) { return "A Care  ·  B Play  ·  C Items"; }
		return "Choose something from your stash";
	}

	public void inspectActive()
	{
		ProfileState state = stateService.getState();
		if (state == null) { return; }
		EggState egg = state.getActiveEgg();
		if (egg != null)
		{
			if (egg.isReady()) { hatchAnimation.beginHatch(); }
			return;
		}
	}

	private static String format(long value) { return NumberFormat.getIntegerInstance(Locale.UK).format(value); }

	private final class TamagotchiDisplay extends JPanel
	{
		private final JButton[] controls = new JButton[3];

		TamagotchiDisplay()
		{
			setOpaque(false);
			setLayout(null);
			setPreferredSize(new Dimension(216, 225));
			setMaximumSize(new Dimension(216, 225));
			String[] tips = {"A · Care and memories", "B · Play", "C · Toy box"};
			for (int i = 0; i < controls.length; i++)
			{
				final int index = i;
				JButton control = new JButton();
				control.setOpaque(false);
				control.setContentAreaFilled(false);
				control.setBorderPainted(false);
				control.setFocusPainted(false);
				control.setFocusable(false);
				control.setToolTipText(tips[i]);
				control.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				control.getModel().addChangeListener(event ->
				{
					if (control.getModel().isRollover()) { handheldHoverButton = index; }
					else if (handheldHoverButton == index) { handheldHoverButton = -1; }
					if (control.getModel().isPressed()) { handheldPressedButton = index; }
					else if (handheldPressedButton == index) { handheldPressedButton = -1; }
					repaint();
				});
				control.addActionListener(event ->
				{
					String selected = index == 0 ? "care" : index == 1 ? "play" : "items";
					journeyMode = selected.equals(journeyMode) ? null : selected;
					refresh();
				});
				controls[i] = control;
				add(control);
			}
		}

		void setJourneyButtonsEnabled(boolean enabled)
		{
			for (JButton control : controls) { control.setEnabled(enabled); }
		}

		@Override public void doLayout()
		{
			double sx = getWidth() / 236d;
			double sy = getHeight() / 246d;
			for (int i = 0; i < controls.length; i++)
			{
				controls[i].setBounds((int) Math.round((58 + i * 51) * sx), (int) Math.round(203 * sy),
					(int) Math.round(36 * sx), (int) Math.round(31 * sy));
			}
		}

		int buttonAt(int screenX, int screenY)
		{
			double x = screenX * 236d / Math.max(1, getWidth());
			double y = screenY * 246d / Math.max(1, getHeight());
			for (int i = 0; i < 3; i++)
			{
				double centreX = 76 + i * 51;
				double centreY = 218;
				if (Math.pow((x - centreX) / 17d, 2) + Math.pow((y - centreY) / 13d, 2) <= 1d) { return i; }
			}
			return -1;
		}

		@Override protected void paintComponent(Graphics raw)
		{
			super.paintComponent(raw);
			Graphics2D g = (Graphics2D) raw.create();
			g.scale(getWidth() / 236d, getHeight() / 246d);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(SHELL_DARK); g.fillRoundRect(4, 4, 228, 238, 44, 44);
			g.setColor(SHELL); g.fillRoundRect(8, 8, 220, 230, 40, 40);
			g.setColor(new Color(0xE2BD76)); g.fillRoundRect(22, 23, 192, 176, 24, 24);
			g.setColor(LCD_DARK); g.fillRoundRect(31, 32, 174, 158, 14, 14);
			g.setColor(LCD); g.fillRoundRect(35, 36, 166, 150, 10, 10);
			ProfileState state = stateService.getState();
			BufferedImage backdrop = state == null ? null : SpriteAssets.backdrop(state.getEquippedBackdrop().getAssetId());
			if (backdrop != null)
			{
				Shape oldClip = g.getClip();
				g.clip(new RoundRectangle2D.Float(35, 36, 166, 150, 10, 10));
				SpriteAssets.drawNearest(g, backdrop, 35, 36, 166, 150);
				g.setColor(new Color(0, 0, 0, 18));
				g.fillRect(35, 36, 166, 150);
				g.setClip(oldClip);
			}
			else
			{
				g.setColor(new Color(39, 48, 31, 20));
				for (int x = 39; x < 198; x += 8) { g.drawLine(x, 40, x, 182); }
				for (int y = 40; y < 183; y += 8) { g.drawLine(39, y, 197, y); }
			}
			BufferedImage sprite = state == null ? null : state.getActiveEgg() != null
				? hatchAnimation.eggImage(state.getActiveEgg(), config.reducedMotion())
				: state.getActiveCompanion() != null ? SpriteAssets.companion(state.getActiveCompanion()) : null;
			if (sprite != null)
			{
				if (hatchAnimation.isCeremonyActive())
				{
					com.gieligotchi.model.HatchReceipt receipt = hatchAnimation.isRevealing() ? hatchAnimation.getLastReceipt() : null;
					Color glow = receipt == null ? new Color(0xC8B989) : RarityColours.palette(receipt.getPalette());
					Color border = receipt == null ? new Color(0x756A52) : RarityColours.species(receipt.getSpeciesRarity());
					double pulse = (Math.sin(System.currentTimeMillis() / 115d) + 1d) / 2d;
					for (int ring = 4; ring >= 0; ring--)
					{
						int radius = 35 + ring * 12 + (int) Math.round(pulse * 5);
						int alpha = hatchAnimation.isRevealing() ? 12 + (4 - ring) * 12 : 5 + (4 - ring) * 6;
						g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha));
						g.fillOval(118 - radius, 113 - radius, radius * 2, radius * 2);
					}
					g.setStroke(new BasicStroke(3f));
					g.setColor(border);
					g.drawRoundRect(31, 32, 174, 158, 14, 14);
				}
				CompanionInstance activeCompanion = state.getActiveCompanion();
				boolean playing = activeCompanion != null && System.currentTimeMillis() - activeCompanion.getLastToyPlayedAt() < 4_000L;
				double idleSpeed = activeCompanion != null && activeCompanion.getPersonality() == com.gieligotchi.model.CompanionPersonality.PLAYFUL ? 235d : 330d;
				int bob = state.getActiveEgg() != null || config.reducedMotion() ? 0
					: (int) Math.round(Math.sin(System.currentTimeMillis() / (playing ? 105d : idleSpeed)) * (playing ? 5 : 2));
				if (state.getActiveEgg() != null)
				{
					int eggWidth = 88;
					int eggHeight = 87;
					SpriteAssets.drawNearestOpaqueShadowed(g, sprite, (236 - eggWidth) / 2,
						39 + (145 - eggHeight) / 2, eggWidth, eggHeight, 2, 3);
				}
				else
				{
					int artSize = hatchAnimation.isRevealing() ? 146 : 134;
					SpriteAssets.drawNearestShadowed(g, sprite, (236 - artSize) / 2,
						46 + (134 - artSize) / 2 + bob, artSize, artSize, 2, 3);
				}
			}
			if (state != null && state.getActiveCompanion() != null && state.getEquippedToy() != null)
			{
				SpriteAssets.drawNearestOpaqueShadowed(g, SpriteAssets.toy(state.getEquippedToy()), 157, 132, 39, 39, 1, 2);
			}
			if (state != null && state.getActiveCompanion() != null) { effects.render(g, 35, 36, 166, 150, 1d); }
			if (state != null && state.getActiveCompanion() != null
				&& (state.getActiveCompanion().isLegacy() || state.getActiveCompanion().getAffectionHearts() >= 60))
			{
				g.setStroke(new BasicStroke(3f));
				g.setColor(state.getActiveCompanion().isLegacy() ? new Color(0xF2C45A) : new Color(0xDB85A9));
				g.drawRoundRect(31, 32, 174, 158, 14, 14);
			}
			for (int i = 0; i < 3; i++)
			{
				boolean selected = (i == 0 && "care".equals(journeyMode))
					|| (i == 1 && "play".equals(journeyMode)) || (i == 2 && "items".equals(journeyMode));
				boolean pressed = i == handheldPressedButton;
				boolean hovered = i == handheldHoverButton;
				int buttonX = 61 + i * 51;
				if (hovered && !selected)
				{
					g.setColor(new Color(0xF2C45A));
					g.fillOval(buttonX - 2, 205, 34, 26);
				}
				g.setColor(selected || pressed ? new Color(0x33291E) : SHELL_DARK);
				g.fillOval(buttonX, 207, 30, 22);
				int insetY = selected || pressed ? 212 : hovered ? 208 : 209;
				g.setColor(selected || pressed ? new Color(0xA77E43)
					: hovered ? new Color(0xF3D597) : new Color(0xE5C47F));
				g.fillOval(buttonX + 3, insetY, 24, selected || pressed ? 14 : 16);
				g.setFont(FontManager.getRunescapeBoldFont().deriveFont(10f));
				g.setColor(SHELL_DARK);
				String label = i == 0 ? "A" : i == 1 ? "B" : "C";
				g.drawString(label, 73 + i * 51, selected || pressed ? 223 : 221);
			}
			g.dispose();
		}
	}

	private final class BootPanel extends JPanel
	{
		private int stage;
		BootPanel() { setOpaque(false); }
		void play(Runnable finished)
		{
			stage = 0;
			Timer intro = new Timer(360, null);
			intro.addActionListener(event ->
			{
				stage++;
				repaint();
				if (stage >= 7) { intro.stop(); finished.run(); }
			});
			intro.start();
		}
		@Override protected void paintComponent(Graphics raw)
		{
			super.paintComponent(raw);
			Graphics2D g = (Graphics2D) raw.create();
			int width = getWidth();
			g.setColor(new Color(0x1A1C19));
			g.fillRect(0, 0, width, getHeight());
			g.setColor(LCD);
			g.fillRoundRect(14, 42, Math.max(1, width - 28), 255, 16, 16);
			g.setColor(LCD_DARK);
			g.drawRoundRect(18, 46, Math.max(1, width - 37), 246, 12, 12);
			g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 19));
			String shown = "GIELIGOTCHI".substring(0, Math.min("GIELIGOTCHI".length(), stage * 2));
			int textWidth = g.getFontMetrics().stringWidth(shown);
			g.drawString(shown, Math.max(20, (width - textWidth) / 2), 92);
			if (stage >= 2)
			{
				BufferedImage egg = SpriteAssets.egg(com.gieligotchi.model.EggTier.COMMON);
				int wobble = stage % 2 == 0 ? -2 : 2;
				SpriteAssets.drawNearestOpaqueShadowed(g, egg, (width - 116) / 2 + wobble, 112, 116, 116, 2, 3);
			}
			g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
			String dots = stage < 3 ? "" : stage < 5 ? "." : stage < 6 ? ".." : "...";
			g.drawString("A TINY LIFE AWAKENS" + dots, Math.max(19, (width - 176) / 2), 264);
			g.dispose();
		}
	}

	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) { return 16; }
		@Override public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) { return Math.max(16, visibleRect.height - 16); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}
}
