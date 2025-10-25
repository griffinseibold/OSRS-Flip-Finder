package com.flipfinder.dto;

import java.util.List;

public class PageResponse<T> {
    public List<T> items;
    public int page; // 0-based
    public int size; // page size
    public long total; // total rows
    public int totalPages; // ceil(total/size)
}
