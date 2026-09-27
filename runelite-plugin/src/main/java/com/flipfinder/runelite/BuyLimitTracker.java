package com.flipfinder.runelite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Counts how many of each item were bought in its current buy limit window.
 * A window starts with the first purchase of an item and lasts four hours,
 * whatever is bought after that
 * (https://oldschool.runescape.wiki/w/Grand_Exchange/Buying_limits).
 */
class BuyLimitTracker
{
	static final long WINDOW_SECONDS = 4 * 60 * 60;

	private final State state;

	BuyLimitTracker()
	{
		this(new State());
	}

	BuyLimitTracker(State state)
	{
		this.state = state;
	}

	/**
	 * Records a buy offer's progress. Only the increase since the offer was last
	 * seen counts, so offers already counted in an earlier session add nothing
	 * when the client reports every slot again at login.
	 */
	void onBuyOffer(int slot, int itemId, int totalQuantity, int price, int quantityBought, long now)
	{
		SlotOffer previous = state.slots.get(slot);
		boolean sameOffer = previous != null
			&& previous.itemId == itemId
			&& previous.totalQuantity == totalQuantity
			&& previous.price == price
			&& previous.quantityBought <= quantityBought;
		int newlyBought = sameOffer ? quantityBought - previous.quantityBought : quantityBought;

		state.slots.put(slot, new SlotOffer(itemId, totalQuantity, price, quantityBought));
		if (newlyBought > 0)
		{
			record(itemId, newlyBought, now);
		}
	}

	/** Forgets a slot once its offer has been collected or cancelled. */
	void onEmptySlot(int slot)
	{
		state.slots.remove(slot);
	}

	void record(int itemId, int quantity, long now)
	{
		Window window = state.windows.get(itemId);
		if (window == null || now - window.startedAt >= WINDOW_SECONDS)
		{
			window = new Window(itemId, now, 0);
			state.windows.put(itemId, window);
		}
		window.bought += quantity;
	}

	/** Windows still running at {@code now}; expired ones are dropped. */
	List<Window> activeWindows(long now)
	{
		state.windows.values().removeIf(window -> now - window.startedAt >= WINDOW_SECONDS);
		return new ArrayList<>(state.windows.values());
	}

	State getState()
	{
		return state;
	}

	/** Everything the tracker needs to survive a client restart, saved as JSON. */
	@Data
	@NoArgsConstructor
	static class State
	{
		private Map<Integer, SlotOffer> slots = new HashMap<>();
		private Map<Integer, Window> windows = new HashMap<>();
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class SlotOffer
	{
		private int itemId;
		private int totalQuantity;
		private int price;
		private int quantityBought;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Window
	{
		private int itemId;
		/** Unix seconds of the first purchase in the window. */
		private long startedAt;
		private int bought;
	}
}
