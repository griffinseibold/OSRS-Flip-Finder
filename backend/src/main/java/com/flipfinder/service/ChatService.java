package com.flipfinder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipfinder.dto.AccountDto;
import com.flipfinder.dto.ChatRequest;
import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipPageResponse;
import com.flipfinder.service.FlipQuery.Membership;
import com.flipfinder.service.FlipQuery.PriceBasis;
import com.flipfinder.service.FlipQuery.Sort;
import com.flipfinder.service.LlmClient.Reply;
import com.flipfinder.service.LlmClient.ToolCall;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Answers a player's questions with the homelab's language model. The model
 * looks up flips through a find_flips tool backed by {@link FlipService}, so
 * its numbers come from the same prices as the rest of Flip Finder.
 */
@Service
@ConditionalOnProperty(name = "flipfinder.llm.base-url")
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    // After this many rounds of tool calls the model has to answer.
    static final int MAX_TOOL_ROUNDS = 3;
    // Older messages are dropped to keep the prompt within the model's context.
    static final int MAX_HISTORY = 12;
    // Matches the prompt's three to five suggestions; the flips table has the rest.
    static final int FLIPS_PER_LOOKUP = 5;

    // The flips table's defaults: items trading actively enough to be priced reliably.
    private static final int ACTIVE_WITHIN_MINUTES = 60;
    private static final long ACTIVE_MIN_VOLUME_5M = 10;

    static final List<Map<String, Object>> TOOLS = List.of(Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "find_flips",
                    "description", "Rank Grand Exchange flips for the player, using their account's coins, "
                            + "membership and buy limits left. Returns prices, margins and estimated profit.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "search", Map.of("type", "string",
                                            "description", "Part of an item name, to look up specific items"),
                                    "budget", Map.of("type", "integer",
                                            "description", "Coins to flip with, when the player asks about "
                                                    + "an amount other than their own"),
                                    "sort", Map.of("type", "string",
                                            "enum", List.of("estimatedProfit", "roi", "margin", "volume"),
                                            "description", "How to rank the flips; estimatedProfit by default"),
                                    "membership", Map.of("type", "string",
                                            "enum", List.of("all", "f2p", "members"),
                                            "description", "Only free-to-play or members items")),
                            "required", List.of()))));

    private final LlmClient llm;
    private final FlipService flips;
    private final AccountService accounts;
    private final ObjectMapper mapper;

    public ChatService(LlmClient llm, FlipService flips, AccountService accounts, ObjectMapper mapper) {
        this.llm = llm;
        this.flips = flips;
        this.accounts = accounts;
        this.mapper = mapper;
    }

    /** Answers the last message, emitting text, tool activity and the flips looked up as they happen. */
    public void chat(ChatRequest request, Consumer<Map<String, Object>> emit) {
        long now = Instant.now().getEpochSecond();
        Optional<AccountDto> account = request.account == null
                ? Optional.empty()
                : accounts.find(request.account, now);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(LlmClient.message("system", systemPrompt(account.orElse(null), now)));
        List<ChatRequest.Message> history = request.messages.stream()
                .filter(message -> ("user".equals(message.role) || "assistant".equals(message.role))
                        && message.content != null && !message.content.isBlank())
                .toList();
        for (ChatRequest.Message message : history.subList(Math.max(history.size() - MAX_HISTORY, 0), history.size())) {
            messages.add(LlmClient.message(message.role, message.content));
        }

        try {
            for (int round = 0; ; round++) {
                List<Map<String, Object>> tools = round < MAX_TOOL_ROUNDS ? TOOLS : List.of();
                Reply reply = llm.complete(messages, tools, text -> emit.accept(event("text", "text", text)));
                List<ToolCall> calls = reply.toolCalls().stream().filter(call -> call.name() != null).toList();
                if (calls.isEmpty()) {
                    break;
                }
                messages.add(LlmClient.assistantToolCalls(reply.content(), calls));
                for (ToolCall call : calls) {
                    messages.add(LlmClient.toolResult(call.id(), runTool(call, account.orElse(null), emit)));
                }
            }
        } catch (IOException e) {
            logger.warn("Chat failed: {}", e.getMessage());
            emit.accept(event("error", "message", "The homelab's language model could not answer. Is it running?"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit.accept(event("error", "message", "The answer was interrupted."));
        }
        emit.accept(Map.of("type", "done"));
    }

    private String runTool(ToolCall call, AccountDto account, Consumer<Map<String, Object>> emit) {
        if (!"find_flips".equals(call.name())) {
            return json(Map.of("error", "Unknown tool " + call.name()));
        }
        JsonNode arguments;
        try {
            arguments = mapper.readTree(call.arguments());
        } catch (JsonProcessingException e) {
            return json(Map.of("error", "The arguments were not valid JSON"));
        }
        String search = arguments.path("search").asText("").trim();
        Long budget = arguments.path("budget").asLong(0) > 0 ? arguments.get("budget").asLong() : null;
        Sort sort = switch (arguments.path("sort").asText("")) {
            case "roi" -> Sort.ROI;
            case "margin" -> Sort.MARGIN;
            case "volume" -> Sort.VOLUME_5M;
            default -> Sort.ESTIMATED_PROFIT;
        };
        Membership membership = switch (arguments.path("membership").asText("")) {
            case "f2p" -> Membership.F2P;
            case "members" -> Membership.MEMBERS;
            default -> Membership.ALL;
        };
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("type", "tool");
        started.put("name", call.name());
        started.put("search", search);
        started.put("budget", budget);
        emit.accept(started);

        // A named item is wanted however quietly it trades.
        boolean activeOnly = search.isEmpty();
        FlipQuery query = new FlipQuery(PriceBasis.LATEST, search, membership,
                activeOnly ? ACTIVE_WITHIN_MINUTES : 0, activeOnly ? ACTIVE_MIN_VOLUME_5M : 0,
                budget, sort, null, account == null ? null : account.accountHash);
        FlipPageResponse page = flips.find(query, 0, FLIPS_PER_LOOKUP);

        Map<String, Object> lookup = new LinkedHashMap<>();
        lookup.put("type", "flips");
        lookup.put("search", search);
        lookup.put("budget", page.budget);
        lookup.put("sort", sort.value());
        lookup.put("total", page.total);
        lookup.put("items", page.items);
        emit.accept(lookup);

        long now = Instant.now().getEpochSecond();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", page.budget);
        result.put("matchingItems", page.total);
        List<Map<String, Object>> flips = new ArrayList<>();
        for (FlipDto flip : page.items) {
            flips.add(describe(flips.size() + 1, flip, now));
        }
        result.put("flips", flips);
        return json(result);
    }

    /** A flip as the model sees it: plain numbers under self-explanatory names. */
    private static Map<String, Object> describe(int rank, FlipDto flip, long now) {
        Map<String, Object> row = new LinkedHashMap<>();
        // Small models reorder a list unless each row says where it belongs.
        row.put("rank", rank);
        row.put("item", flip.item.name);
        row.put("members", flip.item.members);
        row.put("buyAt", flip.buyPrice);
        row.put("sellAt", flip.sellPrice);
        row.put("tax", flip.tax);
        row.put("marginPerItem", flip.margin);
        row.put("roiPercent", flip.roi == null ? null : Math.round(flip.roi * 1000) / 10.0);
        row.put("buyLimit", flip.item.buyLimit);
        row.put("alreadyBought", flip.alreadyBought);
        row.put("limitResetsInMinutes", flip.limitResetsAt == null ? null : Math.max((flip.limitResetsAt - now) / 60, 0));
        row.put("quantity", flip.quantity);
        row.put("fillableIn4h", flip.fillableQuantity);
        row.put("estimatedProfit4h", flip.estimatedProfit);
        row.put("cappedBy", flip.limitedBy == null ? null : flip.limitedBy.value());
        row.put("traded5mAtLowPrice", flip.item.lowPriceVolume5m);
        row.put("traded5mAtHighPrice", flip.item.highPriceVolume5m);
        row.put("lastTradeMinutesAgo", flip.lastTradeTime == null ? null : (now - flip.lastTradeTime) / 60);
        return row;
    }

    static String systemPrompt(AccountDto account, long now) {
        StringBuilder prompt = new StringBuilder("""
                You are Flip Finder, an assistant that helps an Old School RuneScape player choose Grand \
                Exchange flips: items to buy and resell for a profit.

                How Flip Finder prices a flip:
                - Buy at the latest instant-sell (low) price and sell at the latest instant-buy (high) price.
                - Grand Exchange tax: the seller pays 2% of the sale price, rounded down and capped at \
                5,000,000 coins per item. Sales under 50 coins pay none, and a few items such as bonds are exempt.
                - Margin per item = sell price - tax - buy price.
                - Each item can only be bought a limited number of times every four hours, counted from the \
                first purchase.
                - Estimated profit = margin x how many could fill in four hours at the latest five-minute \
                trading pace, capped by the buy limit left and the budget. It is an upper bound: other \
                players compete for the same trades.

                Rules:
                - Get prices, margins and flip ideas only from the find_flips tool. Never make up items or numbers.
                - find_flips ranks the best flips first. List them in rank order, and suggest the top three \
                to five unless the player asks for more.
                - Give each flip one numbered line, like "1. **Lassar teleport**: buy 1,151, sell 1,320, \
                143 gp each, about 796K profit." Mention what caps it, its buy limit, the budget or slow \
                trading, only when that matters.
                - Answer briefly, in plain language, and write coins like 1.2M or 45,000 gp.
                - If an item trades slowly or last traded long ago, warn that its price may be unreliable.

                """);
        if (account == null) {
            prompt.append("""
                    The player has not connected the Flip Finder RuneLite plugin, so their coins, membership \
                    and buy limits are unknown. If they ask about their own account, explain that the plugin \
                    reports it, and ask for their budget when it matters.
                    """);
            return prompt.toString();
        }

        prompt.append("The player's account, as the RuneLite plugin last reported it")
                .append(ago(account.capturedAt, now)).append(":\n");
        prompt.append("- Name: ").append(account.displayName == null ? "unknown" : account.displayName).append('\n');
        if (account.ironman) {
            prompt.append("- Ironman: cannot use the Grand Exchange at all, so flipping is impossible.\n");
        }
        prompt.append(account.members
                ? "- Member, with " + account.membershipDays + " days left.\n"
                : "- Free-to-play: can only trade free-to-play items.\n");
        if (account.coins != null) {
            prompt.append("- Coins: ").append(coins(account.coins)).append(" (").append(coins(account.inventoryCoins))
                    .append(" carried, ").append(coins(account.bankCoins)).append(" in the bank").append(')')
                    .append('\n');
        } else {
            prompt.append("- Coins: ").append(coins(account.inventoryCoins))
                    .append(" carried; the bank has not been seen, so the total is unknown.\n");
        }
        if (account.geOffers.isEmpty()) {
            prompt.append("- No Grand Exchange offers.\n");
        } else {
            prompt.append("- Grand Exchange offers:\n");
            for (AccountDto.Offer offer : account.geOffers) {
                prompt.append("  - ").append(offer.state.toLowerCase(Locale.ROOT).replace('_', ' ')).append(' ')
                        .append(offer.name == null ? "item " + offer.itemId : offer.name).append(": ")
                        .append(String.format("%,d of %,d", offer.quantityTraded, offer.totalQuantity))
                        .append(" at ").append(coins(offer.price)).append(" each\n");
            }
        }
        if (!account.buyLimits.isEmpty()) {
            prompt.append("- Buy limits in use:\n");
            for (AccountDto.BuyLimitUse use : account.buyLimits) {
                prompt.append("  - ").append(use.name == null ? "item " + use.itemId : use.name).append(": ")
                        .append(String.format("%,d", use.bought)).append(" bought")
                        .append(use.limit == null ? "" : String.format(" of %,d", use.limit))
                        .append(", resets in ").append(duration(use.resetsAt - now)).append('\n');
            }
        }
        prompt.append("find_flips already uses these coins as the budget, this membership and the buy limits left, "
                + "unless you pass another budget.\n");
        return prompt.toString();
    }

    private static String coins(long amount) {
        return String.format("%,d gp", amount);
    }

    private static String ago(long timestamp, long now) {
        long minutes = Math.max((now - timestamp) / 60, 0);
        return minutes < 1 ? " just now" : " " + duration(now - timestamp) + " ago";
    }

    static String duration(long seconds) {
        long minutes = Math.max(seconds, 0) / 60;
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private static Map<String, Object> event(String type, String key, Object value) {
        return Map.of("type", type, key, value);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
