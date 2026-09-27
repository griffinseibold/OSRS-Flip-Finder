package com.flipfinder.runelite;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Flip Finder",
	description = "Sends your membership, coins and Grand Exchange buy limits to your Flip Finder server",
	tags = {"grand exchange", "flipping", "merching", "ge"}
)
public class FlipFinderPlugin extends Plugin
{
	static final String CONFIG_GROUP = "flipfinder";
	private static final String BUY_LIMITS_KEY = "buyLimits";
	private static final String BANK_COINS_KEY = "bankCoins";
	private static final String BANK_COINS_SEEN_AT_KEY = "bankCoinsSeenAt";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	// Unchanged data is still resent this often, so the server knows it is current.
	private static final long RESEND_SECONDS = 5 * 60;

	@Inject
	private Client client;

	@Inject
	private FlipFinderConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	// State for the RuneScape profile in loadedProfile, saved in its profile config.
	private String loadedProfile;
	private BuyLimitTracker buyLimits;
	private Long bankCoins;
	private Long bankCoinsSeenAt;

	// Written from OkHttp's threads.
	private volatile boolean sending;
	private volatile String lastSent;
	private volatile long lastSentAt;

	@Override
	protected void startUp()
	{
		loadedProfile = null;
		lastSent = null;
	}

	@Override
	protected void shutDown()
	{
		loadedProfile = null;
		buyLimits = null;
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		GrandExchangeOfferState state = offer.getState();
		if (!loadProfile())
		{
			return;
		}

		if (state == GrandExchangeOfferState.EMPTY)
		{
			// The client clears every slot while logging in or hopping; only
			// believe an empty slot once logged in.
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				buyLimits.onEmptySlot(event.getSlot());
				saveBuyLimits();
			}
			return;
		}

		if (state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY)
		{
			buyLimits.onBuyOffer(event.getSlot(), offer.getItemId(), offer.getTotalQuantity(), offer.getPrice(),
				offer.getQuantitySold(), now());
			saveBuyLimits();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK || !loadProfile())
		{
			return;
		}
		// The bank is only readable while open, so remember what it last held.
		bankCoins = Coins.count(event.getItemContainer().getItems());
		bankCoinsSeenAt = now();
		configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_COINS_KEY, bankCoins);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_COINS_SEEN_AT_KEY, bankCoinsSeenAt);
	}

	/** Sends the account's data when it has changed. Scheduled tasks run on the client thread. */
	@Schedule(period = 10, unit = ChronoUnit.SECONDS)
	public void report()
	{
		if (!config.sendAccountData() || sending || client.getGameState() != GameState.LOGGED_IN || !loadProfile())
		{
			return;
		}
		long accountHash = client.getAccountHash();
		HttpUrl url = HttpUrl.parse(config.serverUrl().trim());
		if (accountHash == -1 || url == null)
		{
			return;
		}

		AccountSnapshot snapshot = snapshot();
		String content = gson.toJson(snapshot);
		long now = now();
		if (content.equals(lastSent) && now - lastSentAt < RESEND_SECONDS)
		{
			return;
		}
		snapshot.setCapturedAt(now);

		Request request = new Request.Builder()
			.url(url.newBuilder()
				.addPathSegments("api/runelite/accounts")
				.addPathSegment(Long.toString(accountHash))
				.build())
			.put(RequestBody.create(JSON, gson.toJson(snapshot)))
			.build();
		sending = true;
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				sending = false;
				log.debug("Could not reach the Flip Finder server at {}", url, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (response.isSuccessful())
					{
						lastSent = content;
						lastSentAt = now;
					}
					else
					{
						log.debug("The Flip Finder server rejected account data: HTTP {}", response.code());
					}
				}
				finally
				{
					sending = false;
				}
			}
		});
	}

	/** Everything sent to the server except the capture time. */
	private AccountSnapshot snapshot()
	{
		AccountSnapshot snapshot = new AccountSnapshot();
		Player player = client.getLocalPlayer();
		snapshot.setDisplayName(player == null ? null : player.getName());
		int membershipDays = client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT);
		snapshot.setMembers(membershipDays > 0);
		snapshot.setMembershipDays(membershipDays);
		snapshot.setIronman(client.getVarbitValue(VarbitID.IRONMAN) != 0);

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		snapshot.setInventoryCoins(inventory == null ? 0 : Coins.count(inventory.getItems()));
		snapshot.setBankCoins(bankCoins);
		snapshot.setBankCoinsSeenAt(bankCoinsSeenAt);

		List<AccountSnapshot.Offer> offers = new ArrayList<>();
		GrandExchangeOffer[] slots = client.getGrandExchangeOffers();
		for (int slot = 0; slot < slots.length; slot++)
		{
			GrandExchangeOffer offer = slots[slot];
			if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				offers.add(new AccountSnapshot.Offer(slot, offer.getItemId(), offer.getState().name(), offer.getPrice(),
					offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent()));
			}
		}
		snapshot.setGeOffers(offers);
		snapshot.setBuyLimits(buyLimits.activeWindows(now()));
		return snapshot;
	}

	/** Loads the saved state of the current RuneScape profile, if there is one yet. */
	private boolean loadProfile()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return false;
		}
		if (!profile.equals(loadedProfile))
		{
			String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, BUY_LIMITS_KEY);
			BuyLimitTracker.State state = saved == null ? null : gson.fromJson(saved, BuyLimitTracker.State.class);
			buyLimits = state == null ? new BuyLimitTracker() : new BuyLimitTracker(state);
			bankCoins = configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_COINS_KEY, Long.class);
			bankCoinsSeenAt = configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_COINS_SEEN_AT_KEY, Long.class);
			loadedProfile = profile;
			lastSent = null;
		}
		return true;
	}

	private void saveBuyLimits()
	{
		configManager.setRSProfileConfiguration(CONFIG_GROUP, BUY_LIMITS_KEY, gson.toJson(buyLimits.getState()));
	}

	private static long now()
	{
		return Instant.now().getEpochSecond();
	}

	@Provides
	FlipFinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlipFinderConfig.class);
	}
}
