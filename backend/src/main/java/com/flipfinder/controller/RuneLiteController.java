package com.flipfinder.controller;

import com.flipfinder.dto.AccountDto;
import com.flipfinder.dto.RuneLiteSnapshot;
import com.flipfinder.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Account data from the Flip Finder RuneLite plugin. The homelab profile enables it. */
@RestController
@ConditionalOnProperty(name = "flipfinder.runelite.enabled", havingValue = "true")
@Tag(name = "RuneLite", description = "Account data sent by the Flip Finder RuneLite plugin")
public class RuneLiteController {
	private final AccountService accounts;

	public RuneLiteController(AccountService accounts) {
		this.accounts = accounts;
	}

	@PutMapping("/api/runelite/accounts/{accountHash}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Report an account", description = "Replaces the account's latest data. The RuneLite plugin calls this.")
	public void report(
			@Parameter(description = "RuneLite's hash of the account") @PathVariable long accountHash,
			@RequestBody RuneLiteSnapshot snapshot) {
		accounts.save(accountHash, snapshot);
	}

	@GetMapping("/api/runelite/accounts")
	@Operation(summary = "List accounts", description = "Every account the plugin has reported, most recent first.")
	public List<AccountDto> list() {
		return accounts.findAll(Instant.now().getEpochSecond());
	}

	@GetMapping("/api/runelite/accounts/{accountHash}")
	@Operation(summary = "Get an account", description = "The account's latest data, with buy limits still in effect.")
	public AccountDto get(@PathVariable long accountHash) {
		return accounts.find(accountHash, Instant.now().getEpochSecond())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RuneLite has not reported this account"));
	}
}
