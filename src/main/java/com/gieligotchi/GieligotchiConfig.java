package com.gieligotchi;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GieligotchiConfig.GROUP)
public interface GieligotchiConfig extends Config
{
	String GROUP = "gieligotchi";

	@ConfigItem(keyName = "showOverlay", name = "Show companion overlay",
		description = "Show the active egg or companion in the game view")
	default boolean showOverlay() { return true; }

	@ConfigItem(keyName = "showBackdropInOverlay", name = "Show backdrop in game overlay",
		description = "Show your equipped cosmetic backdrop in the game overlay; the sidebar always uses it",
		position = 1)
	default boolean showBackdropInOverlay() { return false; }

	@ConfigItem(keyName = "showToyInOverlay", name = "Show toy in game overlay",
		description = "Show your equipped toy in the game overlay; the sidebar always uses it",
		position = 2)
	default boolean showToyInOverlay() { return false; }

	@ConfigItem(keyName = "unlockOverlay", name = "Unlock overlay movement",
		description = "Allow the Gieligotchi overlay to be dragged", position = 3)
	default boolean unlockOverlay() { return false; }

	@Range(min = 50, max = 250)
	@ConfigItem(keyName = "overlayScale", name = "Overlay size",
		description = "Scale the pixel-art overlay from 50% to 250%; 100% is the compact default", position = 4)
	default int overlayScale() { return 100; }

	@ConfigItem(keyName = "reducedMotion", name = "Reduced motion",
		description = "Hold the first animation frame and disable wandering", position = 5)
	default boolean reducedMotion() { return false; }
}
