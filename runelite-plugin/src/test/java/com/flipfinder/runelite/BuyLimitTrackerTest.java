package com.flipfinder.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

public class BuyLimitTrackerTest
{
	private static final int GOLD_LEAF = 8784;
	private static final int THIN_SNAIL = 3363;
	private static final long START = 1_790_520_000L;

	@Test
	public void countsOnlyNewFillsOfTheSameOffer()
	{
		BuyLimitTracker tracker = new BuyLimitTracker();

		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 30, START);
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 30, START + 60);
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 100, START + 120);

		List<BuyLimitTracker.Window> windows = tracker.activeWindows(START + 180);
		assertEquals(1, windows.size());
		assertEquals(100, windows.get(0).getBought());
		assertEquals(START, windows.get(0).getStartedAt());
	}

	@Test
	public void addsEveryOfferOfAnItemToOneWindow()
	{
		BuyLimitTracker tracker = new BuyLimitTracker();

		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 100, START);
		tracker.onBuyOffer(1, GOLD_LEAF, 50, 136_000, 20, START + 3600);

		assertEquals(120, tracker.activeWindows(START + 3600).get(0).getBought());
	}

	@Test
	public void windowResetsFourHoursAfterTheFirstPurchase()
	{
		BuyLimitTracker tracker = new BuyLimitTracker();
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 100, START);

		assertTrue(tracker.activeWindows(START + BuyLimitTracker.WINDOW_SECONDS).isEmpty());

		tracker.onBuyOffer(1, GOLD_LEAF, 40, 135_000, 40, START + BuyLimitTracker.WINDOW_SECONDS + 10);
		BuyLimitTracker.Window window = tracker.activeWindows(START + BuyLimitTracker.WINDOW_SECONDS + 20).get(0);
		assertEquals(40, window.getBought());
		assertEquals(START + BuyLimitTracker.WINDOW_SECONDS + 10, window.getStartedAt());
	}

	@Test
	public void aNewOfferInAReusedSlotCountsFromZero()
	{
		BuyLimitTracker tracker = new BuyLimitTracker();
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 100, START);
		tracker.onEmptySlot(0);

		tracker.onBuyOffer(0, THIN_SNAIL, 500, 1_400, 200, START + 60);

		assertEquals(200, window(tracker, THIN_SNAIL).getBought());
		assertEquals(100, window(tracker, GOLD_LEAF).getBought());
	}

	@Test
	public void anIdenticalOfferThatRestartedCountsAsNew()
	{
		BuyLimitTracker tracker = new BuyLimitTracker();
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 100, START);

		// Same item, quantity and price, but fewer bought: a fresh offer.
		tracker.onBuyOffer(0, GOLD_LEAF, 100, 135_000, 25, START + 60);

		assertEquals(125, window(tracker, GOLD_LEAF).getBought());
	}

	@Test
	public void survivesASaveAndReloadWithoutRecountingAtLogin()
	{
		Gson gson = new Gson();
		BuyLimitTracker tracker = new BuyLimitTracker();
		tracker.onBuyOffer(3, GOLD_LEAF, 100, 135_000, 60, START);

		BuyLimitTracker reloaded = new BuyLimitTracker(
			gson.fromJson(gson.toJson(tracker.getState()), BuyLimitTracker.State.class));
		// At login the client reports every slot again, plus anything bought while logged out.
		reloaded.onBuyOffer(3, GOLD_LEAF, 100, 135_000, 70, START + 600);

		assertEquals(70, window(reloaded, GOLD_LEAF).getBought());
	}

	private static BuyLimitTracker.Window window(BuyLimitTracker tracker, int itemId)
	{
		return tracker.activeWindows(START + 120).stream()
			.filter(window -> window.getItemId() == itemId)
			.findFirst()
			.orElseThrow(AssertionError::new);
	}
}
