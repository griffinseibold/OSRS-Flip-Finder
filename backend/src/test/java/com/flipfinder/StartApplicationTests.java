package com.flipfinder;

import com.flipfinder.repository.ItemRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "flipfinder.scheduling.enabled=false")
@AutoConfigureMockMvc
class StartApplicationTests {
	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearItems() {
		jdbc.update("DELETE FROM items");
	}

	@Test
	void schemaIsCreatedInMemory() {
		assertThat(itemRepository.countAll()).isZero();
	}

	@Test
	void openApiDocumentsTheEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/items']").exists())
				.andExpect(jsonPath("$.paths['/api/flips']").exists());
	}

	@Test
	void flipsAreSortedAndPricedAfterTax() throws Exception {
		String insert = """
				INSERT INTO items (
				  id, name, members, buy_limit, high_price, high_price_time, low_price, low_price_time,
				  high_price_volume_5m, low_price_volume_5m, updated_at
				) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, '2026-09-27T15:00:00Z')
				""";
		jdbc.update(insert, 1, "Gold leaf", 11000, 141749, 1790522991, 135479, 1790522975, 30, 29);
		jdbc.update(insert, 2, "Thin snail", 13000, 3243, 1790522991, 1412, 1790522975, 23, 10);

		mockMvc.perform(get("/api/flips").param("sort", "margin").param("direction", "asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].item.name").value("Thin snail"))
				.andExpect(jsonPath("$.items[0].tax").value(64))
				.andExpect(jsonPath("$.items[0].margin").value(1767))
				.andExpect(jsonPath("$.items[0].fillableQuantity").value(480))
				.andExpect(jsonPath("$.items[0].limitedBy").value("volume"))
				.andExpect(jsonPath("$.items[0].estimatedProfit").value(1767 * 480))
				.andExpect(jsonPath("$.items[1].item.name").value("Gold leaf"))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.profitable").value(2))
				.andExpect(jsonPath("$.topFlip.item.name").value("Gold leaf"));
	}

	@Test
	void budgetHidesFlipsThatCostMore() throws Exception {
		String insert = """
				INSERT INTO items (
				  id, name, members, buy_limit, high_price, low_price,
				  high_price_volume_5m, low_price_volume_5m, updated_at
				) VALUES (?, ?, 1, 8, ?, ?, 5, 5, '2026-09-27T15:00:00Z')
				""";
		jdbc.update(insert, 1, "Affordable", 9_500_000, 9_000_000);
		jdbc.update(insert, 2, "Too dear", 12_500_000, 12_000_000);

		mockMvc.perform(get("/api/flips").param("budget", "10000000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].item.name").value("Affordable"))
				.andExpect(jsonPath("$.items[0].quantity").value(1))
				.andExpect(jsonPath("$.items[0].limitedBy").value("budget"));
	}

	@Test
	void runeLiteEndpointsAreOffOutsideTheHomelab() throws Exception {
		mockMvc.perform(get("/api/runelite/accounts"))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownFlipSortIsRejected() throws Exception {
		mockMvc.perform(get("/api/flips").param("sort", "bogus"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void itemsAreReturnedAsTypedFieldsWithoutRawJson() throws Exception {
		jdbc.update("""
				INSERT INTO items (
				  id, name, examine, members, low_alchemy, high_alchemy,
				  buy_limit, value, icon, high_price, high_price_time,
				  low_price, low_price_time, average_high_price_5m,
				  high_price_volume_5m, average_low_price_5m,
				  low_price_volume_5m, five_minute_timestamp, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				10, "Cannon barrels", "The barrels of the multicannon.", 1,
				75000, 112500, 70, 187500, "Cannon barrels.png",
				164136, 1790522991, 162000, 1790522975, 164100.5,
				14, 162000.0, 9, 1790522700, "2026-09-27T15:00:00Z");

		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(10))
				.andExpect(jsonPath("$.items[0].name").value("Cannon barrels"))
				.andExpect(jsonPath("$.items[0].members").value(true))
				.andExpect(jsonPath("$.items[0].highAlchemy").value(112500))
				.andExpect(jsonPath("$.items[0].buyLimit").value(70))
				.andExpect(jsonPath("$.items[0].highPrice").value(164136))
				.andExpect(jsonPath("$.items[0].lowPrice").value(162000))
				.andExpect(jsonPath("$.items[0].averageHighPrice5m").value(164100.5))
				.andExpect(jsonPath("$.items[0].highPriceVolume5m").value(14))
				.andExpect(jsonPath("$.items[0].data").doesNotExist());
	}

	@Test
	void itemsWithoutPricesReturnNullFields() throws Exception {
		jdbc.update("""
				INSERT INTO items (id, name, members, updated_at)
				VALUES (?, ?, ?, ?)
				""",
				2, "Steel cannonball", 1, "2026-09-27T15:00:00Z");

		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].name").value("Steel cannonball"))
				.andExpect(jsonPath("$.items[0].buyLimit").isEmpty())
				.andExpect(jsonPath("$.items[0].highPrice").isEmpty())
				.andExpect(jsonPath("$.items[0].averageLowPrice5m").isEmpty());
	}

	@Test
	void livenessProbeIsAvailable() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
