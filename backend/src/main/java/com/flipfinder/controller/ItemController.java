package com.flipfinder.controller;

import com.flipfinder.dto.*;
import com.flipfinder.repository.ItemRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ItemController {
	private final ItemRepository repo;

	public ItemController(ItemRepository repo) {
		this.repo = repo;
	}

	@GetMapping("/api/items")
	public PageResponse<ItemDto> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		// safety clamps
		if (page < 0)
			page = 0;
		if (size < 1)
			size = 1;
		if (size > 200)
			size = 200; // cap

		long total = repo.countAll();
		int offset = page * size;

		// If offset beyond total, nudge back to last page
		if (offset >= total && total > 0) {
			page = (int) ((total - 1) / size);
			offset = page * size;
		}

		List<ItemDto> items = repo.findPage(size, offset);

		PageResponse<ItemDto> resp = new PageResponse<>();
		resp.items = items;
		resp.page = page;
		resp.size = size;
		resp.total = total;
		resp.totalPages = (int) ((total + size - 1) / size);
		return resp;
	}
}