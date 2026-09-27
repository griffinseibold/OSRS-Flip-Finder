package com.flipfinder.runelite;

import static org.junit.Assert.assertEquals;

import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class CoinsTest
{
	@Test
	public void countsCoinsAndPlatinumTokens()
	{
		Item[] items = {
			new Item(ItemID.COINS, 1_234_567),
			new Item(ItemID.PLATINUM, 3_000),
			new Item(ItemID.COINS, 10),
			new Item(-1, 0),
		};

		assertEquals(1_234_577 + 3_000_000L, Coins.count(items));
	}

	@Test
	public void handlesMoreThanAMaxStackOfCoins()
	{
		Item[] items = {new Item(ItemID.COINS, Integer.MAX_VALUE), new Item(ItemID.PLATINUM, Integer.MAX_VALUE)};

		assertEquals(Integer.MAX_VALUE + Integer.MAX_VALUE * 1_000L, Coins.count(items));
	}
}
