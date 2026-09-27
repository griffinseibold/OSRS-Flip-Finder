package com.flipfinder.runelite;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the plugin sends to the Flip Finder server about the logged-in account. */
@Data
@NoArgsConstructor
class AccountSnapshot
{
	private String displayName;
	private boolean members;
	private int membershipDays;
	/** Ironman accounts cannot use the Grand Exchange. */
	private boolean ironman;
	/** Coins and platinum tokens carried, in coins. */
	private long inventoryCoins;
	/** Coins and platinum tokens in the bank; null until the bank is opened with the plugin on. */
	private Long bankCoins;
	/** Unix seconds when the bank was last seen. */
	private Long bankCoinsSeenAt;
	private List<Offer> geOffers;
	/** Items bought in buy limit windows that are still running. */
	private List<BuyLimitTracker.Window> buyLimits;
	/** Unix seconds. */
	private long capturedAt;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Offer
	{
		private int slot;
		private int itemId;
		/** A net.runelite.api.GrandExchangeOfferState name, such as BUYING or SOLD. */
		private String state;
		private int price;
		private int totalQuantity;
		/** Items bought or sold so far. */
		private int quantityTraded;
		/** Coins spent on a buy offer, or received from a sell offer. */
		private int spent;
	}
}
