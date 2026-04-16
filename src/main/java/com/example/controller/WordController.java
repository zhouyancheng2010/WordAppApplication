package com.example.controller;

import com.example.model.Word;
import com.example.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WordController {

    @Autowired
    private WordService wordService;

    // 增加分页参数，默认第一页，每页 20 个
    @GetMapping("/api/words")
    public Page<Word> getAllWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return wordService.getAllWords(pageable);
    }

    @GetMapping("/api/word")
    public Word getWord(@RequestParam String word) {
        return wordService.getWord(word);
    }
}
