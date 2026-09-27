package com.flipfinder.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite with this plugin loaded; ./gradlew run calls this. */
public class FlipFinderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FlipFinderPlugin.class);
		RuneLite.main(args);
	}
}
