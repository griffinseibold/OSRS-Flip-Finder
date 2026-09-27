package com.flipfinder.dto;

/** One page of flips, with totals across every flip that matched. */
public class FlipPageResponse extends PageResponse<FlipDto> {
    public long itemCount; // every item in the catalogue
    public long profitable; // matching flips with a positive margin
    public long losing; // matching flips with a negative margin
    public Long searchMatches; // items whose name matches the search, ignoring other filters
    public Long pricesAsOf; // Unix seconds of the most recent trade across all items
    public FlipDto topFlip; // matching flip with the highest positive estimated profit
    public Long budget; // the budget applied: the request's, or the account's coins
}
