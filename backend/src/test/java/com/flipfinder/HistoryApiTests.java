package com.flipfinder;

import static com.flipfinder.repository.PriceHistoryRepository.FIVE_MINUTES;
import static com.flipfinder.repository.PriceHistoryRepository.HOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flipfinder.repository.PriceHistoryRepository;
import com.flipfinder.repository.PriceHistoryRepository.Row;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "flipfinder.scheduling.enabled=false", "flipfinder.history.enabled=true" })
@AutoConfigureMockMvc
class HistoryApiTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PriceHistoryRepository history;

	private long latest;

	@BeforeEach
	void storeADayOfTrading() {
		jdbc.update("DELETE FROM items");
		jdbc.update("DELETE FROM price_history");
		jdbc.update("DELETE FROM price_history_buckets");
		jdbc.update("""
				INSERT INTO items (id, name, members, buy_limit, high_price, low_price, updated_at)
				VALUES (8784, 'Gold leaf', 1, 11000, 141749, 135479, '2026-09-27T15:00:00Z')
				""");

		// Gold leaf trades 100 every five minutes, then 1,000 in the latest five; a cabbage
		// keeps every bucket busy, and Gold leaf skips one.
		latest = Instant.now().getEpochSecond() / FIVE_MINUTES * FIVE_MINUTES - FIVE_MINUTES;
		for (long bucket = latest - 86_400 + FIVE_MINUTES; bucket <= latest; bucket += FIVE_MINUTES) {
			Row cabbage = new Row(1965, 60L, 500, 50L, 500);
			Row leaf = new Row(8784, 141_000L, bucket == latest ? 500 : 50, 136_000L, bucket == latest ? 500 : 50);
			history.saveBucket(FIVE_MINUTES, bucket, bucket == latest - 3600 ? List.of(cabbage) : List.of(cabbage, leaf));
		}
		history.saveBucket(HOUR, latest - 40 * 86_400, List.of(new Row(8784, 1L, 1, 1L, 1)));
	}

	@Test
	void comparesTheLatestTradingWithTheStoredHistory() throws Exception {
		mockMvc.perform(get("/api/items/8784/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Gold leaf"))
				.andExpect(jsonPath("$.fiveMinuteHours").value(24.0))
				.andExpect(jsonPath("$.hourlyDays").value(0.0))
				.andExpect(jsonPath("$.volume.latestBucket").value(latest))
				.andExpect(jsonPath("$.volume.last5m").value(1000))
				.andExpect(jsonPath("$.volume.typical5m").value(100.0))
				.andExpect(jsonPath("$.volume.ratio").value(10.0))
				.andExpect(jsonPath("$.volume.verdict").value("unusually high"))
				.andExpect(jsonPath("$.margin.current").value(6270))
				.andExpect(jsonPath("$.margin.typical").value(5000.0))
				.andExpect(jsonPath("$.margin.verdict").value("typical"))
				.andExpect(jsonPath("$.notes[0]").value(org.hamcrest.Matchers.startsWith(
						"1,000 traded in the latest five minutes, against a typical 100 over the last 24 hours")));
	}

	@Test
	void prunesBucketsOlderThanItKeeps() {
		assertThat(history.prune(HOUR, latest - 30 * 86_400)).isEqualTo(1);
		assertThat(history.importedBuckets(HOUR, 0)).isEmpty();
		assertThat(history.importedBuckets(FIVE_MINUTES, 0)).hasSize(288);
		assertThat(history.tradedBuckets(FIVE_MINUTES, latest)).containsExactly(latest);
	}

	@Test
	void reportsAnUnknownItem() throws Exception {
		mockMvc.perform(get("/api/items/1/history")).andExpect(status().isNotFound());
	}

	@Test
	void listsHistoryAsAFeature() throws Exception {
		mockMvc.perform(get("/api/features")).andExpect(jsonPath("$.history").value(true));
	}
}
