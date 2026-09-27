package com.flipfinder.service;

import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipPageResponse;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.RuneLiteSnapshot;
import com.flipfinder.repository.ItemRepository;
import com.flipfinder.service.FlipQuery.Direction;
import com.flipfinder.service.FlipQuery.Membership;
import com.flipfinder.service.FlipQuery.PriceBasis;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ranks items as flips. Sort keys depend on the price basis and budget in
 * each request, so every item is priced and sorted in memory; the catalogue is
 * a few thousand rows.
 */
@Service
public class FlipService {
    static final int MAX_PAGE_SIZE = 200;

    private final ItemRepository repository;
    private final AccountService accounts;

    public FlipService(ItemRepository repository, AccountService accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    public FlipPageResponse find(FlipQuery query, int page, int size) {
        return find(query, page, size, Instant.now().getEpochSecond());
    }

    FlipPageResponse find(FlipQuery request, int page, int size, long nowSeconds) {
        AccountView account = applyAccount(request, nowSeconds);
        FlipQuery query = account.query();
        List<ItemDto> items = repository.findAll();
        String search = query.search().toLowerCase(Locale.ROOT);

        List<ItemDto> searched = items.stream()
                .filter(item -> FlipCalculator.isFlippable(item.id))
                .filter(item -> item.name.toLowerCase(Locale.ROOT).contains(search))
                .toList();

        List<FlipDto> matches = searched.stream()
                .map(item -> price(item, query, account.buyLimitWindows().get(item.id)))
                .filter(flip -> matchesFilters(flip, query, nowSeconds))
                .sorted(comparator(query.sort(), query.direction()))
                .toList();

        size = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int lastPage = Math.max((matches.size() - 1) / size, 0);
        page = Math.clamp(page, 0, lastPage);
        int from = page * size;

        FlipPageResponse response = new FlipPageResponse();
        response.items = matches.subList(from, Math.min(from + size, matches.size()));
        response.page = page;
        response.size = size;
        response.total = matches.size();
        response.totalPages = (matches.size() + size - 1) / size;
        response.itemCount = items.size();
        response.profitable = matches.stream().filter(flip -> flip.margin != null && flip.margin > 0).count();
        response.losing = matches.stream().filter(flip -> flip.margin != null && flip.margin < 0).count();
        response.topFlip = matches.stream()
                .filter(flip -> flip.estimatedProfit != null && flip.estimatedProfit > 0)
                .max(Comparator.comparingLong(flip -> flip.estimatedProfit))
                .orElse(null);
        if (!search.isEmpty()) {
            response.searchMatches = (long) searched.size();
        }
        long latestTrade = items.stream()
                .mapToLong(item -> Math.max(orZero(item.highPriceTime), orZero(item.lowPriceTime)))
                .max()
                .orElse(0);
        response.pricesAsOf = latestTrade > 0 ? latestTrade : null;
        response.budget = query.budget();
        return response;
    }

    /** Prices an item, less what the account already bought in its buy limit window. */
    private static FlipDto price(ItemDto item, FlipQuery query, RuneLiteSnapshot.BuyLimitWindow window) {
        if (window == null) {
            return FlipCalculator.calculate(item, query.basis(), query.budget());
        }
        FlipDto flip = FlipCalculator.calculate(item, query.basis(), query.budget(), window.bought);
        flip.alreadyBought = (long) window.bought;
        flip.limitResetsAt = window.startedAt + AccountService.BUY_LIMIT_WINDOW_SECONDS;
        return flip;
    }

    /**
     * The query as the requested account allows it: the account's coins are the
     * budget unless one was given, and a free-to-play account cannot buy members
     * items. Without an account the query is unchanged.
     */
    private AccountView applyAccount(FlipQuery query, long nowSeconds) {
        if (query.account() == null) {
            return new AccountView(query, Map.of());
        }
        RuneLiteSnapshot account = accounts.snapshot(query.account())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RuneLite has not reported this account"));
        Long budget = query.budget() != null ? query.budget() : AccountService.coins(account);
        Membership membership = account.members ? query.membership() : Membership.F2P;
        return new AccountView(query.forAccount(budget, membership), AccountService.activeWindows(account, nowSeconds));
    }

    private record AccountView(FlipQuery query, Map<Integer, RuneLiteSnapshot.BuyLimitWindow> buyLimitWindows) {
    }

    private static boolean matchesFilters(FlipDto flip, FlipQuery query, long nowSeconds) {
        boolean members = flip.item.members;
        if (query.membership() == Membership.F2P && members || query.membership() == Membership.MEMBERS && !members) {
            return false;
        }
        if (query.basis() == PriceBasis.LATEST && query.maxTradeAgeMinutes() > 0
                && (flip.lastTradeTime == null || nowSeconds - flip.lastTradeTime > query.maxTradeAgeMinutes() * 60L)) {
            return false;
        }
        if (orZero(flip.volume5m) < query.minVolume5m()) {
            return false;
        }
        // Hide items the budget cannot buy even one of. Five-minute averages can
        // be missing for an item that still has a latest price, so fall back to
        // that; an item with no price at all cannot be shown as affordable.
        if (query.budget() != null) {
            Long price = firstNonNull(flip.buyPrice, flip.item.lowPrice, flip.item.highPrice);
            return price != null && price <= query.budget();
        }
        return true;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static Comparator<FlipDto> comparator(FlipQuery.Sort sort, Direction direction) {
        Comparator<FlipDto> primary = switch (sort) {
            case ESTIMATED_PROFIT -> by(flip -> flip.estimatedProfit, direction);
            case POTENTIAL_PROFIT -> by(flip -> flip.potentialProfit, direction);
            case MARGIN -> by(flip -> flip.margin, direction);
            case ROI -> by(flip -> flip.roi, direction);
            case BUY_PRICE -> by(flip -> flip.buyPrice, direction);
            case SELL_PRICE -> by(flip -> flip.sellPrice, direction);
            case BUY_LIMIT -> by(flip -> flip.item.buyLimit, direction);
            case VOLUME_5M -> by(flip -> flip.volume5m, direction);
            case LAST_TRADE_TIME -> by(flip -> flip.lastTradeTime, direction);
            case NAME -> Comparator.comparing(flip -> flip.item.name, ordered(String.CASE_INSENSITIVE_ORDER, direction));
        };
        return primary
                .thenComparing(flip -> flip.item.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(flip -> flip.item.id);
    }

    // Missing values sort last in either direction.
    private static <T extends Comparable<T>> Comparator<FlipDto> by(Function<FlipDto, T> key, Direction direction) {
        return Comparator.comparing(key, Comparator.nullsLast(ordered(Comparator.<T>naturalOrder(), direction)));
    }

    private static <T> Comparator<T> ordered(Comparator<T> ascending, Direction direction) {
        return direction == Direction.ASC ? ascending : ascending.reversed();
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
