package com.flipfinder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipfinder.dto.AccountDto;
import com.flipfinder.dto.ChatRequest;
import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipPageResponse;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.ItemHistoryDto;
import com.flipfinder.service.FlipQuery.PriceBasis;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatServiceTests {
    private final LlmClient llm = mock(LlmClient.class);
    private final FlipService flips = mock(FlipService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final ChatService chat = new ChatService(llm, flips, accounts, Optional.empty(), new ObjectMapper());
    private final List<Map<String, Object>> events = new ArrayList<>();

    private static ChatRequest ask(Long account, String question) {
        ChatRequest request = new ChatRequest();
        request.account = account;
        ChatRequest.Message message = new ChatRequest.Message();
        message.role = "user";
        message.content = question;
        request.messages = List.of(message);
        return request;
    }

    private static FlipPageResponse goldLeafPage() {
        FlipDto flip = FlipCalculator.calculate(FlipCalculatorTests.item(), PriceBasis.LATEST, 9_001_000L);
        FlipPageResponse page = new FlipPageResponse();
        page.items = List.of(flip);
        page.total = 1;
        page.budget = 9_001_000L;
        return page;
    }

    private static AccountDto account() {
        AccountDto account = new AccountDto();
        account.accountHash = 42;
        account.displayName = "Flipper";
        account.members = true;
        account.membershipDays = 30;
        account.inventoryCoins = 1_000;
        account.bankCoins = 9_000_000L;
        account.coins = 9_001_000L;
        account.capturedAt = Instant.now().getEpochSecond() - 120;
        account.geOffers = List.of();
        AccountDto.BuyLimitUse use = new AccountDto.BuyLimitUse();
        use.itemId = 1;
        use.name = "Gold leaf";
        use.limit = 11_000;
        use.bought = 60;
        use.remaining = 10_940;
        use.resetsAt = Instant.now().getEpochSecond() + 3 * 3600 + 50 * 60 + 30;
        account.buyLimits = List.of(use);
        return account;
    }

    @Test
    @SuppressWarnings("unchecked")
    void looksUpFlipsWithTheToolThenAnswers() throws Exception {
        when(accounts.find(eq(42L), anyLong())).thenReturn(Optional.of(account()));
        when(flips.find(any(), anyInt(), anyInt())).thenReturn(goldLeafPage());
        when(llm.complete(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.Reply("", List.of(
                        new LlmClient.ToolCall("call-1", "find_flips", "{\"search\":\"gold\",\"sort\":\"roi\"}")), "tool_calls"))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<String>>getArgument(2).accept("Flip gold leaf.");
                    return new LlmClient.Reply("Flip gold leaf.", List.of(), "stop");
                });

        chat.chat(ask(42L, "Is gold leaf worth flipping?"), events::add);

        // A named item is looked up for the account without the activity filters.
        ArgumentCaptor<FlipQuery> query = ArgumentCaptor.forClass(FlipQuery.class);
        verify(flips).find(query.capture(), eq(0), eq(FlipService.MAX_PAGE_SIZE));
        assertThat(query.getValue().search()).isEqualTo("gold");
        assertThat(query.getValue().sort()).isEqualTo(FlipQuery.Sort.ROI);
        assertThat(query.getValue().account()).isEqualTo(42L);
        assertThat(query.getValue().maxTradeAgeMinutes()).isZero();
        assertThat(query.getValue().minVolume5m()).isZero();

        assertThat(events).extracting(event -> event.get("type")).containsExactly("tool", "flips", "text", "done");
        assertThat(events.get(0).get("search")).isEqualTo("gold");
        assertThat(events.get(2).get("text")).isEqualTo("Flip gold leaf.");

        // The second request carries the tool call and its result.
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(llm, times(2)).complete(messages.capture(), anyList(), any());
        List<Map<String, Object>> second = messages.getAllValues().get(1);
        Map<String, Object> result = second.getLast();
        assertThat(result.get("role")).isEqualTo("tool");
        assertThat(result.get("tool_call_id")).isEqualTo("call-1");
        assertThat((String) result.get("content")).contains(
                "\"rank\":1,\"item\":\"Gold leaf\",\"line\":\"**Gold leaf**: buy ", "\"budget\":\"9,001,000 gp\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsOnlyTheItemASearchNamesExactly() throws Exception {
        ItemDto pie = FlipCalculatorTests.item();
        pie.name = "Dragonfruit pie";
        ItemDto fruit = FlipCalculatorTests.item();
        fruit.name = "Dragonfruit";
        FlipPageResponse page = new FlipPageResponse();
        page.items = List.of(
                FlipCalculator.calculate(pie, PriceBasis.LATEST, null),
                FlipCalculator.calculate(fruit, PriceBasis.LATEST, null));
        page.total = 2;
        when(flips.find(any(), anyInt(), anyInt())).thenReturn(page);
        when(llm.complete(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.Reply("", List.of(
                        new LlmClient.ToolCall("call-1", "find_flips", "{\"search\":\"dragonfruit\"}")), "tool_calls"))
                .thenReturn(new LlmClient.Reply("", List.of(), "stop"));

        chat.chat(ask(null, "Is dragonfruit worth flipping?"), events::add);

        assertThat((List<FlipDto>) events.get(1).get("items")).extracting(flip -> flip.item.name)
                .containsExactly("Dragonfruit");
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(llm, times(2)).complete(messages.capture(), anyList(), any());
        assertThat((String) messages.getAllValues().get(1).getLast().get("content"))
                .contains("\"item\":\"Dragonfruit\"")
                .doesNotContain("Dragonfruit pie");
    }

    @Test
    void writesEachFlipAsALineForTheModelToCopy() {
        FlipDto flip = new FlipDto();
        flip.item = FlipCalculatorTests.item();
        flip.item.name = "Old school bond";
        flip.buyPrice = 12_152_033L;
        flip.sellPrice = 12_250_000L;
        flip.margin = 97_967L;
        flip.estimatedProfit = 9_796_700L;
        flip.limitedBy = FlipDto.LimitedBy.BUY_LIMIT;

        assertThat(ChatService.line(flip, "margin 5.6x usual, may not last")).isEqualTo(
                "**Old school bond**: buy 12,152,033 gp, sell 12,250,000 gp, 97,967 gp each, about 9.8M profit, "
                        + "capped by the buy limit (margin 5.6x usual, may not last)");

        flip.estimatedProfit = 884_000L;
        flip.limitedBy = FlipDto.LimitedBy.VOLUME;
        assertThat(ChatService.line(flip, null)).endsWith("about 884K profit, capped by trading volume");

        flip.estimatedProfit = 0L;
        assertThat(ChatService.line(flip, null)).endsWith("but one side has not traded in the latest five minutes");

        flip.estimatedProfit = null;
        assertThat(ChatService.line(flip, null)).endsWith("97,967 gp each, buy limit unknown");
    }

    @Test
    void describesTheAccountInTheSystemPrompt() {
        String prompt = ChatService.systemPrompt(account(), Instant.now().getEpochSecond(), false);

        assertThat(prompt)
                .contains("Name: Flipper", "Member, with 30 days left", "9,001,000 gp")
                .contains("Gold leaf: 60 bought of 11,000, resets in 3h 50m")
                .contains("2% of the sale price");
    }

    @Test
    void explainsTheMissingPluginWithoutAnAccount() {
        String prompt = ChatService.systemPrompt(null, Instant.now().getEpochSecond(), false);

        assertThat(prompt).contains("has not connected the Flip Finder RuneLite plugin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsOfferingToolsAfterThreeRounds() throws Exception {
        when(flips.find(any(), anyInt(), anyInt())).thenReturn(goldLeafPage());
        LlmClient.Reply lookup = new LlmClient.Reply("", List.of(
                new LlmClient.ToolCall("call", "find_flips", "{}")), "tool_calls");
        when(llm.complete(anyList(), anyList(), any()))
                .thenReturn(lookup, lookup, lookup, new LlmClient.Reply("Done.", List.of(), "stop"));

        chat.chat(ask(null, "What should I flip?"), events::add);

        ArgumentCaptor<List<Map<String, Object>>> tools = ArgumentCaptor.forClass(List.class);
        verify(llm, times(4)).complete(anyList(), tools.capture(), any());
        assertThat(tools.getAllValues()).extracting(List::size).containsExactly(1, 1, 1, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void looksUpAnItemsHistoryWhenHistoryIsKept() throws Exception {
        PriceHistoryService history = mock(PriceHistoryService.class);
        ChatService chat = new ChatService(llm, flips, accounts, Optional.of(history), new ObjectMapper());
        ItemDto leaf = FlipCalculatorTests.item();
        ItemHistoryDto summary = new ItemHistoryDto();
        summary.name = "Gold leaf";
        summary.volume = new ItemHistoryDto.Volume();
        summary.volume.verdict = "unusually high";
        summary.margin = new ItemHistoryDto.Margin();
        summary.notes = List.of("1,000 traded in the latest five minutes, against a typical 100.");
        when(history.find("gold leaf")).thenReturn(Optional.of(new PriceHistoryService.Match(leaf, List.of("Gold leaf boots"))));
        when(history.summarize(eq(leaf), anyLong())).thenReturn(summary);
        when(llm.complete(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.Reply("", List.of(
                        new LlmClient.ToolCall("call-1", "item_history", "{\"item\":\"gold leaf\"}")), "tool_calls"))
                .thenReturn(new LlmClient.Reply("It is unusually busy.", List.of(), "stop"));

        chat.chat(ask(null, "Is gold leaf trading normally?"), events::add);

        assertThat(events).extracting(event -> event.get("type")).containsExactly("tool", "history", "done");
        assertThat(events.get(0)).containsEntry("name", "item_history").containsEntry("search", "gold leaf");
        assertThat(events.get(1)).containsEntry("history", summary);

        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Map<String, Object>>> tools = ArgumentCaptor.forClass(List.class);
        verify(llm, times(2)).complete(messages.capture(), tools.capture(), any());
        assertThat(tools.getAllValues().getFirst()).containsExactly(ChatService.FIND_FLIPS, ChatService.ITEM_HISTORY);
        assertThat((String) messages.getAllValues().get(1).getFirst().get("content")).contains("Trading history:");
        assertThat((String) messages.getAllValues().get(1).getLast().get("content"))
                .contains("\"item\":\"Gold leaf\"", "\"otherItemsMatchingTheName\":[\"Gold leaf boots\"]",
                        "\"volume\":\"unusually high\"", "against a typical 100");
    }

    @Test
    void reportsAModelThatCannotBeReached() throws Exception {
        when(llm.complete(anyList(), anyList(), any())).thenThrow(new IOException("Connection refused"));

        chat.chat(ask(null, "What should I flip?"), events::add);

        assertThat(events).extracting(event -> event.get("type")).containsExactly("error", "done");
    }
}
