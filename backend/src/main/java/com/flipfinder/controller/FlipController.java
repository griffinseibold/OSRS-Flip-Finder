package com.flipfinder.controller;

import com.flipfinder.dto.FlipPageResponse;
import com.flipfinder.service.FlipQuery;
import com.flipfinder.service.FlipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Flips", description = "Grand Exchange items ranked by what flipping them could make or lose")
public class FlipController {
	private final FlipService flips;

	public FlipController(FlipService flips) {
		this.flips = flips;
	}

	@GetMapping("/api/flips")
	@Operation(summary = "Rank flips", description = """
			Prices every item as a flip (buy at the low price, sell at the high price, pay Grand Exchange \
			tax), filters and sorts the results, and returns one page. The estimated profit caps the \
			quantity at what could trade in four hours at the pace of the latest five-minute window.""")
	public FlipPageResponse list(
			@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, at most 200") @RequestParam(defaultValue = "25") int size,
			@RequestParam(defaultValue = "estimatedProfit") FlipQuery.Sort sort,
			@Parameter(description = "Defaults to desc, or asc when sorting by name")
			@RequestParam(required = false) FlipQuery.Direction direction,
			@Parameter(description = "Price from the latest trades or the five-minute averages")
			@RequestParam(defaultValue = "latest") FlipQuery.PriceBasis basis,
			@Parameter(description = "Case-insensitive part of the item name") @RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "all") FlipQuery.Membership membership,
			@Parameter(description = "Both sides must have traded within this many minutes; 0 for any time. Ignored for five-minute averages.")
			@RequestParam(defaultValue = "0") int maxTradeAgeMinutes,
			@Parameter(description = "Minimum items traded in the latest five-minute window, both prices combined")
			@RequestParam(defaultValue = "0") long minVolume5m,
			@Parameter(description = "Coins available to flip with: hides items that cost more and limits quantity to what it buys")
			@RequestParam(required = false) Long budget,
			@Parameter(description = "Account hash from the RuneLite plugin. Uses the account's coins as the budget, "
					+ "its membership, and what it already bought in each buy limit window.")
			@RequestParam(required = false) Long account) {
		FlipQuery query = new FlipQuery(
				basis, search, membership, maxTradeAgeMinutes, minVolume5m, budget, sort, direction, account);
		return flips.find(query, page, size);
	}
}
