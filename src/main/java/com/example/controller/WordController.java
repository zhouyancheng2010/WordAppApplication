package com.example.controller;

import com.example.model.Word;
import com.example.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WordController {

    @Autowired
    private WordService wordService;

    @GetMapping("/api/words")
    public Page<Word> getAllWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));
        return wordService.getAllWords(pageable);
    }

    @GetMapping("/api/word")
    public Word getWord(@RequestParam String word) {
        return wordService.getWord(word);
    }

    @GetMapping("/api/search")
    public Page<Word> searchWords(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));
        return wordService.searchWords(keyword, pageable);
    }

    @GetMapping("/api/all-words")
    public List<Word> getAllWordsList() {
        return wordService.getAllWordsBySortOrder();
    }
}
