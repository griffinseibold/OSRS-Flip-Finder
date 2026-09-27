package com.flipfinder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipfinder.dto.AccountDto;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.RuneLiteSnapshot;
import com.flipfinder.repository.AccountRepository;
import com.flipfinder.repository.AccountRepository.StoredAccount;
import com.flipfinder.repository.ItemRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/** Accounts as reported by the RuneLite plugin. */
@Service
public class AccountService {
    // A buy limit window lasts four hours from the first purchase in it.
    static final long BUY_LIMIT_WINDOW_SECONDS = 4 * 60 * 60;

    private final AccountRepository accounts;
    private final ItemRepository items;
    private final ObjectMapper mapper;

    public AccountService(AccountRepository accounts, ItemRepository items, ObjectMapper mapper) {
        this.accounts = accounts;
        this.items = items;
        this.mapper = mapper;
    }

    public void save(long accountHash, RuneLiteSnapshot snapshot) {
        if (snapshot.geOffers == null) snapshot.geOffers = List.of();
        if (snapshot.buyLimits == null) snapshot.buyLimits = List.of();
        String receivedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        accounts.save(accountHash, snapshot.displayName, write(snapshot), receivedAt);
    }

    public Optional<RuneLiteSnapshot> snapshot(long accountHash) {
        return accounts.find(accountHash).map(stored -> read(stored.snapshotJson()));
    }

    public Optional<AccountDto> find(long accountHash, long nowSeconds) {
        return accounts.find(accountHash).map(stored -> toDto(stored, itemsById(), nowSeconds));
    }

    public List<AccountDto> findAll(long nowSeconds) {
        Map<Integer, ItemDto> itemsById = itemsById();
        return accounts.findAll().stream().map(stored -> toDto(stored, itemsById, nowSeconds)).toList();
    }

    /** Buy limit windows that have not yet reset, keyed by item id. */
    static Map<Integer, RuneLiteSnapshot.BuyLimitWindow> activeWindows(RuneLiteSnapshot snapshot, long nowSeconds) {
        return snapshot.buyLimits.stream()
                .filter(window -> nowSeconds - window.startedAt < BUY_LIMIT_WINDOW_SECONDS)
                .collect(Collectors.toMap(window -> window.itemId, Function.identity(), (a, b) -> a));
    }

    /** The coins an account has to flip with, once the bank has been seen. */
    static Long coins(RuneLiteSnapshot snapshot) {
        return snapshot.bankCoins == null ? null : snapshot.inventoryCoins + snapshot.bankCoins;
    }

    private AccountDto toDto(StoredAccount stored, Map<Integer, ItemDto> itemsById, long nowSeconds) {
        RuneLiteSnapshot snapshot = read(stored.snapshotJson());
        AccountDto account = new AccountDto();
        account.accountHash = stored.accountHash();
        account.displayName = snapshot.displayName;
        account.members = snapshot.members;
        account.membershipDays = snapshot.membershipDays;
        account.ironman = snapshot.ironman;
        account.inventoryCoins = snapshot.inventoryCoins;
        account.bankCoins = snapshot.bankCoins;
        account.bankCoinsSeenAt = snapshot.bankCoinsSeenAt;
        account.coins = coins(snapshot);
        account.capturedAt = snapshot.capturedAt;
        account.receivedAt = stored.receivedAt();

        account.geOffers = snapshot.geOffers.stream().map(offer -> {
            AccountDto.Offer dto = new AccountDto.Offer();
            dto.slot = offer.slot;
            dto.itemId = offer.itemId;
            dto.name = name(itemsById, offer.itemId);
            dto.state = offer.state;
            dto.price = offer.price;
            dto.totalQuantity = offer.totalQuantity;
            dto.quantityTraded = offer.quantityTraded;
            dto.spent = offer.spent;
            return dto;
        }).toList();

        account.buyLimits = activeWindows(snapshot, nowSeconds).values().stream()
                .sorted(Comparator.comparingLong((RuneLiteSnapshot.BuyLimitWindow window) -> window.startedAt).reversed())
                .map(window -> {
                    ItemDto item = itemsById.get(window.itemId);
                    AccountDto.BuyLimitUse use = new AccountDto.BuyLimitUse();
                    use.itemId = window.itemId;
                    use.name = name(itemsById, window.itemId);
                    use.limit = item == null ? null : item.buyLimit;
                    use.bought = window.bought;
                    use.remaining = use.limit == null ? null : Math.max(use.limit - window.bought, 0);
                    use.resetsAt = window.startedAt + BUY_LIMIT_WINDOW_SECONDS;
                    return use;
                })
                .toList();
        return account;
    }

    private Map<Integer, ItemDto> itemsById() {
        return items.findAll().stream().collect(Collectors.toMap(item -> item.id, Function.identity()));
    }

    private static String name(Map<Integer, ItemDto> itemsById, int itemId) {
        ItemDto item = itemsById.get(itemId);
        return item == null ? null : item.name;
    }

    private String write(RuneLiteSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store RuneLite snapshot", e);
        }
    }

    private RuneLiteSnapshot read(String json) {
        try {
            RuneLiteSnapshot snapshot = mapper.readValue(json, RuneLiteSnapshot.class);
            if (snapshot.geOffers == null) snapshot.geOffers = List.of();
            if (snapshot.buyLimits == null) snapshot.buyLimits = List.of();
            return snapshot;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored RuneLite snapshot is not valid JSON", e);
        }
    }
}
