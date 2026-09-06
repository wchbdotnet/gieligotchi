package com.gieligotchi;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GieligotchiPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GieligotchiPlugin.class);
		RuneLite.main(args);
	}
}
