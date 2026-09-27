package com.flipfinder.runelite;

import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;

final class Coins
{
	static final long PLATINUM_TOKEN_VALUE = 1_000;

	private Coins()
	{
	}

	/** Coins among the items, counting each platinum token as 1,000 coins. */
	static long count(Item[] items)
	{
		long coins = 0;
		for (Item item : items)
		{
			if (item.getId() == ItemID.COINS)
			{
				coins += item.getQuantity();
			}
			else if (item.getId() == ItemID.PLATINUM)
			{
				coins += item.getQuantity() * PLATINUM_TOKEN_VALUE;
			}
		}
		return coins;
	}
}
