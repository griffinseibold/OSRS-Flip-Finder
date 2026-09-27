package com.flipfinder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "flipfinder.scheduling.enabled=false", "flipfinder.runelite.enabled=true" })
@AutoConfigureMockMvc
class RuneLiteApiTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearData() {
		jdbc.update("DELETE FROM items");
		jdbc.update("DELETE FROM runelite_accounts");
		String insert = """
				INSERT INTO items (
				  id, name, members, buy_limit, high_price, high_price_time, low_price, low_price_time,
				  high_price_volume_5m, low_price_volume_5m, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-09-27T15:00:00Z')
				""";
		long now = Instant.now().getEpochSecond();
		jdbc.update(insert, 8784, "Gold leaf", 1, 11000, 141749, now, 135479, now, 30, 29);
		jdbc.update(insert, 1965, "Cabbage", 0, 13000, 60, now, 50, now, 500, 500);
	}

	private void report(long accountHash, boolean members, long boughtAt) throws Exception {
		String snapshot = """
				{
				  "displayName": "Flipper",
				  "members": %s,
				  "membershipDays": 30,
				  "ironman": false,
				  "inventoryCoins": 1000,
				  "bankCoins": 9000000,
				  "bankCoinsSeenAt": %d,
				  "geOffers": [
				    {"slot": 0, "itemId": 8784, "state": "BUYING", "price": 135479,
				     "totalQuantity": 100, "quantityTraded": 60, "spent": 8128740}
				  ],
				  "buyLimits": [{"itemId": 8784, "startedAt": %d, "bought": 60}],
				  "capturedAt": %d
				}
				""".formatted(members, boughtAt, boughtAt, boughtAt);
		mockMvc.perform(put("/api/runelite/accounts/{hash}", accountHash)
				.contentType(MediaType.APPLICATION_JSON)
				.content(snapshot))
				.andExpect(status().isNoContent());
	}

	@Test
	void storesAndDescribesAnAccount() throws Exception {
		long boughtAt = Instant.now().getEpochSecond() - 600;
		report(42, true, boughtAt);

		mockMvc.perform(get("/api/runelite/accounts/42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Flipper"))
				.andExpect(jsonPath("$.coins").value(9_001_000))
				.andExpect(jsonPath("$.geOffers[0].name").value("Gold leaf"))
				.andExpect(jsonPath("$.buyLimits[0].name").value("Gold leaf"))
				.andExpect(jsonPath("$.buyLimits[0].limit").value(11000))
				.andExpect(jsonPath("$.buyLimits[0].remaining").value(10940))
				.andExpect(jsonPath("$.buyLimits[0].resetsAt").value(boughtAt + 4 * 60 * 60));

		mockMvc.perform(get("/api/runelite/accounts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].accountHash").value(42));
	}

	@Test
	void reportingAgainReplacesTheAccount() throws Exception {
		long now = Instant.now().getEpochSecond();
		report(42, true, now - 600);
		report(42, false, now - 300);

		mockMvc.perform(get("/api/runelite/accounts"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].members").value(false));
	}

	@Test
	void flipsUseTheAccount() throws Exception {
		report(42, false, Instant.now().getEpochSecond() - 600);

		mockMvc.perform(get("/api/flips").param("account", "42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").value(9_001_000))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].item.name").value("Cabbage"));
	}

	@Test
	void flipsShowWhatIsLeftOfABuyLimit() throws Exception {
		report(42, true, Instant.now().getEpochSecond() - 600);

		mockMvc.perform(get("/api/flips").param("account", "42").param("search", "gold"))
				.andExpect(jsonPath("$.items[0].alreadyBought").value(60))
				.andExpect(jsonPath("$.items[0].quantity").value(66));
	}

	@Test
	void unknownAccountsAreNotFound() throws Exception {
		mockMvc.perform(get("/api/runelite/accounts/7")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/flips").param("account", "7")).andExpect(status().isNotFound());
	}
}
